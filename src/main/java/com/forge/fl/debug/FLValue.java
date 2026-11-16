// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.debug;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.ImmediateFullValueEvaluator;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A local or a data stack entry: its tag and its repr (secrets redacted, cut at 1000 characters by the runner).
 * When the engine can change values, it has a modifier (Set Value): a local by name, a stack entry by index.
 */
final class FLValue extends XNamedValue {
    static final int SHOWN = 120;
    /** The runner cuts a repr at 1000 characters; a repr this long may be cut and is not offered for editing. */
    static final int EDITABLE_MAX = 990;

    private final String type;
    private final String value;
    /** The index in the runner's stack, deepest first, of a stack entry; -1 for a local. */
    private final int stackIndex;
    private volatile @Nullable XValueModifier modifier;

    FLValue(@NotNull String name, @NotNull String type, @NotNull String value) {
        this(name, type, value, -1);
    }

    FLValue(@NotNull String name, @NotNull String type, @NotNull String value, int stackIndex) {
        super(name);
        this.type = type;
        this.value = value;
        this.stackIndex = stackIndex;
    }

    String type() {
        return type;
    }

    String value() {
        return value;
    }

    boolean isStackEntry() {
        return stackIndex >= 0;
    }

    int stackIndex() {
        return stackIndex;
    }

    /** The text the Set Value editor starts with: the repr, unless it is redacted or may have been cut. */
    @Nullable
    String initialEditorText() {
        return value.contains("<redacted>") || value.length() >= EDITABLE_MAX ? null : value;
    }

    /** Makes the value settable through the evaluator of its frame. */
    void setModifier(@NotNull FLEvaluator evaluator) {
        modifier = new XValueModifier() {
            @Override
            public void setValue(@NotNull XExpression expression, @NotNull XModificationCallback callback) {
                evaluator.setValue(expression.getExpression(), FLValue.this, callback);
            }

            @Override
            public void calculateInitialValueEditorText(XInitialValueCallback callback) {
                callback.setValue(initialEditorText());
            }
        };
    }

    @Nullable
    @Override
    public XValueModifier getModifier() {
        return modifier;
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        String shown = value.length() > SHOWN ? value.substring(0, SHOWN - 3) + "..." : value;
        node.setPresentation(AllIcons.Debugger.Value, new XRegularValuePresentation(shown, type), false);
        if (shown.length() < value.length()) node.setFullValueEvaluator(new ImmediateFullValueEvaluator(value));
    }
}
