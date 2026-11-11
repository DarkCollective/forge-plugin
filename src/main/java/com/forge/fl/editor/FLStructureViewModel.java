// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.editor;

import com.forge.fl.psi.FLConstant;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLInForm;
import com.forge.fl.psi.FLPrimitive;
import com.forge.fl.psi.FLVariable;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FLStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {
    public FLStructureViewModel(@NotNull PsiFile psiFile, @Nullable Editor editor) {
        super(psiFile, editor, new FLStructureViewElement(psiFile));
        withSuitableClasses(FLDefinition.class, FLConstant.class, FLVariable.class, FLPrimitive.class, FLInForm.class);
        withSorters(Sorter.ALPHA_SORTER);
    }

    @Override
    public Filter @NotNull [] getFilters() {
        return new Filter[]{PRIVATE_FILTER};
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
        return false;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement element) {
        return element.getValue() instanceof com.forge.fl.psi.FLNamedElement;
    }

    /** "Show private words": reverted, so the words are shown until the action is switched off. */
    public static final Filter PRIVATE_FILTER = new Filter() {
        @Override
        public boolean isVisible(TreeElement treeNode) {
            return !(treeNode instanceof FLStructureViewElement e && e.isPrivate());
        }

        @NotNull
        @Override
        public com.intellij.ide.util.treeView.smartTree.ActionPresentation getPresentation() {
            return new com.intellij.ide.util.treeView.smartTree.ActionPresentationData("Show Private Words", null,
                    com.intellij.icons.AllIcons.Nodes.C_private);
        }

        @NotNull
        @Override
        public String getName() {
            return "FL_SHOW_PRIVATE";
        }

        @Override
        public boolean isReverted() {
            return true;
        }
    };
}
