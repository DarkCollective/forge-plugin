// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.diagnostics;

import com.forge.fl.intentions.FLMetaBlockMover;
import com.forge.fl.psi.FLMetaBlock;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public final class FLMetaPlacementInspection extends LocalInspectionTool implements CleanupLocalInspectionTool {
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull com.intellij.psi.PsiElement element) {
                if (!(element instanceof FLMetaBlock block)) return;
                var definition = FLMetaBlockMover.targetDefinition(block);
                if (definition == null || FLMetaBlockMover.isImmediatelyAfter(block, definition)) return;
                holder.registerProblem(block, "META block should follow its definition",
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new MoveMetaBlockFix());
            }
        };
    }

    private static final class MoveMetaBlockFix implements LocalQuickFix {
        @NotNull
        @Override
        public String getName() {
            return "Move META block next to definition (Code Cleanup)";
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return "FL META block";
        }

        @Override
        public void applyFix(@NotNull com.intellij.openapi.project.Project project,
                             @NotNull ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof FLMetaBlock block)) return;
            var definition = FLMetaBlockMover.targetDefinition(block);
            if (definition != null && !FLMetaBlockMover.isImmediatelyAfter(block, definition)) {
                FLMetaBlockMover.move(project, block.getContainingFile(), block, definition);
            }
        }
    }
}
