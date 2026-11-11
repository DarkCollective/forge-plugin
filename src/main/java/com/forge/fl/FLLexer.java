package com.forge.fl;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class FLLexer extends LexerBase {
    private static final Set<String> KEYWORDS = Set.of(
            ":",
            ";",
            "IN:",
            "USING:",
            "META:",
            "VOCAB",
            "CONSTANT",
            "VARIABLE",
            "PRIMITIVE",
            "recurse",
            "true",
            "false",
            "param",
            "category",
            "doc",
            "version",
            "alias",
            "aliases",
            "see-also",
            "seeAlso",
            "name",
            "vocab",
            "qname",
            "kind",
            "exported",
            "op",
            "effect",
            "params",
            "effects",
            "hash",
            "source"
    );

    private CharSequence myBuffer;
    private int myStart;
    private int myEnd;
    private int myIndex;
    private int myTokenStart;
    private int myTokenEnd;
    private IElementType myTokenType;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        myBuffer = buffer;
        myStart = startOffset;
        myEnd = endOffset;
        myIndex = startOffset;
        myTokenStart = startOffset;
        myTokenEnd = startOffset;
        myTokenType = null;
        advance();
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return myTokenType;
    }

    @Override
    public int getTokenStart() {
        return myTokenStart;
    }

    @Override
    public int getTokenEnd() {
        return myTokenEnd;
    }

    @Override
    public void advance() {
        if (myIndex >= myEnd) {
            myTokenType = null;
            myTokenStart = myEnd;
            myTokenEnd = myEnd;
            return;
        }

        myTokenStart = myIndex;
        myTokenType = TokenType.WHITE_SPACE;

        while (myIndex < myEnd && Character.isWhitespace(myBuffer.charAt(myIndex))) {
            myIndex++;
        }
        if (myIndex > myTokenStart) {
            myTokenEnd = myIndex;
            return;
        }

        char c = myBuffer.charAt(myIndex);
        if (c == '\\') {
            myTokenType = FLTokenTypes.COMMENT;
            myIndex++;
            while (myIndex < myEnd && myBuffer.charAt(myIndex) != '\n' && myBuffer.charAt(myIndex) != '\r') {
                myIndex++;
            }
            myTokenEnd = myIndex;
            return;
        }

        if (c == '"') {
            myTokenType = FLTokenTypes.STRING;
            myIndex++;
            while (myIndex < myEnd) {
                char ch = myBuffer.charAt(myIndex);
                if (ch == '\\') {
                    myIndex += 2;
                    continue;
                }
                if (ch == '"') {
                    myIndex++;
                    break;
                }
                myIndex++;
            }
            myTokenEnd = myIndex;
            return;
        }

        if (Character.isDigit(c) || (c == '-' && myIndex + 1 < myEnd && Character.isDigit(myBuffer.charAt(myIndex + 1)))) {
            myTokenType = FLTokenTypes.NUMBER;
            myIndex++;
            while (myIndex < myEnd) {
                char ch = myBuffer.charAt(myIndex);
                if (Character.isDigit(ch) || ch == '_' || ch == '.' || ch == 'e' || ch == 'E' || ch == 'x' || ch == 'X' || ch == 'b' || ch == 'B') {
                    myIndex++;
                } else {
                    break;
                }
            }
            myTokenEnd = myIndex;
            return;
        }

        if (isNameStart(c)) {
            myTokenType = FLTokenTypes.IDENTIFIER;
            myIndex++;
            while (myIndex < myEnd && isNameChar(myBuffer.charAt(myIndex))) {
                myIndex++;
            }
            String word = myBuffer.subSequence(myTokenStart, myIndex).toString();
            if (KEYWORDS.contains(word)) {
                myTokenType = FLTokenTypes.KEYWORD;
            }
            if ("true".equals(word) || "false".equals(word)) {
                myTokenType = FLTokenTypes.BOOLEAN;
            }
            myTokenEnd = myIndex;
            return;
        }

        if (c == '[') {
            myTokenType = FLTokenTypes.LBRACKET;
            myIndex++;
            myTokenEnd = myIndex;
            return;
        }
        if (c == ']') {
            myTokenType = FLTokenTypes.RBRACKET;
            myIndex++;
            myTokenEnd = myIndex;
            return;
        }
        if (c == '{') {
            myTokenType = FLTokenTypes.LBRACE;
            myIndex++;
            myTokenEnd = myIndex;
            return;
        }
        if (c == '}') {
            myTokenType = FLTokenTypes.RBRACE;
            myIndex++;
            myTokenEnd = myIndex;
            return;
        }
        if (c == '(') {
            myTokenType = FLTokenTypes.LPAREN;
            myIndex++;
            myTokenEnd = myIndex;
            return;
        }
        if (c == ')') {
            myTokenType = FLTokenTypes.RPAREN;
            myIndex++;
            myTokenEnd = myIndex;
            return;
        }
        if (c == ':' || c == ';') {
            myTokenType = FLTokenTypes.KEYWORD;
            myIndex++;
            myTokenEnd = myIndex;
            return;
        }

        if (c == '#' && myIndex + 1 < myEnd && myBuffer.charAt(myIndex + 1) == '[') {
            myTokenType = FLTokenTypes.KEYWORD;
            myIndex += 2;
            while (myIndex < myEnd && myBuffer.charAt(myIndex) != ']') {
                myIndex++;
            }
            if (myIndex < myEnd) {
                myIndex++;
            }
            myTokenEnd = myIndex;
            return;
        }

        myTokenType = TokenType.BAD_CHARACTER;
        myIndex++;
        myTokenEnd = myIndex;
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return myBuffer;
    }

    @Override
    public int getBufferEnd() {
        return myEnd;
    }

    private boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '-' || c == '!' || c == '$' || c == '%' || c == '&'
                || c == '*' || c == '+' || c == ',' || c == '.' || c == '/' || c == '<' || c == '=' || c == '>'
                || c == '?' || c == '@' || c == '^' || c == '|' || c == '~';
    }

    private boolean isNameChar(char c) {
        return isNameStart(c) || Character.isDigit(c);
    }
}
