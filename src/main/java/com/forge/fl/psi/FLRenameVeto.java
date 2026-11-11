// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.builtins.FLBuiltins;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;

/** Built-in words have no source to edit: rename is refused for them. */
public class FLRenameVeto implements Condition<PsiElement> {
    @Override
    public boolean value(PsiElement element) {
        return element instanceof FLNamedElement && FLBuiltins.isBuiltin(element);
    }
}
