// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.lexer.FLSyntax;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A constant declaration (see docs/LANGUAGE.md section 5). */
public class FLConstant extends FLNamedElement {
    public FLConstant(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    protected IElementType nameTokenType() {
        return FLTokenTypes.WORD_NAME;
    }

    @Override
    public String getKindName() {
        return "constant";
    }

    @Override
    protected PsiElement createNameLeaf(String name) {
        return FLElementFactory.createConstantName(getProject(), name);
    }

    @Nullable
    @Override
    public String getEffectText() {
        PsiElement first = getFirstChild();
        if (first == null) return null;
        String t = FLSyntax.literalType(first.getNode().getElementType(), first.getText());
        return "( -- " + t + " )";
    }

    /** The literal that initialises the declaration. */
    @Override
    public String getLocationText() {
        PsiElement first = getFirstChild();
        return first == null ? null : first.getText();
    }
}
