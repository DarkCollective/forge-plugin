// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.intentions;

import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLMetaBlock;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public final class FLMoveMetaBlockIntention extends BaseIntentionAction {
    public FLMoveMetaBlockIntention() {
        setText("Move META block next to definition");
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "FL META block";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        FLMetaBlock block = findBlock(editor, file);
        FLDefinition definition = block == null ? null : FLMetaBlockMover.targetDefinition(block);
        return definition != null && !FLMetaBlockMover.isImmediatelyAfter(block, definition);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        FLMetaBlock block = findBlock(editor, file);
        FLDefinition definition = block == null ? null : FLMetaBlockMover.targetDefinition(block);
        if (definition != null && !FLMetaBlockMover.isImmediatelyAfter(block, definition)) {
            FLMetaBlockMover.move(project, file, block, definition);
        }
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    private static FLMetaBlock findBlock(Editor editor, PsiFile file) {
        PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
        return leaf == null ? null : PsiTreeUtil.getParentOfType(leaf, FLMetaBlock.class);
    }
}
