// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.editor;

import com.forge.fl.FLFile;
import com.forge.fl.psi.FLChoiceType;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLMetaBlock;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Live template contexts for FL (registered with {@code contextId}s FL, FL_TOPLEVEL and FL_BODY): anywhere in an FL
 * file, at the top level (where definitions, {@code META:} and {@code CHOICE:} blocks go), and in a word body (where
 * quotations and combinators go).
 */
public final class FLTemplateContexts {
    private FLTemplateContexts() {
    }

    /** The element at the start of the template, or just before it at the end of the text. */
    @Nullable
    static PsiElement at(@NotNull TemplateActionContext context) {
        PsiFile file = context.getFile();
        if (!(file instanceof FLFile)) return null;
        int offset = context.getStartOffset();
        PsiElement e = file.findElementAt(offset);
        if (e == null && offset > 0) e = file.findElementAt(offset - 1);
        return e == null ? file : e;
    }

    /** Whether the offset is in a body: inside a definition, after its header. */
    static boolean inBody(@NotNull TemplateActionContext context) {
        PsiElement e = at(context);
        FLDefinition def = e == null ? null : PsiTreeUtil.getParentOfType(e, FLDefinition.class, false);
        return def != null && def.getHeader() != null && context.getStartOffset() >= def.getHeader().getTextRange().getEndOffset();
    }

    public static final class Any extends TemplateContextType {
        public Any() {
            super("FL");
        }

        @Override
        public boolean isInContext(@NotNull TemplateActionContext context) {
            return context.getFile() instanceof FLFile;
        }
    }

    public static final class TopLevel extends TemplateContextType {
        public TopLevel() {
            super("Top level");
        }

        @Override
        public boolean isInContext(@NotNull TemplateActionContext context) {
            PsiElement e = at(context);
            return e != null && PsiTreeUtil.getParentOfType(e, FLDefinition.class, FLMetaBlock.class, FLChoiceType.class) == null;
        }
    }

    public static final class Body extends TemplateContextType {
        public Body() {
            super("Word body");
        }

        @Override
        public boolean isInContext(@NotNull TemplateActionContext context) {
            return inBody(context);
        }
    }
}
