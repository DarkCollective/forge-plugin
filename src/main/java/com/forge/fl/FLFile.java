// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Key;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class FLFile extends PsiFileBase {
    /**
     * Marks the file behind a debugger expression editor (Evaluate, watches): body code of the paused word, whose
     * locals the file cannot see, so diagnostics are not computed for it.
     */
    public static final Key<Boolean> DEBUGGER_FRAGMENT = Key.create("com.forge.fl.debuggerFragment");

    public FLFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, FLLanguage.INSTANCE);
    }

    public static boolean isDebuggerFragment(@NotNull PsiFile file) {
        PsiFile original = file.getOriginalFile();
        return Boolean.TRUE.equals(file.getUserData(DEBUGGER_FRAGMENT))
                || Boolean.TRUE.equals(original.getUserData(DEBUGGER_FRAGMENT));
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
