// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.effects;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.psi.FLHeader;
import com.forge.fl.psi.FLParameter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The stack effect of a header (docs/LANGUAGE.md 5.4): its inputs deepest first, whether a leading {@code ..} makes
 * the input variadic, and its outputs, or {@code -- ..} (unchecked). A named input (a parameter or a bare label) is
 * a local in the body; an anonymous input (a bare type name) stays on the stack (section 7).
 */
public record FLEffect(@NotNull List<Input> inputs, boolean variadicIn, @NotNull List<String> outputs, boolean uncheckedOut) {
    /** One input: its name (null for an anonymous input), its header text and its parameter (named inputs). */
    public record Input(@Nullable String name, @NotNull String text, @Nullable FLParameter parameter) {
    }

    /** {@code ( -- x )}: a constant, a variable, a local. */
    public static final FLEffect PUSH = new FLEffect(List.of(), false, List.of("any"), false);
    /** {@code ( x -- )}: a variable setter {@code n!}, a {@code :>} binding. */
    public static final FLEffect POP = new FLEffect(List.of(new Input(null, "any", null)), false, List.of(), false);

    /** The number of fixed inputs (after a leading {@code ..}). */
    public int arity() {
        return inputs.size();
    }

    /** The anonymous inputs, which are on the stack when the body starts. */
    public int anonymousInputs() {
        int n = 0;
        for (Input i : inputs) if (i.name() == null) n++;
        return n;
    }

    /**
     * The effect of a header, from its tokens (so that a {@code ..} inside a constraint is not taken for the variadic
     * marker); null when an item fits no category or there is no {@code --}, so that callers stay silent.
     */
    @Nullable
    public static FLEffect of(@Nullable FLHeader header) {
        if (header == null) return null;
        List<List<PsiElement>> items = new ArrayList<>();
        List<PsiElement> run = null;
        for (PsiElement c = header.getFirstChild(); c != null; c = c.getNextSibling()) {
            IElementType t = c.getNode().getElementType();
            if (c instanceof PsiWhiteSpace || c instanceof PsiComment || t == FLTokenTypes.HDR_LPAREN || t == FLTokenTypes.HDR_RPAREN) {
                run = null;
                continue;
            }
            if (run == null) {
                run = new ArrayList<>();
                items.add(run);
            }
            run.add(c);
        }
        List<Input> inputs = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        boolean arrow = false;
        boolean variadic = false;
        boolean unchecked = false;
        for (List<PsiElement> item : items) {
            PsiElement first = item.get(0);
            IElementType t = first.getNode().getElementType();
            if (item.size() == 1 && t == FLTokenTypes.HDR_ARROW) {
                if (arrow) return null;
                arrow = true;
                continue;
            }
            boolean dots = item.size() == 1 && t == FLTokenTypes.HDR_DOTS;
            if (!arrow) {
                if (dots) {
                    if (!inputs.isEmpty() || variadic) return null;
                    variadic = true;
                } else if (item.size() == 1 && first instanceof FLParameter p) {
                    inputs.add(new Input(p.getName(), p.getText(), p));
                } else if (item.size() == 1 && t == FLTokenTypes.HDR_TYPE) {
                    inputs.add(new Input(null, first.getText(), null));
                } else {
                    return null;
                }
            } else if (dots) {
                if (!outputs.isEmpty() || unchecked) return null;
                unchecked = true;
            } else {
                if (unchecked) return null;
                StringBuilder text = new StringBuilder();
                for (PsiElement e : item) text.append(e.getText());
                outputs.add(text.toString());
            }
        }
        return arrow ? new FLEffect(List.copyOf(inputs), variadic, List.copyOf(outputs), unchecked) : null;
    }

    /** {@code ( a:int b -- c )}: for display. */
    @NotNull
    public String text() {
        StringBuilder sb = new StringBuilder("(");
        if (variadicIn) sb.append(" ..");
        for (Input i : inputs) sb.append(' ').append(i.text());
        sb.append(" --");
        if (uncheckedOut) sb.append(" ..");
        for (String o : outputs) sb.append(' ').append(o);
        return sb.append(" )").toString();
    }
}
