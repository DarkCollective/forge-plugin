// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.lexer;

import com.forge.fl.FLTokenTypes;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.forge.fl.lexer.FLSyntax.isWs;

/**
 * Hand-written lexer for FL (docs/LANGUAGE.md section 2, reference: packages/lang/src/reader.ts).
 *
 * <p>A token is a maximal run of non-whitespace characters, classified by the spec rules, with the string, char,
 * comment, header and byte-array exceptions. The only lookbehind the language has is the {@code : name (} header
 * rule; it, and the position inside a header, are encoded in {@link #getState()} so the lexer can restart at any
 * token boundary. The lexer never throws, always advances, and never runs past the end of the buffer.
 */
public final class FLLexer extends LexerBase {
    /** No pending lookbehind. */
    public static final int S_NORMAL = 0;
    /** The last significant token was ':'. */
    public static final int S_AFTER_COLON = 1;
    /** The last two significant tokens were ':' and a name: a standalone '(' is a header. */
    public static final int S_AFTER_NAME = 2;
    /** Inside a header, before '--'. */
    public static final int S_HDR_IN = 3;
    /** Inside a header, after '--'. */
    public static final int S_HDR_OUT = 4;
    /** The last two significant tokens were ': :'; both "after ':'" and "after ': name'". */
    public static final int S_AFTER_COLON_NAME = 5;

    private CharSequence buffer = "";
    private int end;
    private int pos;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;
    /** State before the current token (what start() needs to resume here). */
    private int state;
    /** State after the current token. */
    private int nextState;

    @Override
    public void start(@NotNull CharSequence buf, int startOffset, int endOffset, int initialState) {
        buffer = buf;
        end = Math.min(endOffset, buf.length());
        pos = startOffset;
        state = initialState >= S_NORMAL && initialState <= S_AFTER_COLON_NAME ? initialState : S_NORMAL;
        locate();
    }

    @Override
    public int getState() {
        return state;
    }

