// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.vectors;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Run icons in a vectors file ({@code packages/ops/vocab/<vocab>.test.json}): on the opening {@code [} (the whole
 * file) and on the opening token of each entry, with the result of its last run. Registered for the JSON language
 * by id, so no JSON plugin class is needed.
 */
public final class FLVectorsLineMarkerContributor extends RunLineMarkerContributor {
    @Nullable
    @Override
    public Info getInfo(@NotNull PsiElement element) {
        if (element.getFirstChild() != null) return null;
        String text = element.getText();
        if (!"[".equals(text) && !"{".equals(text)) return null;
        PsiFile file = element.getContainingFile();
        if (file == null || !FLVectorFiles.isVectorsFile(file.getVirtualFile())) return null;
        int offset = element.getTextRange().getStartOffset();
        String name = file.getVirtualFile().getName();
        if ("[".equals(text)) {
            if (offset != FLVectorFiles.arrayStart(file.getViewProvider().getContents())) return null;
            return new Info(AllIcons.RunConfigurations.TestState.Run_run, ExecutorAction.getActions(0),
                    e -> "Run all vectors of " + name);
        }
        List<Integer> offsets = FLVectorFiles.entryOffsets(file);
        int index = offsets.indexOf(offset);
        if (index < 0) return null;
        String url = FLVectorLocator.ENTRY + "://" + name + "#" + index;
        return new Info(getTestStateIcon(url, element.getProject(), false), ExecutorAction.getActions(0),
                e -> "Run entry #" + index + " of " + name);
    }
}
