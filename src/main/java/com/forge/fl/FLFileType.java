package com.forge.fl;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public final class FLFileType extends LanguageFileType {
    public static final FLFileType INSTANCE = new FLFileType();

    private FLFileType() {
        super(FLLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "FL";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Forge FL source file";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "fl";
    }

    @Override
    public Icon getIcon() {
        return FLIcons.FILE;
    }
}
