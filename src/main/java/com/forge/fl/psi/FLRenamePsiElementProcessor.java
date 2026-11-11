package com.forge.fl.psi;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import org.jetbrains.annotations.NotNull;

public class FLRenamePsiElementProcessor extends RenamePsiElementProcessor {
    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
        return element instanceof FLNamedElement || element instanceof FLWordElement || element instanceof FLDefinition;
    }

    @Override
    public boolean isInplaceRenameSupported() {
        return true;
    }

    @Override
    public PsiElement substituteElementToRename(@NotNull PsiElement element, Editor editor) {
        if (element instanceof FLDefinition definition) {
            PsiElement nameIdentifier = definition.getNameIdentifier();
            if (nameIdentifier != null) {
                return nameIdentifier;
            }
        }
        if (element instanceof FLWordElement word) {
            return word;
        }
        return element;
    }
}