    @Nullable
    @Override
    public IElementType getTokenType() {
        return tokenType;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @Override
    public void advance() {
        pos = tokenEnd;
        state = nextState;
        locate();
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return end;
    }

    // ------------------------------------------------------------------------------------------------------

    private void locate() {
        tokenStart = pos;
        if (pos >= end) {
            tokenType = null;
            tokenEnd = pos;
            nextState = state;
            return;
        }
        lex();
        if (tokenEnd <= tokenStart) { // safety net: never stall
            tokenEnd = tokenStart + 1;
            tokenType = TokenType.BAD_CHARACTER;
            nextState = S_NORMAL;
        }
        if (tokenEnd > end) tokenEnd = end;
    }

    private void emit(IElementType type, int to, int next) {
        tokenType = type;
        tokenEnd = to;
        nextState = next;
    }

    /** Emits a significant token, updating the header lookbehind. */
    private void emitSignificant(IElementType type, int to) {
        emit(type, to, stateAfter(type));
    }

    private int stateAfter(IElementType t) {
        boolean afterColon = state == S_AFTER_COLON || state == S_AFTER_COLON_NAME;
        if (t == FLTokenTypes.COLON) return afterColon ? S_AFTER_COLON_NAME : S_AFTER_COLON;
        if (afterColon && FLTokenTypes.NAME_LIKE.contains(t)) return S_AFTER_NAME;
        return S_NORMAL;
    }

    private int runEnd(int from) {
        int j = from;
        while (j < end && !isWs(buffer.charAt(j))) j++;
        return j;
    }

    private void lex() {
        int i = pos;
        char c = buffer.charAt(i);
        if (state == S_HDR_IN || state == S_HDR_OUT) {
            lexHeader(i, c);
            return;
        }
        if (isWs(c)) {
            int j = i + 1;
            while (j < end && isWs(buffer.charAt(j))) j++;
            emit(TokenType.WHITE_SPACE, j, state);
            return;
        }
        if (c == '"') {
            lexString(i);
            return;
        }
        if (c == '\'') {
            lexChar(i);
            return;
        }
        int j = runEnd(i);
        String run = buffer.subSequence(i, j).toString();

        if (run.equals("\\")) { // rule 1: line comment
            int k = j;
            while (k < end && buffer.charAt(k) != '\n' && buffer.charAt(k) != '\r') k++;
            emit(FLTokenTypes.LINE_COMMENT, k, state);
            return;
        }
        if (run.equals("(")) { // rule 2: header or block comment
            if (state == S_AFTER_NAME || state == S_AFTER_COLON_NAME) {
                emit(FLTokenTypes.HDR_LPAREN, j, S_HDR_IN);
                return;
            }
            int close = findStandaloneClose(j);
            if (close < 0) {
                emit(TokenType.BAD_CHARACTER, end, S_NORMAL); // unterminated block comment
            } else {
                emit(FLTokenTypes.BLOCK_COMMENT, close + 1, state);
            }
            return;
        }
        if (run.equals("#[")) { // rule 5: byte array
            lexBytes(i, j);
            return;
        }
        if (FLSyntax.hasInvisible(run)) {
            emit(TokenType.BAD_CHARACTER, j, S_NORMAL);
            return;
        }
        switch (run) {
            case "[" -> emitSignificant(FLTokenTypes.LBRACKET, j);
            case "]" -> emitSignificant(FLTokenTypes.RBRACKET, j);
            case "{" -> emitSignificant(FLTokenTypes.LBRACE, j);
            case "}" -> emitSignificant(FLTokenTypes.RBRACE, j);
            default -> lexWord(run, c, j);
        }
    }

    private void lexWord(String run, char c, int j) {
        int i = pos;
        if (FLSyntax.isDigit(c) || (c == '-' && j - i > 1 && FLSyntax.isDigit(run.charAt(1)))) {
            if (FLSyntax.isNumber(run)) emitSignificant(FLTokenTypes.NUMBER, j);
            else emit(TokenType.BAD_CHARACTER, j, S_NORMAL);
            return;
        }
        IElementType kw = keyword(run);
        if (kw != null) {
            emitSignificant(kw, j);
            return;
        }
        int colon = run.indexOf(':');
        if (colon >= 0) {
            String vocab = run.substring(0, colon);
            String name = run.substring(colon + 1);
            if (FLSyntax.isVocabName(vocab) && name.indexOf(':') < 0 && FLSyntax.isWordName(name)) {
                emitSignificant(FLTokenTypes.QUALIFIED_NAME, j);
            } else {
                emit(TokenType.BAD_CHARACTER, j, S_NORMAL);
            }
            return;
        }
        if (FLSyntax.isWordName(run)) emitSignificant(FLTokenTypes.WORD_NAME, j);
        else emit(TokenType.BAD_CHARACTER, j, S_NORMAL);
    }

    @Nullable
    private static IElementType keyword(String run) {
        return switch (run) {
            case "true", "false" -> FLTokenTypes.BOOL;
            case ":" -> FLTokenTypes.COLON;
            case ";" -> FLTokenTypes.SEMI;
            case "IN:" -> FLTokenTypes.IN_KW;
            case "USING:" -> FLTokenTypes.USING_KW;
            case "META:" -> FLTokenTypes.META_KW;
            case "VOCAB" -> FLTokenTypes.VOCAB_KW;
            case "CONSTANT" -> FLTokenTypes.CONSTANT_KW;
            case "VARIABLE" -> FLTokenTypes.VARIABLE_KW;
            case "PRIMITIVE" -> FLTokenTypes.PRIMITIVE_KW;
            case "recurse" -> FLTokenTypes.RECURSE_KW;
            default -> null;
        };
    }

    /** Index of the first standalone ')' at or after {@code from}, or -1. */
    private int findStandaloneClose(int from) {
        for (int k = from; k < end; k++) {
            if (buffer.charAt(k) == ')' && k > 0 && isWs(buffer.charAt(k - 1))
                    && (k + 1 >= end || isWs(buffer.charAt(k + 1)))) {
                return k;
            }
        }
        return -1;
    }

    private void lexString(int i) {
        boolean bad = false;
        int k = i + 1;
        for (; ; ) {
            if (k >= end) { // unterminated
                emit(TokenType.BAD_CHARACTER, end, S_NORMAL);
                return;
            }
            char d = buffer.charAt(k);
            if (d == '"') break;
            if (d == '\\') {
                int next = FLSyntax.readEscape(buffer, k, end, false);
                if (next < 0) {
                    bad = true;
                    k = Math.min(k + 2, end);
                } else {
                    k = next;
                }
            } else {
                k++;
            }
        }
        int to = k + 1;
        if (to < end && !isWs(buffer.charAt(to))) { // a string must be followed by whitespace
            bad = true;
            to = runEnd(to);
        }
        if (bad) emit(TokenType.BAD_CHARACTER, to, S_NORMAL);
        else emit(FLTokenTypes.STRING, to, S_NORMAL);
    }

    private void lexChar(int i) {
        int k = i + 1;
        boolean ok = true;
        if (k >= end) {
            ok = false;
        } else {
            char d = buffer.charAt(k);
            if (d == '\n' || d == '\r' || d == '\'') {
                ok = false;
            } else if (d == '\\') {
                int next = FLSyntax.readEscape(buffer, k, end, true);
                if (next < 0) ok = false;
                else k = next;
            } else {
                k += Character.charCount(Character.codePointAt(buffer, k));
            }
        }
        if (ok && (k >= end || buffer.charAt(k) != '\'')) ok = false;
        int to = k + 1;
        if (ok && to < end && !isWs(buffer.charAt(to))) ok = false;
        if (ok) emit(FLTokenTypes.CHAR, to, S_NORMAL);
        else emit(TokenType.BAD_CHARACTER, Math.max(i + 1, runEnd(i)), S_NORMAL);
    }

    private void lexBytes(int i, int afterOpen) {
        boolean bad = false;
        int k = afterOpen;
        for (; ; ) {
            while (k < end && isWs(buffer.charAt(k))) k++;
            if (k >= end) { // unterminated
                emit(TokenType.BAD_CHARACTER, end, S_NORMAL);
                return;
            }
            int m = runEnd(k);
            if (m - k == 1 && buffer.charAt(k) == ']') {
                k = m;
                break;
            }
            if (!(m - k == 2 && FLSyntax.isHex(buffer.charAt(k)) && FLSyntax.isHex(buffer.charAt(k + 1)))) bad = true;
            k = m;
        }
        emit(bad ? TokenType.BAD_CHARACTER : FLTokenTypes.BYTES, k, S_NORMAL);
    }

    // ---- header ------------------------------------------------------------------------------------------

    private void lexHeader(int i, char c) {
        if (isWs(c)) {
            int j = i + 1;
            while (j < end && isWs(buffer.charAt(j))) j++;
            emit(TokenType.WHITE_SPACE, j, state);
            return;
        }
        if (c == ')' && (i + 1 >= end || isWs(buffer.charAt(i + 1)))) {
            emit(FLTokenTypes.HDR_RPAREN, i + 1, S_NORMAL);
            return;
        }
        // An item runs to the next whitespace outside a string literal.
        int k = i;
        while (k < end) {
            char d = buffer.charAt(k);
            if (isWs(d)) break;
            if (d == '"') {
                k++;
                while (k < end && buffer.charAt(k) != '"') {
                    if (buffer.charAt(k) == '\\') k++;
                    k++;
                }
                k = Math.min(k + 1, end);
            } else {
                k++;
            }
        }
        String item = buffer.subSequence(i, k).toString();
        boolean in = state == S_HDR_IN;
        if (item.equals("--") && in) {
            emit(FLTokenTypes.HDR_ARROW, k, S_HDR_OUT);
        } else if (item.equals("..")) {
            emit(FLTokenTypes.HDR_DOTS, k, state);
        } else if (in) {
            lexInputItem(i, k, item);
        } else if (FLSyntax.isTypeName(item)) {
            emit(FLTokenTypes.HDR_TYPE, k, state);
        } else {
            emit(FLTokenTypes.HDR_OUTPUT, k, state);
        }
    }

    private void lexInputItem(int i, int k, String item) {
        if (item.charAt(0) == ':') { // the rest of a parameter item, after its name token
            emit(FLTokenTypes.HDR_PARAM_REST, k, state);
            return;
        }
        int colon = -1;
        for (int p = 0; p < item.length(); p++) {
            char ch = item.charAt(p);
            if (ch == ':') {
                colon = p;
                break;
            }
            if ("\"[]{}=|".indexOf(ch) >= 0) break;
        }
        if (colon > 0) {
            emit(FLTokenTypes.HDR_PARAM_NAME, i + colon, state);
        } else if (colon < 0 && FLSyntax.isTypeName(item)) {
            emit(FLTokenTypes.HDR_TYPE, k, state);
        } else if (colon < 0 && FLSyntax.isPname(item)) {
            emit(FLTokenTypes.HDR_PARAM_NAME, k, state);
        } else {
            emit(FLTokenTypes.HDR_OTHER, k, state);
        }
    }
}
