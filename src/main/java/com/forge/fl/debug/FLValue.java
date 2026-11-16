// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.debug;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.ImmediateFullValueEvaluator;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation;
import org.jetbrains.annotations.NotNull;

/** A local or a data stack entry: its tag and its repr (secrets redacted, cut at 1000 characters by the runner). */
final class FLValue extends XNamedValue {
    static final int SHOWN = 120;

    private final String type;
    private final String value;

    FLValue(@NotNull String name, @NotNull String type, @NotNull String value) {
        super(name);
        this.type = type;
        this.value = value;
    }

    String type() {
        return type;
    }

    String value() {
        return value;
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        String shown = value.length() > SHOWN ? value.substring(0, SHOWN - 3) + "..." : value;
        node.setPresentation(AllIcons.Debugger.Value, new XRegularValuePresentation(shown, type), false);
        if (shown.length() < value.length()) node.setFullValueEvaluator(new ImmediateFullValueEvaluator(value));
    }
}
