// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.editor;

import com.forge.fl.psi.FLConstant;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLInForm;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.psi.FLPrimitive;
import com.forge.fl.psi.FLResolver;
import com.forge.fl.psi.FLScope;
import com.forge.fl.psi.FLVariable;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structure view: a file lists its definitions, constants, variables and primitives. When the file has more than
 * one {@code IN:} the words are grouped by vocabulary. Private words (a leading {@code _}) are marked with a
 * private overlay icon and a tooltip.
 */
public class FLStructureViewElement extends PsiTreeElementBase<PsiElement> {
    /** For a vocabulary group: the vocabulary name. */
    private final String group;

    public FLStructureViewElement(PsiElement element) {
        this(element, null);
    }

    private FLStructureViewElement(PsiElement element, String group) {
        super(element);
        this.group = group;
    }

    public boolean isGroup() {
        return group != null;
    }

    public boolean isPrivate() {
        return getElement() instanceof FLNamedElement n && n.getName() != null && FLResolver.isPrivateName(n.getName());
    }

    private static boolean isDeclaration(PsiElement c) {
        return c instanceof FLDefinition || c instanceof FLConstant || c instanceof FLVariable || c instanceof FLPrimitive;
    }

    @NotNull
    @Override
    public Collection<StructureViewTreeElement> getChildrenBase() {
        List<StructureViewTreeElement> result = new ArrayList<>();
        PsiElement element = getElement();
        if (group != null) return groupChildren();
        if (!(element instanceof PsiFile)) return result;
        int ins = 0;
        for (PsiElement c = element.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof FLInForm in && in.getVocabName() != null) ins++;
        }
        FLScope scope = FLScope.of((PsiFile) element);
        Map<String, FLStructureViewElement> groups = new LinkedHashMap<>();
        for (PsiElement c = element.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (!isDeclaration(c)) continue;
            if (ins <= 1) {
                result.add(new FLStructureViewElement(c));
            } else {
                String vocab = scope.vocabAt(c.getTextRange().getStartOffset());
                groups.computeIfAbsent(vocab, v -> new FLStructureViewElement(element, v));
            }
        }
        result.addAll(groups.values());
        return result;
    }

    private Collection<StructureViewTreeElement> groupChildren() {
        List<StructureViewTreeElement> result = new ArrayList<>();
        PsiElement file = getElement();
        FLScope scope = FLScope.of((PsiFile) file);
        for (PsiElement c = file.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (isDeclaration(c) && scope.vocabAt(c.getTextRange().getStartOffset()).equals(group)) {
                result.add(new FLStructureViewElement(c));
            }
        }
        return result;
    }

    @Nullable
    @Override
    public String getPresentableText() {
        if (group != null) return group;
        PsiElement element = getElement();
        if (element instanceof FLNamedElement named) return named.getName();
        return element == null ? null : element.getContainingFile().getName();
    }

    @Nullable
    @Override
    public String getLocationString() {
        PsiElement element = getElement();
        return group == null && element instanceof FLNamedElement named ? named.getLocationText() : null;
    }

    @Nullable
    @Override
    public Icon getIcon(boolean open) {
        PsiElement element = getElement();
        if (element == null) return null;
        if (group != null) return AllIcons.Nodes.Package;
        Icon base = element.getIcon(0);
        if (base != null && isPrivate()) {
            return IconManager.getInstance().createRowIcon(base, AllIcons.Nodes.C_private);
        }
        return base;
    }

    @NotNull
    @Override
    public ItemPresentation getPresentation() {
        PresentationData data = new PresentationData(getPresentableText(), getLocationString(), getIcon(false), null);
        if (isPrivate()) data.setTooltip("Private word");
        return data;
    }
}
