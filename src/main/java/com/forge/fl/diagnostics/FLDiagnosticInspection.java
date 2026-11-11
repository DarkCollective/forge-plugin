// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.diagnostics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Base of the weak-warning inspections: reports the {@link FLDiagnostics} findings with the given codes. */
abstract class FLDiagnosticInspection extends LocalInspectionTool {
    private final Set<String> codes;

    FLDiagnosticInspection(String... codes) {
        this.codes = Set.of(codes);
    }

    @Override
    public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager,
                                                    boolean isOnTheFly) {
        List<ProblemDescriptor> out = new ArrayList<>();
        for (FLDiagnostics.Problem p : FLDiagnostics.compute(file)) {
            if (p.severity() != FLDiagnostics.Severity.WEAK || !codes.contains(p.code())) continue;
            out.add(manager.createProblemDescriptor(file, p.range(), p.message(),
                    ProblemHighlightType.WEAK_WARNING, isOnTheFly));
        }
        return out.isEmpty() ? null : out.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }
}
