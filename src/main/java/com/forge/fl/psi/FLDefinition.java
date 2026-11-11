// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/** {@code : name ( header ) decls body ;} */
public class FLDefinition extends FLNamedElement {
    public FLDefinition(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    protected IElementType nameTokenType() {
        return FLTokenTypes.WORD_NAME;
    }

    @Override
    public String getKindName() {
        return "word";
    }

    @Override
    protected PsiElement createNameLeaf(String name) {
        return FLElementFactory.createDefinitionName(getProject(), name);
    }

    @Nullable
    public FLHeader getHeader() {
        return PsiTreeUtil.getChildOfType(this, FLHeader.class);
    }

    @NotNull
    public List<FLParameter> getParameters() {
        FLHeader h = getHeader();
        return h == null ? Collections.emptyList() : h.getParameters();
    }

    @Nullable
    public FLParameter findParameter(String name) {
        for (FLParameter p : getParameters()) {
            if (name.equals(p.getName())) return p;
        }
        return null;
    }

    @Nullable
    @Override
    public String getEffectText() {
        FLHeader h = getHeader();
        return h == null ? null : h.getText().replaceAll("\\s+", " ");
    }

    @Override
    public String getLocationText() {
        FLHeader h = getHeader();
        return h == null ? null : h.getText();
    }
}
