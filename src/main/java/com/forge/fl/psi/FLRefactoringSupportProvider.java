package com.forge.fl.psi;

import com.forge.fl.FLLanguage;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class FLRefactoringSupportProvider extends RefactoringSupportProvider {
    @Override
    public boolean isAvailable(@NotNull PsiElement element) {
        return element instanceof FLNamedElement || element instanceof FLWordElement;
    }

    @Override
    public boolean isInplaceRenameAvailable(@NotNull PsiElement element, PsiElement context) {
        return isAvailable(element);
    }

    @Override
    public boolean isMemberInplaceRenameAvailable(@NotNull PsiElement element, PsiElement context) {
        return isAvailable(element);
    }
}
