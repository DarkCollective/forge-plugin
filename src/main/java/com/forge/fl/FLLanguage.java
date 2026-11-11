package com.forge.fl;

import com.intellij.lang.Language;

public final class FLLanguage extends Language {
    public static final FLLanguage INSTANCE = new FLLanguage();

    private FLLanguage() {
        super("FL");
    }
}
