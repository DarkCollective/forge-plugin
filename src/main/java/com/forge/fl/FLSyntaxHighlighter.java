package com.forge.fl;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class FLSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey FL_COMMENT =
            TextAttributesKey.createTextAttributesKey("FL_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey FL_STRING =
            TextAttributesKey.createTextAttributesKey("FL_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey FL_NUMBER =
            TextAttributesKey.createTextAttributesKey("FL_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey FL_KEYWORD =
            TextAttributesKey.createTextAttributesKey("FL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey FL_BOOLEAN =
            TextAttributesKey.createTextAttributesKey("FL_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey FL_IDENTIFIER =
            TextAttributesKey.createTextAttributesKey("FL_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey FL_METADATA =
            TextAttributesKey.createTextAttributesKey("FL_METADATA", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey FL_BAD_CHARACTER =
            TextAttributesKey.createTextAttributesKey("FL_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

    private static final Map<IElementType, TextAttributesKey> ATTRS = new HashMap<>();

    static {
        fillMap(ATTRS, FLTokenTypes.COMMENTS, FL_COMMENT);
        fillMap(ATTRS, FLTokenTypes.STRINGS, FL_STRING);
        fillMap(ATTRS, FLTokenTypes.NUMBERS, FL_NUMBER);
        fillMap(ATTRS, FLTokenTypes.KEYWORDS, FL_KEYWORD);
        ATTRS.put(FLTokenTypes.BOOLEAN, FL_BOOLEAN);
        ATTRS.put(FLTokenTypes.IDENTIFIER, FL_IDENTIFIER);
        ATTRS.put(FLTokenTypes.METADATA_BLOCK, FL_METADATA);
        ATTRS.put(FLTokenTypes.METADATA_CLAUSE, FL_METADATA);
        ATTRS.put(TokenType.BAD_CHARACTER, FL_BAD_CHARACTER);
    }

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new FLLexer();
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        TextAttributesKey key = ATTRS.get(tokenType);
        return key == null ? EMPTY : new TextAttributesKey[]{key};
    }
}
