package com.forge.fl.psi;

import com.forge.fl.lexer.FLSyntax;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * A new name must match the {@code word-name} grammar of docs/LANGUAGE.md 2.11 (no ':') and not be a reserved
 * word. The stricter parameter rule is in {@link FLParameterRenameValidator}.
 */
public class FLNamesValidator implements NamesValidator {
    @Override
    public boolean isKeyword(@NotNull String name, Project project) {
        return FLSyntax.isReserved(name);
    }

    @Override
    public boolean isIdentifier(@NotNull String name, Project project) {
        return FLSyntax.isDefinableName(name);
    }
}
