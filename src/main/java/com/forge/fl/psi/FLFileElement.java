package com.forge.fl.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;

public class FLFileElement extends ASTWrapperPsiElement {
    public FLFileElement(ASTNode node) {
        super(node);
    }
}
