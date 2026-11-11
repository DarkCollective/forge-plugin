// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.highlighting;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.lexer.FLLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class FLSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey LINE_COMMENT =
            createTextAttributesKey("FL_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey BLOCK_COMMENT =
            createTextAttributesKey("FL_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    public static final TextAttributesKey STRING =
            createTextAttributesKey("FL_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey CHAR =
            createTextAttributesKey("FL_CHAR", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey BYTES =
            createTextAttributesKey("FL_BYTES", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("FL_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey BOOL =
            createTextAttributesKey("FL_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey KEYWORD =
            createTextAttributesKey("FL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey BRACKETS =
            createTextAttributesKey("FL_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey BRACES =
            createTextAttributesKey("FL_BRACES", DefaultLanguageHighlighterColors.BRACES);
    public static final TextAttributesKey WORD =
            createTextAttributesKey("FL_WORD", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey QUALIFIED_WORD =
            createTextAttributesKey("FL_QUALIFIED_WORD", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey HEADER_PAREN =
            createTextAttributesKey("FL_HEADER_PAREN", DefaultLanguageHighlighterColors.PARENTHESES);
    public static final TextAttributesKey HEADER_PARAMETER =
            createTextAttributesKey("FL_HEADER_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER);
    public static final TextAttributesKey HEADER_PARAMETER_DETAILS =
            createTextAttributesKey("FL_HEADER_PARAMETER_DETAILS", DefaultLanguageHighlighterColors.METADATA);
    public static final TextAttributesKey HEADER_TYPE =
            createTextAttributesKey("FL_HEADER_TYPE", DefaultLanguageHighlighterColors.CLASS_REFERENCE);
    public static final TextAttributesKey HEADER_ARROW =
            createTextAttributesKey("FL_HEADER_ARROW", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey HEADER_OUTPUT =
            createTextAttributesKey("FL_HEADER_OUTPUT", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey BAD_CHARACTER =
            createTextAttributesKey("FL_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

    // Semantic keys, applied by FLSemanticAnnotator (the lexer cannot know what a word is).
    public static final TextAttributesKey META_KEYWORD =
            createTextAttributesKey("FL_META_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey DECLARATION_NAME =
            createTextAttributesKey("FL_DECLARATION_NAME", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
    public static final TextAttributesKey PARAMETER_USE =
            createTextAttributesKey("FL_PARAMETER_USE", DefaultLanguageHighlighterColors.PARAMETER);
    public static final TextAttributesKey BUILTIN_WORD =
            createTextAttributesKey("FL_BUILTIN_WORD", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL);
    public static final TextAttributesKey VOCABULARY =
            createTextAttributesKey("FL_VOCABULARY", DefaultLanguageHighlighterColors.CLASS_NAME);

    private static final Map<IElementType, TextAttributesKey> ATTRS = new HashMap<>();

    static {
        ATTRS.put(FLTokenTypes.LINE_COMMENT, LINE_COMMENT);
        ATTRS.put(FLTokenTypes.BLOCK_COMMENT, BLOCK_COMMENT);
        ATTRS.put(FLTokenTypes.STRING, STRING);
        ATTRS.put(FLTokenTypes.CHAR, CHAR);
        ATTRS.put(FLTokenTypes.BYTES, BYTES);
        ATTRS.put(FLTokenTypes.NUMBER, NUMBER);
        ATTRS.put(FLTokenTypes.BOOL, BOOL);
        for (IElementType k : FLTokenTypes.KEYWORDS.getTypes()) ATTRS.put(k, KEYWORD);
        ATTRS.put(FLTokenTypes.LBRACKET, BRACKETS);
        ATTRS.put(FLTokenTypes.RBRACKET, BRACKETS);
        ATTRS.put(FLTokenTypes.LBRACE, BRACES);
        ATTRS.put(FLTokenTypes.RBRACE, BRACES);
        ATTRS.put(FLTokenTypes.WORD_NAME, WORD);
        ATTRS.put(FLTokenTypes.QUALIFIED_NAME, QUALIFIED_WORD);
        ATTRS.put(FLTokenTypes.HDR_LPAREN, HEADER_PAREN);
        ATTRS.put(FLTokenTypes.HDR_RPAREN, HEADER_PAREN);
        ATTRS.put(FLTokenTypes.HDR_PARAM_NAME, HEADER_PARAMETER);
        ATTRS.put(FLTokenTypes.HDR_PARAM_REST, HEADER_PARAMETER_DETAILS);
        ATTRS.put(FLTokenTypes.HDR_OTHER, HEADER_PARAMETER_DETAILS);
        ATTRS.put(FLTokenTypes.HDR_TYPE, HEADER_TYPE);
        ATTRS.put(FLTokenTypes.HDR_ARROW, HEADER_ARROW);
        ATTRS.put(FLTokenTypes.HDR_DOTS, HEADER_ARROW);
        ATTRS.put(FLTokenTypes.HDR_OUTPUT, HEADER_OUTPUT);
        ATTRS.put(TokenType.BAD_CHARACTER, BAD_CHARACTER);
    }

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new FLLexer();
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        return pack(ATTRS.get(tokenType));
    }
}
