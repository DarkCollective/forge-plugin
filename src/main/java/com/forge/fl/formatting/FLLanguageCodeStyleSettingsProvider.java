// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.formatting;

import com.forge.fl.FLLanguage;
import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The FL code style page (Settings | Editor | Code Style | FL) and its defaults. The default indent is two spaces,
 * the house style of the Forge sources (docs/FL-STYLE.md); a project can still override it.
 */
public final class FLLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
    public static final int DEFAULT_INDENT = 2;

    @NotNull
    @Override
    public Language getLanguage() {
        return FLLanguage.INSTANCE;
    }

    @Override
    protected void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings,
                                     @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
        indentOptions.INDENT_SIZE = DEFAULT_INDENT;
        indentOptions.CONTINUATION_INDENT_SIZE = DEFAULT_INDENT;
        indentOptions.TAB_SIZE = DEFAULT_INDENT;
        indentOptions.USE_TAB_CHARACTER = false;
    }

    @Nullable
    @Override
    public IndentOptionsEditor getIndentOptionsEditor() {
        return new SmartIndentOptionsEditor();
    }

    @Nullable
    @Override
    public String getCodeSample(@NotNull SettingsType settingsType) {
        return """
                \\ Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
                \\ demo: a sample of the FL house style.
                IN: demo

                USING: util ;

                CHOICE: mode
                  option cbc "Cipher block chaining." ;
                  option ctr ;
                ;

                : encrypt ( data:bytes key:key{hex|utf8} m:choice<mode>=cbc -- bytes )
                  "Encrypt bytes." doc
                  "Encryption" category
                  data key m drop drop ;

                META: encrypt
                  param key "The key." ;
                  use-when "You need to encrypt bytes." ;
                ;

                : _days ( days:int -- int )
                  days 719468 + :> z
                  z [ 146097 div ] [ 0 ] if ;
                """;
    }
}
