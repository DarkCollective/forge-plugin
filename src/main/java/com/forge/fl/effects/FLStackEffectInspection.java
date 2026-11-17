// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.effects;

import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.psi.FLDefinition;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * Stack effect problems that {@link FLStackAnalysis} can prove: a body that ends (or exits) with a depth other than
 * its header declares, a call or {@code :>} with too few values, branches that leave different depths, and a loop
 * quotation that breaks the depth rule of docs/LANGUAGE.md section 8. Each would be {@code E_EFFECT} or
 * {@code E_STACK_UNDERFLOW} when that path runs.
 */
public final class FLStackEffectInspection extends LocalInspectionTool {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof FLDefinition def) || FLBuiltins.isBuiltin(def)) return;
                for (FLStackAnalysis.Problem p : FLStackAnalysis.of(def).problems()) {
                    holder.registerProblem(p.at(), p.message(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
            }
        };
    }
}
