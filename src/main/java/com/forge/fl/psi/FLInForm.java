// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** {@code IN: v} or the console form {@code VOCAB v}: sets the current vocabulary. */
public class FLInForm extends FLPsiElement {
    public FLInForm(@NotNull ASTNode node) {
        super(node);
    }

    @Nullable
    public String getVocabName() {
        ASTNode n = getNode().findChildByType(FLTokenTypes.WORD_NAME);
        return n == null ? null : n.getText();
    }

    @Nullable
    public PsiElement getVocabElement() {
        ASTNode n = getNode().findChildByType(FLTokenTypes.WORD_NAME);
        return n == null ? null : n.getPsi();
    }
}
