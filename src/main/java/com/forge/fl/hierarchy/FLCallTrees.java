// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.hierarchy;

import com.forge.fl.psi.FLAlias;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLMetaBlock;
import com.forge.fl.psi.FLPrimitive;
import com.forge.fl.psi.FLWordRef;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The two trees of an FL call hierarchy: who calls a word, and what a word calls. */
public final class FLCallTrees {
    private FLCallTrees() {
    }

    /**
     * Callers: the words (or files, for top-level code) whose bodies use the word or one of its aliases. References in
     * {@code META:} blocks ({@code see-also}, {@code inverse}, the target) and in headers are not calls.
     */
    public static final class Callers extends HierarchyTreeStructure {
        private final String scopeType;

        public Callers(@NotNull Project project, @NotNull PsiElement word, @NotNull String scopeType) {
            super(project, new FLCallNode(project, null, word, true, false));
            this.scopeType = scopeType;
        }

        @Override
        protected Object @NotNull [] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
            PsiElement word = descriptor.getPsiElement();
            if (word == null || ((FLCallNode) descriptor).isRecursive() || word instanceof PsiFile) return new Object[0];
            SearchScope scope = getSearchScope(scopeType, word);
            Map<PsiElement, FLCallNode> byCaller = new LinkedHashMap<>();
            for (PsiElement target : withAliases(word)) {
                for (PsiReference r : ReferencesSearch.search(target, scope).findAll()) {
                    if (!(r.getElement() instanceof FLWordRef ref) || !isCall(ref)) continue;
                    FLDefinition def = PsiTreeUtil.getParentOfType(ref, FLDefinition.class);
                    PsiElement caller = def != null ? def : ref.getContainingFile();
                    FLCallNode node = byCaller.computeIfAbsent(caller, c ->
                            new FLCallNode(myProject, descriptor, c, false, isAncestor(descriptor, c)));
                    node.addUsage(ref);
                }
            }
            return sorted(byCaller.values());
        }
    }

    /** Callees: the words a body uses, in the order of their first use; locals, constants and variables are not calls. */
    public static final class Callees extends HierarchyTreeStructure {
        public Callees(@NotNull Project project, @NotNull PsiElement word) {
            super(project, new FLCallNode(project, null, word, true, false));
        }

        @Override
        protected Object @NotNull [] buildChildren(@NotNull HierarchyNodeDescriptor descriptor) {
            PsiElement word = descriptor.getPsiElement();
            if (!(word instanceof FLDefinition def) || ((FLCallNode) descriptor).isRecursive()) return new Object[0];
            Map<PsiElement, FLCallNode> byCallee = new LinkedHashMap<>();
            for (FLWordRef ref : PsiTreeUtil.findChildrenOfType(def, FLWordRef.class)) {
                if (!isCall(ref)) continue;
                PsiElement callee = callable(ref.getReference().resolve());
                if (callee == null) continue;
                FLCallNode node = byCallee.computeIfAbsent(callee, c ->
                        new FLCallNode(myProject, descriptor, c, false, isAncestor(descriptor, c)));
                node.addUsage(ref);
            }
            // recurse: the word calls itself
            for (PsiElement leaf : PsiTreeUtil.collectElements(def, e -> e.getFirstChild() == null
                    && e.getNode().getElementType() == com.forge.fl.FLTokenTypes.RECURSE_KW)) {
                FLCallNode node = byCallee.computeIfAbsent(def, c -> new FLCallNode(myProject, descriptor, c, false, true));
                node.addUsage(leaf);
            }
            return sorted(byCallee.values());
        }
    }

    /** Children in call-site order, so that the structure does not depend on the order of a reference search. */
    private static Object[] sorted(java.util.Collection<FLCallNode> nodes) {
        List<FLCallNode> list = new ArrayList<>(nodes);
        list.sort(FLCallHierarchyProvider::byFirstUse);
        return list.toArray();
    }

    /** A word use that runs code: in a definition body or top-level code, not in a header or a META: block. */
    static boolean isCall(@NotNull FLWordRef ref) {
        if (PsiTreeUtil.getParentOfType(ref, FLMetaBlock.class) != null) return false;
        FLDefinition def = PsiTreeUtil.getParentOfType(ref, FLDefinition.class);
        return def == null || def.getHeader() == null || !PsiTreeUtil.isAncestor(def.getHeader(), ref, false);
    }

    /** What a use calls: a definition or a primitive (an alias as its word); null for locals, constants and variables. */
    @Nullable
    static PsiElement callable(@Nullable PsiElement target) {
        if (target instanceof FLDefinition || target instanceof FLPrimitive) return target;
        if (target instanceof FLAlias alias) {
            FLMetaBlock block = PsiTreeUtil.getParentOfType(alias, FLMetaBlock.class);
            FLWordRef of = block == null ? null : block.getTarget();
            return of == null ? null : callable(of.getReference().resolve());
        }
        return null;
    }

    /** The word and the aliases declared for it in META: blocks of the project. */
    static List<PsiElement> withAliases(@NotNull PsiElement word) {
        List<PsiElement> out = new ArrayList<>();
        out.add(word);
        if (!(word instanceof FLDefinition)) return out;
        for (PsiReference r : ReferencesSearch.search(word, GlobalSearchScope.projectScope(word.getProject())).findAll()) {
            if (r.getElement() instanceof FLWordRef ref && ref.getParent() instanceof FLMetaBlock block && block.getTarget() == ref) {
                out.addAll(PsiTreeUtil.findChildrenOfType(block, FLAlias.class));
            }
        }
        return out;
    }

    /** Whether {@code element} is already on the path from the root to {@code descriptor} (a call cycle). */
    static boolean isAncestor(@NotNull HierarchyNodeDescriptor descriptor, @NotNull PsiElement element) {
        for (var d = descriptor; d != null; d = (HierarchyNodeDescriptor) d.getParentDescriptor()) {
            if (element.equals(d.getPsiElement())) return true;
        }
        return false;
    }
}
