// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.effects;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLElementTypes;
import com.forge.fl.psi.FLWordRef;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parameter info (Ctrl+P / ⌘P). On a value that a call takes (a literal, a local, a quotation, the result of another
 * word), the header of that call with the input it feeds highlighted; on a word, its own header.
 */
public final class FLParameterInfoHandler implements ParameterInfoHandler<PsiElement, FLCallee> {
    /** The call shown for a caret position, and which input to highlight (-1 for none). */
    public record Target(@NotNull PsiElement call, int input, @NotNull FLCallee callee) {
    }

    @Nullable
    @Override
    public PsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
        Target t = targetAt(context.getFile(), context.getOffset());
        if (t == null) return null;
        context.setItemsToShow(new Object[]{t.callee()});
        return t.call();
    }

    @Override
    public void showParameterInfo(@NotNull PsiElement element, @NotNull CreateParameterInfoContext context) {
        context.showHint(element, element.getTextRange().getStartOffset(), this);
    }

    @Nullable
    @Override
    public PsiElement findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
        Target t = targetAt(context.getFile(), context.getOffset());
        return t == null ? null : t.call();
    }

    @Override
    public void updateParameterInfo(@NotNull PsiElement call, @NotNull UpdateParameterInfoContext context) {
        Target t = targetAt(context.getFile(), context.getOffset());
        context.setCurrentParameter(t != null && t.call() == call ? t.input() : -1);
    }

    @Override
    public void updateUI(FLCallee callee, @NotNull ParameterInfoUIContext context) {
        Presentation p = presentation(callee.effect(), context.getCurrentParameterIndex());
        context.setupUIComponentPresentation(p.text(), p.start(), p.end(), false, false, false,
                context.getDefaultParameterColor());
    }

    /** The header text and the range of input {@code current} in it (-1, -1 when none). */
    public record Presentation(String text, int start, int end) {
    }

    @NotNull
    public static Presentation presentation(@NotNull FLEffect effect, int current) {
        StringBuilder sb = new StringBuilder();
        int start = -1;
        int end = -1;
        if (effect.variadicIn()) sb.append(".. ");
        for (int i = 0; i < effect.inputs().size(); i++) {
            if (i == current) start = sb.length();
            sb.append(effect.inputs().get(i).text());
            if (i == current) end = sb.length();
            sb.append(' ');
        }
        sb.append("--");
        if (effect.uncheckedOut()) sb.append(" ..");
        for (String o : effect.outputs()) sb.append(' ').append(o);
        return new Presentation(sb.toString(), start, end);
    }

    /**
     * What to show at {@code offset} (or just before it): a word with its own header; otherwise the call that takes
     * the innermost body item around the caret, with that input highlighted.
     */
    @Nullable
    public static Target targetAt(@NotNull PsiFile file, int offset) {
        PsiElement leaf = file.findElementAt(offset);
        if ((leaf == null || leaf instanceof PsiWhiteSpace) && offset > 0) leaf = file.findElementAt(offset - 1);
        if (leaf == null) return null;
        FLDefinition def = PsiTreeUtil.getParentOfType(leaf, FLDefinition.class);
        if (def == null) return null;
        // On a word: its own header (a local or a constant has none, so it shows the call that takes its value).
        if (leaf.getParent() instanceof FLWordRef word) {
            FLCallee callee = FLCallee.of(word);
            if (callee != null && callee.definition() != null) return new Target(word, -1, callee);
        }
        if (leaf.getNode().getElementType() == FLTokenTypes.RECURSE_KW) {
            FLCallee self = FLCallee.ofDefinition(def);
            if (self != null) return new Target(leaf, -1, self);
        }
        FLStackAnalysis analysis = FLStackAnalysis.of(def);
        for (PsiElement e = leaf; e != null && e != def; e = e.getParent()) {
            FLStackAnalysis.Use use = analysis.useOf(e);
            if (use != null) return new Target(use.call(), use.index(), use.callee());
            if (e.getNode().getElementType() == FLElementTypes.LIST_LITERAL) break;
        }
        return null;
    }
}
