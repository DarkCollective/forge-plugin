package com.forge.fl.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class FLDefinitionImpl extends ASTWrapperPsiElement implements FLDefinition {
    public FLDefinitionImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public String getName() {
        PsiElement[] children = getChildren();
        for (PsiElement child : children) {
            if (child instanceof FLWordElement) {
                return child.getText();
            }
        }
        return "";
    }

    @Override
    public PsiElement getNameIdentifier() {
        PsiElement[] children = getChildren();
        for (PsiElement child : children) {
            if (child instanceof FLWordElement) {
                return child;
            }
        }
        return null;
    }

    @Override
    public PsiElement setName(@NotNull String name) {
        PsiElement identifier = getNameIdentifier();
        if (identifier instanceof FLWordElement word) {
            return word.setName(name);
        }
        return this;
    }
}
