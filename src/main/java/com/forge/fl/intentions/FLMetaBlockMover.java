// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.intentions;

import com.forge.fl.FLFile;
import com.forge.fl.formatting.FLTrailingWhitespaceProcessor;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLMetaBlock;
import com.forge.fl.psi.FLWordRef;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public final class FLMetaBlockMover {
    private FLMetaBlockMover() {
    }

    @Nullable
    public static FLDefinition targetDefinition(FLMetaBlock block) {
        FLWordRef target = block.getTarget();
        PsiElement resolved = target == null ? null : target.getReference().resolve();
        return resolved instanceof FLDefinition definition && definition.getContainingFile().equals(block.getContainingFile())
                ? definition : null;
    }

    public static boolean isImmediatelyAfter(FLMetaBlock block, FLDefinition definition) {
        if (definition.getTextRange().getEndOffset() > block.getTextRange().getStartOffset()) return false;
        PsiElement next = definition.getNextSibling();
        while (next != null && next != block) {
            if (!next.getText().isBlank()) return false;
            next = next.getNextSibling();
        }
        return next == block;
    }

    public static void move(Project project, PsiFile file, FLMetaBlock block, FLDefinition definition) {
        PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
        Document document = manager.getDocument(file);
        if (document == null) return;

        int blockStart = block.getTextRange().getStartOffset();
        int blockEnd = block.getTextRange().getEndOffset();
        int definitionEnd = definition.getTextRange().getEndOffset();
        CharSequence text = document.getCharsSequence();
        int removalStart = blockStart;
        int lineStart = blockStart;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n' && text.charAt(lineStart - 1) != '\r') {
            lineStart--;
        }
        boolean onlyIndentBeforeBlock = true;
        for (int i = lineStart; i < blockStart; i++) {
            if (text.charAt(i) != ' ' && text.charAt(i) != '\t') {
                onlyIndentBeforeBlock = false;
                break;
            }
        }
        if (onlyIndentBeforeBlock && lineStart > 0) {
            removalStart = lineStart - 1;
            if (text.charAt(removalStart) == '\n' && removalStart > 0 && text.charAt(removalStart - 1) == '\r') {
                removalStart--;
            }
        }
        String blockText = text.subSequence(blockStart, blockEnd).toString();

        document.deleteString(removalStart, blockEnd);
        int insertionOffset = definitionEnd - (removalStart < definitionEnd ? blockEnd - removalStart : 0);
        document.insertString(insertionOffset, "\n\n" + blockText);
        manager.commitDocument(document);
        if (file instanceof FLFile flFile) {
            FLTrailingWhitespaceProcessor.trimTrailingWhitespace(flFile);
        }
    }
}
