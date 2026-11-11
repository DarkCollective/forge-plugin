// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** {@code META: name clause... ;} */
public class FLMetaBlock extends FLPsiElement {
    public FLMetaBlock(@NotNull ASTNode node) {
        super(node);
    }

    /** The word the block annotates. */
    @Nullable
    public FLWordRef getTarget() {
        return PsiTreeUtil.getChildOfType(this, FLWordRef.class);
    }

    @NotNull
    public List<FLMetaClause> getClauses() {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, FLMetaClause.class);
    }
}
