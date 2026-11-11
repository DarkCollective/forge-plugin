package com.forge.fl.psi;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FLWordElement extends LeafPsiElement implements FLWord {
    public FLWordElement(ASTNode node) {
        super(node.getElementType(), node.getText());
    }

    @Override
    public String getName() {
        return getText();
    }

    @Nullable
    @Override
    public PsiElement getNameIdentifier() {
        return this;
    }

    @Override
    public PsiElement setName(@NotNull String name) {
        if (name.equals(getText())) {
            return this;
        }
        FLWordElement replacement = new FLWordElement(ASTFactory.leaf(getNode().getElementType(), name));
        return replace(replacement);
    }
}
