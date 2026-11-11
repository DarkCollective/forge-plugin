// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.diagnostics;

import com.forge.fl.psi.FLElementFactory;
import com.forge.fl.psi.FLWordRef;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/** Quick fix for E_AMBIGUOUS: rewrites the use as {@code vocab:name}. */
public final class FLQualifyFix extends BaseIntentionAction {
    private final SmartPsiElementPointer<FLWordRef> pointer;
    private final String qualified;

    public FLQualifyFix(@NotNull FLWordRef ref, @NotNull String qualified) {
        this.pointer = SmartPointerManager.createPointer(ref);
        this.qualified = qualified;
        setText("Qualify as '" + qualified + "'");
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "Qualify ambiguous FL word";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        FLWordRef ref = pointer.getElement();
        return ref != null && ref.isValid() && !ref.isQualified();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        FLWordRef ref = pointer.getElement();
        if (ref == null) return;
        PsiElement leaf = ref.getNameLeaf();
        if (leaf != null) leaf.replace(FLElementFactory.createWordRefLeaf(project, qualified));
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
