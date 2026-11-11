package com.forge.fl.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class FLHeaderImpl extends ASTWrapperPsiElement implements FLHeader {
    public FLHeaderImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public String getTextValue() {
        return getText();
    }
}
