// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.builtins.FLBuiltins;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class FLRefactoringSupportProvider extends RefactoringSupportProvider {
    @Override
    public boolean isMemberInplaceRenameAvailable(@NotNull PsiElement element, PsiElement context) {
        return element instanceof FLNamedElement && !FLBuiltins.isBuiltin(element);
    }

    @Override
    public boolean isInplaceRenameAvailable(@NotNull PsiElement element, PsiElement context) {
        return element instanceof FLNamedElement && !FLBuiltins.isBuiltin(element);
    }

    @Override
    public com.intellij.refactoring.RefactoringActionHandler getIntroduceVariableHandler() {
        return new com.forge.fl.refactoring.FLIntroduceLocal();
    }

    @Override
    public com.intellij.refactoring.RefactoringActionHandler getExtractMethodHandler() {
        return new com.forge.fl.refactoring.FLExtractWord();
    }

    @Override
    public com.intellij.refactoring.changeSignature.ChangeSignatureHandler getChangeSignatureHandler() {
        return new com.forge.fl.refactoring.FLChangeSignatureHandler();
    }
}
