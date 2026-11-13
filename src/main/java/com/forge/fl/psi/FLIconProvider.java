// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.intellij.icons.AllIcons;

import javax.swing.Icon;

final class FLIconProvider {
    private FLIconProvider() {
    }

    static Icon iconFor(FLNamedElement e) {
        if (e instanceof FLConstant) return AllIcons.Nodes.Constant;
        if (e instanceof FLVariable) return AllIcons.Nodes.Variable;
        if (e instanceof FLParameter || e instanceof FLLocal) return AllIcons.Nodes.Parameter;
        if (e instanceof FLChoiceType) return AllIcons.Nodes.Enum;
        if (e instanceof FLPrimitive) return AllIcons.Nodes.AbstractMethod;
        return AllIcons.Nodes.Function;
    }
}
