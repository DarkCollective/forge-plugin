// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.hierarchy;

import com.forge.fl.FLFile;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLPrimitive;
import com.forge.fl.psi.FLWordRef;
import com.intellij.ide.hierarchy.CallHierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTree;
import java.util.Comparator;
import java.util.Map;

/**
 * Navigate | Call Hierarchy (⌃⌥H / Ctrl+Alt+H) for FL words: the callers of the word under the caret (or of the
 * definition the caret is in), and its callees.
 */
public final class FLCallHierarchyProvider implements HierarchyProvider {
    @Nullable
    @Override
    public PsiElement getTarget(@NotNull DataContext dataContext) {
        PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
        if (element instanceof FLDefinition || element instanceof FLPrimitive) return element;
        Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
        PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
        if (editor == null || !(file instanceof FLFile)) return null;
        return targetAt(file, editor.getCaretModel().getOffset());
    }

    /** The word used at the offset, else the definition that contains it. */
    @Nullable
    public static PsiElement targetAt(@NotNull PsiFile file, int offset) {
        PsiElement leaf = file.findElementAt(offset);
        if (leaf == null && offset > 0) leaf = file.findElementAt(offset - 1);
        if (leaf == null) return null;
        FLWordRef ref = PsiTreeUtil.getParentOfType(leaf, FLWordRef.class, false);
        if (ref != null) {
            PsiElement callee = FLCallTrees.callable(ref.getReference().resolve());
            if (callee != null) return callee;
        }
        return PsiTreeUtil.getParentOfType(leaf, FLDefinition.class, FLPrimitive.class);
    }

    @NotNull
    @Override
    public HierarchyBrowser createHierarchyBrowser(@NotNull PsiElement target) {
        return new Browser(target.getProject(), target);
    }

    @Override
    public void browserActivated(@NotNull HierarchyBrowser browser) {
        ((Browser) browser).changeView(CallHierarchyBrowserBase.getCallerType());
    }

    static final class Browser extends CallHierarchyBrowserBase {
        Browser(@NotNull Project project, @NotNull PsiElement element) {
            super(project, element);
        }

        @Override
        protected void createTrees(@NotNull Map<? super String, ? super JTree> trees) {
            trees.put(getCallerType(), createTree(false));
            trees.put(getCalleeType(), createTree(false));
        }

        @Nullable
        @Override
        protected PsiElement getElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
            return descriptor.getPsiElement();
        }

        /** Double-click goes to the first call site the node stands for, else to the word. */
        @Nullable
        @Override
        protected PsiElement getOpenFileElementFromDescriptor(@NotNull HierarchyNodeDescriptor descriptor) {
            if (descriptor instanceof FLCallNode node && !node.usages().isEmpty()) return node.usages().get(0);
            return descriptor.getPsiElement();
        }

        @Override
        protected boolean isApplicableElement(@NotNull PsiElement element) {
            return element instanceof FLDefinition || element instanceof FLPrimitive;
        }

        @Nullable
        @Override
        protected HierarchyTreeStructure createHierarchyTreeStructure(@NotNull String type, @NotNull PsiElement element) {
            if (getCallerType().equals(type)) return new FLCallTrees.Callers(myProject, element, getCurrentScopeType());
            if (getCalleeType().equals(type)) return new FLCallTrees.Callees(myProject, element);
            return null;
        }

        @NotNull
        @Override
        protected Comparator<NodeDescriptor<?>> getComparator() {
            if (HierarchyBrowserManager.getInstance(myProject).getState().SORT_ALPHABETICALLY) {
                return (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(a), String.valueOf(b));
            }
            return FLCallHierarchyProvider::byFirstUse;
        }
    }

    /**
     * Source order by call site: callees in the order the body first uses them, callers by the file and position of
     * their first call (a word's own position means nothing across files, built-ins included).
     */
    static int byFirstUse(NodeDescriptor<?> a, NodeDescriptor<?> b) {
        PsiElement ua = firstUse(a);
        PsiElement ub = firstUse(b);
        if (ua == null || ub == null) return ua == null ? (ub == null ? 0 : -1) : 1;
        String fa = ua.getContainingFile() == null ? "" : ua.getContainingFile().getName();
        String fb = ub.getContainingFile() == null ? "" : ub.getContainingFile().getName();
        int byFile = fa.compareTo(fb);
        return byFile != 0 ? byFile : Integer.compare(ua.getTextRange().getStartOffset(), ub.getTextRange().getStartOffset());
    }

    @Nullable
    private static PsiElement firstUse(NodeDescriptor<?> d) {
        return d instanceof FLCallNode n && !n.usages().isEmpty() ? n.usages().get(0) : null;
    }
}
