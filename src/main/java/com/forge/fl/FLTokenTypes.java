package com.forge.fl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public final class FLTokenTypes {
    public static final com.intellij.psi.tree.IFileElementType FILE = new com.intellij.psi.tree.IFileElementType("FL_FILE", FLLanguage.INSTANCE);
    public static final IElementType COMMENT = new IElementType("FL_COMMENT", FLLanguage.INSTANCE);
    public static final IElementType STRING = new IElementType("FL_STRING", FLLanguage.INSTANCE);
    public static final IElementType NUMBER = new IElementType("FL_NUMBER", FLLanguage.INSTANCE);
    public static final IElementType WORD = new IElementType("FL_WORD", FLLanguage.INSTANCE);
    public static final IElementType KEYWORD = new IElementType("FL_KEYWORD", FLLanguage.INSTANCE);
    public static final IElementType BOOLEAN = new IElementType("FL_BOOLEAN", FLLanguage.INSTANCE);
    public static final IElementType IDENTIFIER = new IElementType("FL_IDENTIFIER", FLLanguage.INSTANCE);
    public static final IElementType LBRACKET = new IElementType("FL_LBRACKET", FLLanguage.INSTANCE);
    public static final IElementType RBRACKET = new IElementType("FL_RBRACKET", FLLanguage.INSTANCE);
    public static final IElementType LBRACE = new IElementType("FL_LBRACE", FLLanguage.INSTANCE);
    public static final IElementType RBRACE = new IElementType("FL_RBRACE", FLLanguage.INSTANCE);
    public static final IElementType LPAREN = new IElementType("FL_LPAREN", FLLanguage.INSTANCE);
    public static final IElementType RPAREN = new IElementType("FL_RPAREN", FLLanguage.INSTANCE);

    public static final TokenSet KEYWORDS = TokenSet.orSet(TokenSet.create(KEYWORD), TokenSet.create(BOOLEAN));
    public static final TokenSet PUNCTUATION = TokenSet.create(LBRACKET, RBRACKET, LBRACE, RBRACE, LPAREN, RPAREN);
    public static final TokenSet STRINGS = TokenSet.create(STRING);
    public static final TokenSet COMMENTS = TokenSet.create(COMMENT);
    public static final TokenSet NUMBERS = TokenSet.create(NUMBER);

    private FLTokenTypes() {
    }
}
