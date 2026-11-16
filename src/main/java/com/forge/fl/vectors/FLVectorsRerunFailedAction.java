// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.vectors;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.ComponentContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rerun Failed Tests: the failed entries, and whole files whose loading or coverage test failed. */
final class FLVectorsRerunFailedAction extends AbstractRerunFailedTestsAction {
    FLVectorsRerunFailedAction(@NotNull ComponentContainer componentContainer, @NotNull TestConsoleProperties properties) {
        super(componentContainer);
        init(properties);
    }

    @Nullable
    @Override
    protected MyRunProfile getRunProfile(@NotNull ExecutionEnvironment environment) {
        FLVectorsRunConfiguration base = (FLVectorsRunConfiguration) myConsoleProperties.getConfiguration();
        List<String> urls = new ArrayList<>();
        for (AbstractTestProxy t : getFailedTests(base.getProject())) {
            String url = t.getLocationUrl();
            if (url != null) urls.add(url);
        }
        FLVectorsRunConfiguration copy = base.clone();
        apply(copy, urls);
        return new MyRunProfile(copy) {
            @Override
            public Module @NotNull [] getModules() {
                return Module.EMPTY_ARRAY;
            }

            @Nullable
            @Override
            public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
                return copy.getState(executor, env);
            }
        };
    }

    /**
     * Narrows {@code c} to the given failed test locations: {@code flvector://f#i} reruns entry i of f,
     * {@code flvectors://f} (a load or coverage failure) all of f. The op filter is dropped.
     */
    static void apply(@NotNull FLVectorsRunConfiguration c, @NotNull List<String> locationUrls) {
        Set<String> whole = new LinkedHashSet<>();
        Map<String, List<Integer>> entries = new LinkedHashMap<>();
        for (String url : locationUrls) {
            if (url.startsWith(FLVectorLocator.FILE + "://")) {
                whole.add(url.substring(FLVectorLocator.FILE.length() + 3));
            } else if (url.startsWith(FLVectorLocator.ENTRY + "://")) {
                String path = url.substring(FLVectorLocator.ENTRY.length() + 3);
                int hash = path.lastIndexOf('#');
                if (hash <= 0) continue;
                try {
                    entries.computeIfAbsent(path.substring(0, hash), k -> new ArrayList<>())
                            .add(Integer.parseInt(path.substring(hash + 1)));
                } catch (NumberFormatException ignored) {
                    // not an entry location
                }
            }
        }
        whole.forEach(entries::remove);
        Set<String> files = new LinkedHashSet<>(whole);
        files.addAll(entries.keySet());
        c.setFiles(new ArrayList<>(files));
        c.setEntries(entries);
        c.setOp("");
    }
}
