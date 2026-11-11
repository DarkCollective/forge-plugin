// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The {@code pname} of a META: {@code param} clause; refers to a parameter of the target definition. */
public class FLParamRef extends FLPsiElement {
    public FLParamRef(@NotNull ASTNode node) {
        super(node);
    }

    @Nullable
    @Override
    public PsiReference getReference() {
        return new FLParamReference(this);
    }

    @Nullable
    FLParameter resolveTarget() {
        FLMetaBlock block = PsiTreeUtil.getParentOfType(this, FLMetaBlock.class);
        if (block == null) return null;
        FLWordRef target = block.getTarget();
        PsiElement def = target == null ? null : target.resolveTarget();
        return def instanceof FLDefinition d ? d.findParameter(getText()) : null;
    }

    void replaceName(String newName) {
        PsiElement leaf = getFirstChild();
        if (leaf != null) leaf.replace(FLElementFactory.createWordRefLeaf(getProject(), newName));
    }
}
