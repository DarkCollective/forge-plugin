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
import org.jetbrains.annotations.Nullable;

import static com.forge.fl.FLTokenTypes.*;

/**
 * Recursive-descent parser for FL v0.2 (docs/LANGUAGE.md section 5.1). Every loop consumes at least one token per
 * iteration or exits, so the parser always consumes the whole file. Errors become {@code PsiErrorElement}s.
 */
public class FLParser implements PsiParser {

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

    private int textLength() {
        String text = b.getTokenText();
        return text == null ? 0 : text.length();
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
        if (t == COLON || t == MEMO_KW) {
            definition();
        } else if (t == CHOICE_KW) {
            choice();
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
        b.advanceLexer(); // ':' or 'MEMO:'
        if (tt() == WORD_NAME) {
            b.advanceLexer();
        } else if (tt() == QUALIFIED_NAME || (tt() != null && KEYWORDS.contains(tt()) && !DEFINERS.contains(tt())
                && tt() != SEMI)) {
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
                int to = b.getCurrentOffset() + textLength();
                b.advanceLexer();
                // the rest of the item: ':type...' or ':choice<' TYPE '>...' (FL v0.2), all glued to the name
                while ((tt() == HDR_PARAM_REST || tt() == HDR_CHOICE_REF) && b.getCurrentOffset() == to) {
                    to = b.getCurrentOffset() + textLength();
                    if (tt() == HDR_CHOICE_REF) {
                        PsiBuilder.Marker r = b.mark();
                        b.advanceLexer();
                        r.done(FLElementTypes.CHOICE_REF);
                    } else {
                        b.advanceLexer();
                    }
                }
                p.done(FLElementTypes.PARAMETER);
            } else if (t == HDR_PARAM_REST || t == HDR_TYPE || t == HDR_ARROW || t == HDR_DOTS || t == HDR_OUTPUT
                    || t == HDR_OTHER || t == HDR_CHOICE_REF) {
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
        } else if (t == BIND_KW) {
            bind();
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

    /**
     * {@code :> name} (docs/LANGUAGE.md 7.1). A token after {@code :>} that is not a word is E_SYNTAX and is left
     * for the body; a reserved word or keyword is E_NAME. Where {@code :>} may appear is checked by the diagnostics.
     */
    private void bind() {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer(); // ':>'
        IElementType t = tt();
        if (t == WORD_NAME) {
            b.advanceLexer();
        } else if (t != null && (KEYWORDS.contains(t) || t == VOCAB_KW) && !FORM_STARTS.contains(t) && t != SEMI) {
            errorAndAdvance("':>' needs an unreserved word name (E_NAME)");
        } else {
            b.error(t == SEMI || (t != null && FORM_STARTS.contains(t)) ? "':>' needs a word name (E_NAME)"
                    : "':>' must be followed by one word name (E_SYNTAX)");
        }
        m.done(FLElementTypes.LOCAL_BIND);
    }

    private void wordRef() {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer();
        m.done(FLElementTypes.WORD_REF);
    }

    // ---- CHOICE: -----------------------------------------------------------------------------------------

    /** {@code CHOICE: name { option opt [help] ; } ;} (docs/LANGUAGE.md 5.12): the shape of META:. */
    private void choice() {
        PsiBuilder.Marker m = b.mark();
        b.advanceLexer(); // CHOICE:
        if (tt() == WORD_NAME) b.advanceLexer();
        else if (tt() == QUALIFIED_NAME || (tt() != null && KEYWORDS.contains(tt()) && tt() != SEMI && !FORM_STARTS.contains(tt())))
            errorAndAdvance("a choice type name must be an unreserved, unqualified word (E_NAME)");
        else nameMissing("choice type name expected");
        int options = 0;
        while (!b.eof()) {
            IElementType t = tt();
            if (t == SEMI) {
                if (options == 0) {
                    PsiBuilder.Marker e = b.mark();
                    b.advanceLexer();
                    e.error("a CHOICE: block needs at least one option (E_META)");
                } else {
                    b.advanceLexer();
                }
                m.done(FLElementTypes.CHOICE_DECL);
                return;
            }
            if (FORM_STARTS.contains(t)) break;
            if (t == WORD_NAME && FLSyntax.CHOICE_OPTION.equals(b.getTokenText())) {
                option();
                options++;
            } else {
                PsiBuilder.Marker c = b.mark();
                b.advanceLexer();
                skipToClauseEnd();
                c.error("'option' or the closing ';' expected (E_SYNTAX)");
            }
        }
        b.error("';' expected (E_SYNTAX)");
        m.done(FLElementTypes.CHOICE_DECL);
    }

    private void option() {
        PsiBuilder.Marker c = b.mark();
        b.advanceLexer(); // option
        int args = 0;
        while (!b.eof() && tt() != SEMI && !FORM_STARTS.contains(tt())) {
            IElementType t = tt();
            boolean ok = args == 0 ? t == WORD_NAME || t == STRING || (t == NUMBER && isInteger(b.getTokenText()))
                    : args == 1 && t == STRING;
            if (ok) b.advanceLexer();
            else errorAndAdvance(args == 0 ? "option expected: a word, an integer or a string (E_META)"
                    : args == 1 ? "help text expected: a string (E_META)" : "an option takes at most two arguments (E_META)");
            args++;
        }
        if (args == 0) b.error("option expected (E_META)");
        if (tt() == SEMI) b.advanceLexer();
        else b.error("';' expected (E_SYNTAX)");
        c.done(FLElementTypes.CHOICE_OPTION);
    }

    private static boolean isInteger(@Nullable String text) {
        return text != null && !text.contains(".") && (text.contains("0x") || text.contains("0b")
                || (!text.contains("e") && !text.contains("E")));
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
        if ("checks".equals(key)) { // reserved (5.9): an error whatever follows
            skipToClauseEnd();
            c.error("'checks' is reserved for a later release (E_META)");
            return;
        }
        int args = 0;
        boolean paramName = "param".equals(key);
        while (!b.eof() && tt() != SEMI && !FORM_STARTS.contains(tt())) {
            IElementType t = tt();
            switch (key == null ? "" : key) {
                case "doc", "category", "version", "use-when" -> {
                    if (t == STRING && args == 0) b.advanceLexer();
                    else errorAndAdvance(t == STRING ? "'" + key + "' takes one string (E_META)" : "string expected (E_META)");
                }
                case "link" -> {
                    if (t == STRING && args < 2) b.advanceLexer();
                    else errorAndAdvance(t == STRING ? "'link' takes a URL and an optional label (E_META)"
                            : "string expected (E_META)");
                }
                case "inverse" -> {
                    if ((t == WORD_NAME || t == QUALIFIED_NAME) && args == 0) wordRef();
                    else errorAndAdvance(args == 0 ? "word name expected (E_META)" : "'inverse' takes one name (E_META)");
                }
                case "helper" -> errorAndAdvance("'helper' takes no arguments (E_META)");
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
        if (args == 0 && !"helper".equals(key)) b.error("argument expected (E_META)");
        if (tt() == SEMI) b.advanceLexer();
        else b.error("';' expected (E_SYNTAX)");
        c.done(FLElementTypes.META_CLAUSE);
    }
}
