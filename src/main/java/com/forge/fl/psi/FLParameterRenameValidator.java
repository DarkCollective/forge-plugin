// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.lexer.FLSyntax;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidatorEx;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A parameter name must match {@code pname} (5.4) and must not be a type name. */
public class FLParameterRenameValidator implements RenameInputValidatorEx {
    @NotNull
    @Override
    public ElementPattern<? extends PsiElement> getPattern() {
        return PlatformPatterns.psiElement(FLParameter.class);
    }

    @Override
    public boolean isInputValid(@NotNull String newName, @NotNull PsiElement element, @NotNull ProcessingContext context) {
        return isValidParameterName(newName);
    }

    @Nullable
    @Override
    public String getErrorMessage(@NotNull String newName, @NotNull Project project) {
        return isValidParameterName(newName) ? null
                : "A parameter name is lower-case letters, digits and '-', starting with a letter, and is not a type name";
    }

    public static boolean isValidParameterName(String name) {
        return FLSyntax.isPname(name) && !FLSyntax.isTypeName(name);
    }
}
