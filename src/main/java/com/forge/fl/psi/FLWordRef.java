// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A use of a word: a body word, a stray top-level word, a META: target or a {@code see-also} name. It wraps one
 * WORD_NAME or QUALIFIED_NAME leaf and carries a soft reference to its declaration in the same file.
 */
public class FLWordRef extends FLPsiElement {
    public FLWordRef(@NotNull ASTNode node) {
        super(node);
    }

    @NotNull
    @Override
    public FLWordReference getReference() {
        return new FLWordReference(this);
    }

    @Nullable
    public PsiElement getNameLeaf() {
        ASTNode n = getNode().getFirstChildNode();
        return n == null ? null : n.getPsi();
    }

    public boolean isQualified() {
        return getText().indexOf(':') > 0;
    }

    /** The text before the colon of a qualified name, or null. */
    @Nullable
    public String getVocab() {
        String t = getText();
        int c = t.indexOf(':');
        return c > 0 ? t.substring(0, c) : null;
    }

    /** The word name without any {@code vocab:} prefix. */
    @NotNull
    public String getBareName() {
        String t = getText();
        return t.substring(t.indexOf(':') + 1);
    }

    /** The single declaration this use resolves to (docs/LANGUAGE.md section 6); null when unresolved or ambiguous. */
    @Nullable
    PsiElement resolveTarget() {
        return getReference().resolve();
    }

    /** Replaces the name part (after any {@code vocab:}) with {@code newName}, plus {@code !} when asked. */
    void replaceName(String newName, boolean bang) {
        PsiElement leaf = getNameLeaf();
        if (leaf == null) return;
        String vocab = getVocab();
        String text = (vocab == null ? "" : vocab + ":") + newName + (bang ? "!" : "");
        leaf.replace(FLElementFactory.createWordRefLeaf(getProject(), text));
    }

    boolean isNameLeafKind() {
        PsiElement leaf = getNameLeaf();
        return leaf != null && (leaf.getNode().getElementType() == FLTokenTypes.WORD_NAME
                || leaf.getNode().getElementType() == FLTokenTypes.QUALIFIED_NAME);
    }
}
