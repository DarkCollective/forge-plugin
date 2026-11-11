// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.navigation;

import com.forge.fl.psi.FLNamedElement;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiElement;
import com.intellij.navigation.PsiElementNavigationItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A Go to Symbol result: the word name, with the vocabulary and the file as its location. */
public final class FLSymbolItem implements PsiElementNavigationItem {
    private final FLNamedElement element;
    private final String qualifiedName;

    FLSymbolItem(@NotNull FLNamedElement element, @NotNull String qualifiedName) {
        this.element = element;
        this.qualifiedName = qualifiedName;
    }

    @NotNull
    public String getQualifiedName() {
        return qualifiedName;
    }

    @Nullable
    @Override
    public PsiElement getTargetElement() {
        return element;
    }

    @Override
    public String getName() {
        return element.getName();
    }

    @Override
    public ItemPresentation getPresentation() {
        int colon = qualifiedName.indexOf(':');
        String vocab = colon > 0 ? qualifiedName.substring(0, colon) : "";
        String name = String.valueOf(element.getName());
        String effect = element.getEffectText();
        return new PresentationData(effect == null ? name : name + " " + effect,
                "(" + vocab + ") " + element.getContainingFile().getName(), element.getIcon(0), null);
    }

    @Override
    public void navigate(boolean requestFocus) {
        ((NavigationItem) element).navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
        return element.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return element.canNavigateToSource();
    }
}
