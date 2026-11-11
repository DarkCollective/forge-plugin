// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.editor;

import com.forge.fl.FLLanguage;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLMetaBlock;
import com.forge.fl.psi.FLWordRef;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Breadcrumbs: the definition or META: block around the caret. */
public final class FLBreadcrumbsProvider implements BreadcrumbsProvider {
    @Override
    public Language[] getLanguages() {
        return new Language[]{FLLanguage.INSTANCE};
    }

    @Override
    public boolean acceptElement(@NotNull PsiElement e) {
        return e instanceof FLDefinition || e instanceof FLMetaBlock;
    }

    @NotNull
    @Override
    public String getElementInfo(@NotNull PsiElement e) {
        if (e instanceof FLDefinition d) return ": " + d.getName();
        FLWordRef t = ((FLMetaBlock) e).getTarget();
        return "META: " + (t == null ? "" : t.getText());
    }

    @Nullable
    @Override
    public String getElementTooltip(@NotNull PsiElement e) {
        return e instanceof FLDefinition d ? d.getEffectText() : null;
    }
}
