// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.navigation;

import com.forge.fl.index.FLIndex;
import com.forge.fl.index.FLIndexEntry;
import com.forge.fl.index.FLIndexLookup;
import com.forge.fl.psi.FLNamedElement;
import com.intellij.navigation.ChooseByNameContributorEx;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Go to Symbol over the FL index. The names offered are the qualified {@code vocab:name} (the declaration keys of
 * {@link FLIndex} without their prefix), so a query matches both the bare word name and the {@code vocab:name}
 * form, and each declaration is listed once. The name list comes from the index keys alone; PSI is loaded only for
 * the names the user's query selects. The search scope decides whether the built-in words (library files) are
 * included: they are when "include non-project items" is on.
 */
public final class FLGotoSymbolContributor implements ChooseByNameContributorEx {
    @Override
    public void processNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope,
                             IdFilter filter) {
        Set<String> seen = new HashSet<>();
        Project project = scope.getProject();
        FileBasedIndex.getInstance().processAllKeys(FLIndex.NAME, key -> {
            ProgressManager.checkCanceled();
            if (key.length() > 1 && key.charAt(0) == 'D' && seen.add(key) && inScope(project, key, scope)) {
                return processor.process(key.substring(1));
            }
            return true;
        }, scope, filter);
    }

    /**
     * The platform's key enumeration is only a hint (it may report keys of files outside the scope, and keys of
     * files that are gone), so the key must have a file in the scope. Index data only, no PSI.
     */
    private static boolean inScope(Project project, String key, GlobalSearchScope scope) {
        if (project == null) return true;
        for (FLIndexLookup.Hit hit : FLIndexLookup.hits(project, key)) {
            if (scope.contains(hit.file())) return true;
        }
        return false;
    }

    @Override
    public void processElementsWithName(@NotNull String name, @NotNull Processor<? super NavigationItem> processor,
                                        @NotNull FindSymbolParameters parameters) {
        GlobalSearchScope scope = parameters.getSearchScope();
        Set<String> done = new HashSet<>();
        for (FLIndexLookup.Hit hit : FLIndexLookup.hits(parameters.getProject(), "D" + name)) {
            if (!scope.contains(hit.file())) continue;
            for (FLIndexEntry entry : hit.entries()) {
                if (!entry.isDeclaration() || !done.add(hit.file().getPath() + "@" + entry.offset())) continue;
                FLNamedElement element = FLIndexLookup.element(parameters.getProject(), hit.file(), entry);
                if (element != null && !processor.process(new FLSymbolItem(element, name))) return;
            }
        }
    }
}
