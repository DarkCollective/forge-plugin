// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl;

import com.intellij.lang.Language;

public final class FLLanguage extends Language {
    public static final FLLanguage INSTANCE = new FLLanguage();

    private FLLanguage() {
        super("FL");
    }
}
