package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.intellij.psi.tree.TokenSet;

public final class FLTokenSets {
    public static final TokenSet COMMENTS = FLTokenTypes.COMMENTS;
    public static final TokenSet STRINGS = FLTokenTypes.STRINGS;
    public static final TokenSet KEYWORDS = FLTokenTypes.KEYWORDS;
    public static final TokenSet NUMBERS = FLTokenTypes.NUMBERS;
    public static final TokenSet PUNCTUATION = FLTokenTypes.PUNCTUATION;

    private FLTokenSets() {
    }
}
