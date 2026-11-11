// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/** A alias declaration (see docs/LANGUAGE.md section 5). */
public class FLAlias extends FLNamedElement {
    public FLAlias(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    protected IElementType nameTokenType() {
        return FLTokenTypes.WORD_NAME;
    }

    @Override
    public String getKindName() {
        return "alias";
    }

    @Override
    protected PsiElement createNameLeaf(String name) {
        return FLElementFactory.createAliasName(getProject(), name);
    }
}
