// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.psi.FLDefinition;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A run icon on the name of every word ({@code :} and {@code MEMO:}) of a project file: Run and Debug. */
public final class FLRunLineMarkerContributor extends RunLineMarkerContributor {
    @Nullable
    @Override
    public Info getInfo(@NotNull PsiElement element) {
        if (!(element.getParent() instanceof FLDefinition def) || def.getNameIdentifier() != element) return null;
        if (def.getName() == null || FLBuiltins.isBuiltin(def)) return null;
        String qname = FLRunInputs.qualifiedName(def);
        AnAction[] actions = {
                new FLRunWordAction(def, DefaultRunExecutor.getRunExecutorInstance()),
                new FLRunWordAction(def, DefaultDebugExecutor.getDebugExecutorInstance())};
        return new Info(AllIcons.RunConfigurations.TestState.Run, actions, e -> "Run or debug " + qname);
    }
}
