// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A mid-body local, {@code :> name} (FL v0.2, docs/LANGUAGE.md 7.1). The name is visible from the next token to
 * the end of the innermost enclosing body: the colon or {@code MEMO:} body, or the {@code [ ... ]} quotation that
 * directly holds the binding.
 */
public class FLLocal extends FLNamedElement {
    public FLLocal(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    protected IElementType nameTokenType() {
        return FLTokenTypes.WORD_NAME;
    }

    @Override
    public String getKindName() {
        return "local";
    }

    @Override
    protected PsiElement createNameLeaf(String name) {
        return FLElementFactory.createLocalName(getProject(), name);
    }

    /** The body the binding belongs to: its quotation or definition; null for a {@code :>} outside any body. */
    @Nullable
    public PsiElement getScopeOwner() {
        PsiElement p = getParent();
        if (p instanceof FLDefinition) return p;
        if (p != null && p.getNode().getElementType() == FLElementTypes.QUOTATION) return p;
        return null; // inside { } or at top level: an error, and visible nowhere
    }

    /** Whether the name is in scope at {@code offset} (spec 7.1: from the next token to the end of its body). */
    public boolean isVisibleAt(int offset) {
        PsiElement owner = getScopeOwner();
        return owner != null && offset >= getTextRange().getEndOffset() && offset < owner.getTextRange().getEndOffset();
    }

    @NotNull
    @Override
    public SearchScope getUseScope() {
        PsiElement owner = getScopeOwner();
        return new LocalSearchScope(owner != null ? owner : this);
    }

    @Override
    public String getLocationText() {
        FLDefinition def = PsiTreeUtil.getParentOfType(this, FLDefinition.class);
        return def == null ? null : def.getName();
    }
}
