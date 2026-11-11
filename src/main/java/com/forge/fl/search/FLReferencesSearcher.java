// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.search;

import com.forge.fl.index.FLIndexLookup;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.psi.FLParameter;
import com.forge.fl.psi.FLVariable;
import com.forge.fl.psi.FLWordRef;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.openapi.application.QueryExecutorBase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the uses of FL declarations. The platform finds uses through its word index, which holds identifier-like
 * words only: it cannot find {@code +}, {@code >hex} or {@code n!}. This searcher asks the FL index (keyed by the
 * bare word name, symbols included) for the files that use the name, walks only those files, and keeps every
 * {@link FLWordRef} whose reference resolves to the declaration. Duplicates with the platform's own search are
 * removed by the platform.
 */
public class FLReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
    public FLReferencesSearcher() {
        super(true);
    }

    @Override
    public void processQuery(@NotNull ReferencesSearch.SearchParameters params, @NotNull Processor<? super PsiReference> consumer) {
        PsiElement target = params.getElementToSearch();
        if (!(target instanceof FLNamedElement named) || target instanceof FLParameter) return;
        String name = named.getName();
        if (name == null) return;
        Set<String> names = new LinkedHashSet<>();
        names.add(name);
        if (target instanceof FLVariable) names.add(name + "!");

        SearchScope scope = params.getEffectiveSearchScope();
        List<PsiFile> files = new ArrayList<>();
        Set<VirtualFile> seen = new LinkedHashSet<>();
        PsiFile own = named.getContainingFile();
        VirtualFile ownVf = own.getOriginalFile().getVirtualFile();
        // The declaring file is searched from its PSI (it may hold unsaved changes the index has not seen).
        if (scopeContains(scope, own)) {
            files.add(own);
            if (ownVf != null) seen.add(ownVf);
        }
        if (scope instanceof GlobalSearchScope global) {
            for (String n : names) {
                for (FLIndexLookup.Hit hit : FLIndexLookup.usages(named.getProject(), n)) {
                    if (!global.contains(hit.file()) || !seen.add(hit.file())) continue;
                    PsiFile psi = PsiManager.getInstance(named.getProject()).findFile(hit.file());
                    if (psi != null) files.add(psi);
                }
            }
        } else if (scope instanceof LocalSearchScope local) {
            for (PsiElement e : local.getScope()) {
                PsiFile f = e.getContainingFile();
                if (f != null && f != own && seen.add(f.getOriginalFile().getVirtualFile() == null ? f.getViewProvider().getVirtualFile() : f.getOriginalFile().getVirtualFile())) {
                    files.add(f);
                }
            }
        }
        for (PsiFile file : files) {
            for (FLWordRef ref : PsiTreeUtil.findChildrenOfType(file, FLWordRef.class)) {
                if (!names.contains(ref.getBareName()) || !PsiSearchScopeUtil.isInScope(scope, ref)) continue;
                PsiReference reference = ref.getReference();
                if (reference.isReferenceTo(target) && !consumer.process(reference)) return;
            }
        }
    }

    private static boolean scopeContains(SearchScope scope, PsiFile file) {
        if (scope instanceof GlobalSearchScope global) {
            VirtualFile vf = file.getOriginalFile().getVirtualFile();
            return vf != null && global.contains(vf);
        }
        if (scope instanceof LocalSearchScope local) return local.isInScope(file.getOriginalFile().getVirtualFile());
        return false;
    }
}
