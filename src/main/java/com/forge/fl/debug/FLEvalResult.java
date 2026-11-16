// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of evaluating an expression: the values it left on top of the paused stack, top first. One value and
 * nothing else shows as that value; otherwise the node summarises and its children are the values, then what the
 * expression printed.
 */
final class FLEvalResult extends XValue {
    private final List<FLValue> values;
    private final int consumed;
    private final String output;
    private final @Nullable String error;

    FLEvalResult(@NotNull List<FLValue> values, int consumed, @NotNull String output, @Nullable String error) {
        this.values = values;
        this.consumed = consumed;
        this.output = output;
        this.error = error;
    }

    /** From an {@code evaluated} event of the runner. */
    @NotNull
    static FLEvalResult of(@NotNull JsonObject event) {
        String output = str(event, "output");
        if (!event.has("ok") || !event.get("ok").getAsBoolean()) {
            String error = str(event, "error");
            if (error.isEmpty()) error = "Evaluation failed";
            if (!output.isEmpty()) error += "\nprinted: " + output.strip();
            return new FLEvalResult(List.of(), 0, output, error);
        }
        JsonArray stack = event.getAsJsonArray("stack");
        int kept = event.has("kept") ? event.get("kept").getAsInt() : 0;
        int consumed = event.has("consumed") ? event.get("consumed").getAsInt() : 0;
        List<FLValue> values = new ArrayList<>();
        if (stack != null) {
            for (int i = stack.size() - 1, k = 0; i >= kept; i--, k++) {
                JsonObject v = stack.get(i).getAsJsonObject();
                values.add(new FLValue("[" + k + "]", str(v, "type"), str(v, "value")));
            }
        }
        return new FLEvalResult(values, consumed, output, null);
    }

    @Nullable
    String error() {
        return error;
    }

    List<FLValue> values() {
        return values;
    }

    int consumed() {
        return consumed;
    }

    String output() {
        return output;
    }

    /** The summary of a result that is not a single value, e.g. "2 values, took 1 from the stack". */
    @NotNull
    String summary() {
        StringBuilder s = new StringBuilder(values.isEmpty() ? "no values" : values.size() == 1 ? "1 value" : values.size() + " values");
        if (consumed > 0) s.append(", took ").append(consumed).append(" from the stack");
        if (!output.isEmpty()) s.append(", printed output");
        return s.toString();
    }

    private boolean single() {
        return values.size() == 1 && consumed == 0 && output.isEmpty();
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        if (single()) {
            values.get(0).computePresentation(node, place);
            return;
        }
        node.setPresentation(AllIcons.Debugger.Value, new XRegularValuePresentation(summary(), null),
                !values.isEmpty() || !output.isEmpty());
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        XValueChildrenList children = new XValueChildrenList();
        if (!single()) {
            for (FLValue v : values) children.add(v);
            if (!output.isEmpty()) children.add(new FLValue("output", "printed", output.strip()));
        }
        node.addChildren(children, true);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
}
