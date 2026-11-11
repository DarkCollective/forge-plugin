// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.diagnostics;

import com.forge.fl.FLFile;
import com.forge.fl.psi.FLWordRef;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Reports the compile errors of {@link FLDiagnostics} (everything with error severity). An annotator rather than
 * an inspection: these are errors the compiler would stop on, so there is no case for switching them off, and
 * they are cheap because resolution is cached. The whole file is checked once, when the platform visits the file
 * element. The weak warnings that console snippets and shadowing parameters legitimately produce are inspections
 * instead ({@link FLTopLevelCodeInspection}, {@link FLShadowInspection}), which can be switched off.
 */
public final class FLAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof FLFile file)) return;
        for (FLDiagnostics.Problem p : FLDiagnostics.compute(file)) {
            if (p.severity() == FLDiagnostics.Severity.WEAK) continue;
            HighlightSeverity severity = p.severity() == FLDiagnostics.Severity.ERROR
                    ? HighlightSeverity.ERROR : HighlightSeverity.WARNING;
            AnnotationBuilder b = holder.newAnnotation(severity, p.message()).range(p.range());
            if (p.unknownSymbol()) b = b.highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
            if (p.element() instanceof FLWordRef ref) {
                for (String q : p.qualifyAs()) b = b.withFix(new FLQualifyFix(ref, q));
            }
            b.create();
        }
    }
}
