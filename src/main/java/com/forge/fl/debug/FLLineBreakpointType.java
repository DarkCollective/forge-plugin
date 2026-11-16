// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.debug;

import com.forge.fl.FLFileType;
import com.forge.fl.FLTokenTypes;
import com.forge.fl.builtins.FLBuiltins;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A line breakpoint in an FL file: on a line that holds code (not only white space or comments). */
public final class FLLineBreakpointType extends XLineBreakpointType<XBreakpointProperties<?>> {
    public static final String ID = "fl-line";

    public FLLineBreakpointType() {
        super(ID, "FL Line Breakpoints");
    }

    @Nullable
    @Override
    public XBreakpointProperties<?> createBreakpointProperties(@NotNull VirtualFile file, int line) {
        return null;
    }

    @Override
    public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
        if (file.getFileType() != FLFileType.INSTANCE || FLBuiltins.isBuiltinFile(file)) return false;
        Document doc = FileDocumentManager.getInstance().getDocument(file);
        if (doc == null || line < 0 || line >= doc.getLineCount()) return false;
        PsiFile psi = PsiManager.getInstance(project).findFile(file);
        if (psi == null) return false;
        int start = doc.getLineStartOffset(line);
        int end = doc.getLineEndOffset(line);
        for (int offset = start; offset < end; ) {
            PsiElement leaf = psi.findElementAt(offset);
            if (leaf == null) break;
            IElementType t = leaf.getNode().getElementType();
            if (!(leaf instanceof PsiWhiteSpace) && t != FLTokenTypes.LINE_COMMENT && t != FLTokenTypes.BLOCK_COMMENT) {
                return true;
            }
            offset = Math.max(offset + 1, leaf.getTextRange().getEndOffset());
        }
        return false;
    }
}
