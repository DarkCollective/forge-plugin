// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.parser;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.lexer.FLSyntax;
import com.forge.fl.psi.FLElementTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import static com.forge.fl.FLTokenTypes.*;

/**
 * Recursive-descent parser for FL (docs/LANGUAGE.md section 5.1). Every loop consumes at least one token per
 * iteration or exits, so the parser always consumes the whole file. Errors become {@code PsiErrorElement}s.
 */
public class FLParser implements PsiParser {
    /** Tokens that start a top-level form; they end an unterminated definition or META: block. */
    private static final TokenSet FORM_STARTS = TokenSet.create(COLON, IN_KW, USING_KW, META_KW, VOCAB_KW, PRIMITIVE_KW);

    private PsiBuilder b;

    @NotNull
    @Override
    public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        this.b = builder;
        builder.setDebugMode(false);
        PsiBuilder.Marker file = builder.mark();
        while (!builder.eof()) {
            int before = builder.getCurrentOffset();
            topLevel();
            if (builder.getCurrentOffset() == before && !builder.eof()) {
                errorAndAdvance("unexpected token (E_SYNTAX)"); // safety net
            }
        }
        file.done(root);
        return builder.getTreeBuilt();
    }

    private IElementType tt() {
        return b.getTokenType();
    }

    private void errorAndAdvance(String message) {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer();
        m.error(message);
    }

    /** A missing name operand: E_SYNTAX at the end of the input, E_NAME for any other token (docs/LANGUAGE.md 11.0). */
    private void nameMissing(String message) {
        b.error(message + (b.eof() ? " (E_SYNTAX)" : " (E_NAME)"));
    }

    private static boolean isLiteral(IElementType t) {
        return t != null && LITERALS.contains(t);
    }

    // ---- top level ---------------------------------------------------------------------------------------

    private void topLevel() {
        IElementType t = tt();
        if (t == COLON) {
            definition();
        } else if (t == IN_KW) {
            nameForm(FLElementTypes.IN_FORM, "vocabulary name expected");
        } else if (t == VOCAB_KW) {
            nameForm(FLElementTypes.VOCAB_FORM, "vocabulary name expected");
        } else if (t == USING_KW) {
            using();
        } else if (t == META_KW) {
            meta();
        } else if (t == PRIMITIVE_KW) {
            nameForm(FLElementTypes.PRIMITIVE_DECL, "word name expected");
        } else if (isLiteral(t) && (b.lookAhead(1) == CONSTANT_KW || b.lookAhead(1) == VARIABLE_KW)) {
            constOrVar();
        } else if (t == SEMI) {
            errorAndAdvance("unexpected ';' (E_SYNTAX)");
        } else if (t == CONSTANT_KW || t == VARIABLE_KW) {
            errorAndAdvance("a literal must come before " + (t == CONSTANT_KW ? "CONSTANT" : "VARIABLE") + " (E_SYNTAX)");
        } else {
            item(true); // stray code: the console allows it, so no error
        }
    }

    /** {@code IN: v}, {@code VOCAB v} or {@code PRIMITIVE name}. */
    private void nameForm(IElementType type, String missing) {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer();
        if (tt() == WORD_NAME) b.advanceLexer();
        else nameMissing(missing);
        m.done(type);
    }

    private void using() {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer();
        while (!b.eof() && tt() != SEMI) {
            if (tt() == WORD_NAME) b.advanceLexer();
            else if (FORM_STARTS.contains(tt())) break;
            else errorAndAdvance("vocabulary name expected (E_NAME)");
        }
        if (tt() == SEMI) b.advanceLexer();
        else b.error("';' expected (E_SYNTAX)");
        m.done(FLElementTypes.USING_FORM);
    }

    private void constOrVar() {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer(); // literal
        boolean constant = tt() == CONSTANT_KW;
        b.advanceLexer();
        if (tt() == WORD_NAME) b.advanceLexer();
        else nameMissing("word name expected");
        m.done(constant ? FLElementTypes.CONSTANT_DECL : FLElementTypes.VARIABLE_DECL);
    }

    // ---- definitions -------------------------------------------------------------------------------------

    private void definition() {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer(); // ':'
        if (tt() == WORD_NAME) {
            b.advanceLexer();
        } else if (tt() == QUALIFIED_NAME || (tt() != null && KEYWORDS.contains(tt()) && tt() != COLON && tt() != SEMI)) {
            errorAndAdvance("a definition name must be an unreserved, unqualified word (E_NAME)");
        } else {
            nameMissing("word name expected");
        }
        if (tt() == HDR_LPAREN) header();
        else b.error("stack-effect header expected (E_HEADER)");
        while (tt() == STRING && b.lookAhead(1) == WORD_NAME && isDocWord()) {
            PsiBuilder.Marker d = b.mark();
            b.advanceLexer();
            b.advanceLexer();
            d.done(FLElementTypes.DOC_DECL);
        }
        body();
        if (tt() == SEMI) {
            b.advanceLexer();
        } else {
            b.error("';' expected (E_SYNTAX)");
        }
        m.done(FLElementTypes.DEFINITION);
    }

    /** Whether the token after the current string is the word doc or category. */
    private boolean isDocWord() {
        PsiBuilder.Marker probe = b.mark();
        b.advanceLexer();
        String text = b.getTokenText();
        probe.rollbackTo();
        return "doc".equals(text) || "category".equals(text);
    }

    private void header() {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer(); // '('
        while (!b.eof() && tt() != HDR_RPAREN) {
            IElementType t = tt();
            if (t == HDR_PARAM_NAME) {
                PsiBuilder.Marker p = b.mark();
                b.advanceLexer();
                if (tt() == HDR_PARAM_REST) b.advanceLexer();
                p.done(FLElementTypes.PARAMETER);
            } else if (t == HDR_PARAM_REST || t == HDR_TYPE || t == HDR_ARROW || t == HDR_DOTS || t == HDR_OUTPUT
                    || t == HDR_OTHER) {
                b.advanceLexer();
            } else {
                break; // not a header token: the header is unterminated
            }
        }
        if (tt() == HDR_RPAREN) b.advanceLexer();
        else b.error("')' expected (E_LEX)");
        m.done(FLElementTypes.HEADER);
    }

    /**
     * Parses body items until the terminating ';' (not consumed), the end of the file, or a token that starts
     * a new form (a missing ';').
     *
     */
    private void body() {
        while (!b.eof()) {
            IElementType t = tt();
            if (t == SEMI || FORM_STARTS.contains(t)) return;
            if (!item(false)) return;
        }
    }

    /**
     * Parses one item. Returns false (consuming nothing) when the token ends the enclosing construct.
     */
    private boolean item(boolean topLevel) {
        IElementType t = tt();
        if (t == LBRACKET) {
            group(FLElementTypes.QUOTATION, RBRACKET, "']' expected");
        } else if (t == LBRACE) {
            group(FLElementTypes.LIST_LITERAL, RBRACE, "'}' expected");
        } else if (t == WORD_NAME || t == QUALIFIED_NAME) {
            wordRef();
        } else if (t == RBRACKET || t == RBRACE) {
            if (!topLevel && depth > 0) return false;
            errorAndAdvance("unbalanced '" + (t == RBRACKET ? "]" : "}") + "' (E_SYNTAX)");
        } else if (isLiteral(t) || t == RECURSE_KW) {
            b.advanceLexer();
        } else if (t == TokenType.BAD_CHARACTER) {
            errorAndAdvance("malformed token (E_LEX)");
        } else if (t == SEMI && topLevel) {
            errorAndAdvance("unexpected ';' (E_SYNTAX)");
        } else if (t == CONSTANT_KW || t == VARIABLE_KW) {
            errorAndAdvance("a literal must come before " + (t == CONSTANT_KW ? "CONSTANT" : "VARIABLE") + " (E_SYNTAX)");
        } else if (t == SEMI || FORM_STARTS.contains(t)) {
            return false;
        } else {
            errorAndAdvance("unexpected token (E_SYNTAX)");
        }
        return true;
    }

    private int depth;

    private void group(IElementType type, IElementType close, String missing) {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer();
        depth++;
        try {
            while (!b.eof() && tt() != close) {
                IElementType t = tt();
                if (t == SEMI || FORM_STARTS.contains(t)) break; // unclosed: let the definition recover
                // A closer of the other kind ends this group as unbalanced; the outer construct handles it.
                if ((t == RBRACKET || t == RBRACE) && t != close) break;
                if (!item(false)) break;
            }
        } finally {
            depth--;
        }
        if (tt() == close) b.advanceLexer();
        else b.error(missing + " (E_SYNTAX)");
        m.done(type);
    }

    private void wordRef() {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer();
        m.done(FLElementTypes.WORD_REF);
    }

    // ---- META: -------------------------------------------------------------------------------------------

    private void meta() {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer(); // META:
        if (tt() == WORD_NAME) wordRef();
        else b.error("word name expected (E_SYNTAX)");
        while (!b.eof()) {
            IElementType t = tt();
            if (t == SEMI) {
                b.advanceLexer();
                m.done(FLElementTypes.META_BLOCK);
                return;
            }
            if (FORM_STARTS.contains(t)) break;
            if (t == WORD_NAME && FLSyntax.META_CLAUSES.contains(b.getTokenText())) {
                clause();
            } else if (t == WORD_NAME) {
                PsiBuilder.Marker c = b.mark();
                b.advanceLexer();
                skipToClauseEnd();
                c.error("unknown META: clause (E_META)");
            } else {
                errorAndAdvance("unexpected token in META: block (E_META)");
            }
        }
        b.error("';' expected (E_SYNTAX)");
        m.done(FLElementTypes.META_BLOCK);
    }

    private void skipToClauseEnd() {
        while (!b.eof() && tt() != SEMI && !FORM_STARTS.contains(tt())) b.advanceLexer();
        if (tt() == SEMI) b.advanceLexer();
    }

    private void clause() {
        PsiBuilder.Marker c = b.mark();
        String key = b.getTokenText();
        b.advanceLexer();
        int args = 0;
        boolean paramName = "param".equals(key);
        while (!b.eof() && tt() != SEMI && !FORM_STARTS.contains(tt())) {
            IElementType t = tt();
            switch (key == null ? "" : key) {
                case "doc", "category", "version" -> {
                    if (t == STRING) b.advanceLexer();
                    else errorAndAdvance("string expected (E_META)");
                }
                case "param" -> {
                    if (paramName && t == WORD_NAME) {
                        PsiBuilder.Marker r = b.mark();
                        b.advanceLexer();
                        r.done(FLElementTypes.PARAM_REF);
                        paramName = false;
                    } else if (!paramName && t == STRING) {
                        b.advanceLexer();
                    } else {
                        errorAndAdvance(paramName ? "parameter name expected (E_META)" : "string expected (E_META)");
                    }
                }
                case "alias" -> {
                    if (t == WORD_NAME) {
                        PsiBuilder.Marker a = b.mark();
                        b.advanceLexer();
                        a.done(FLElementTypes.ALIAS);
                    } else {
                        errorAndAdvance("alias name expected (E_NAME)");
                    }
                }
                default -> { // see-also
                    if (t == WORD_NAME || t == QUALIFIED_NAME) wordRef();
                    else errorAndAdvance("word name expected (E_META)");
                }
            }
            args++;
        }
        if (args == 0) b.error("argument expected (E_META)");
        if (tt() == SEMI) b.advanceLexer();
        else b.error("';' expected (E_SYNTAX)");
        c.done(FLElementTypes.META_CLAUSE);
    }
}
