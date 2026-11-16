// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.debug;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One activation: a word, or a quotation of a word. Its children are its locals; the innermost frame also shows
 * the data stack, top first.
 */
final class FLStackFrame extends XStackFrame {
    /** A frame as the runner reports it; {@code unit}, {@code line} and {@code col} may be null (console code). */
    record Info(String word, boolean quotation, @Nullable String unit, @Nullable Integer line, @Nullable Integer col,
                List<FLValue> locals) {
    }

    private final Info info;
    private final @Nullable XSourcePosition position;
    private final @Nullable List<FLValue> stack;

    FLStackFrame(@NotNull Info info, @Nullable XSourcePosition position, @Nullable List<FLValue> stack) {
        this.info = info;
        this.position = position;
        this.stack = stack;
    }

    Info info() {
        return info;
    }

    @Nullable
    @Override
    public XSourcePosition getSourcePosition() {
        return position;
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
        component.append(info.word(), position == null ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        if (info.quotation()) component.append(" [ quotation ]", SimpleTextAttributes.GRAY_ATTRIBUTES);
        if (info.unit() != null && info.line() != null) {
            component.append("  " + info.unit() + ":" + info.line(), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        }
        component.setIcon(AllIcons.Debugger.Frame);
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        XValueChildrenList children = new XValueChildrenList();
        for (FLValue v : info.locals()) children.add(v);
        if (stack != null) children.addTopGroup(new StackGroup(stack));
        node.addChildren(children, true);
    }

    /** The data stack, top first ({@code [0]} is the top). */
    private static final class StackGroup extends XValueGroup {
        private final List<FLValue> values;

        StackGroup(List<FLValue> values) {
            super("Data stack (" + values.size() + ")");
            this.values = values;
        }

        @Override
        public boolean isAutoExpand() {
            return true;
        }

        @Override
        public void computeChildren(@NotNull XCompositeNode node) {
            XValueChildrenList children = new XValueChildrenList();
            for (FLValue v : values) children.add(v);
            node.addChildren(children, true);
        }
    }
}
