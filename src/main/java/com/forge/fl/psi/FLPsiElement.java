package com.forge.fl.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/** Base of every composite FL PSI element. Nothing in FL extends LeafPsiElement. */
public class FLPsiElement extends ASTWrapperPsiElement {
    public FLPsiElement(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public String toString() {
        return getNode().getElementType().toString();
    }
}
