package com.forge.fl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public class FLFile extends PsiFileBase {
    public FLFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, FLLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return FLFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "FL File";
    }
}
