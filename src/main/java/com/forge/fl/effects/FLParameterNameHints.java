// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.effects;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.psi.FLDefinition;
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector;
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider;
import com.intellij.codeInsight.hints.declarative.InlayTreeSink;
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition;
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Parameter names before literal arguments: in {@code "hello" "url" false base32:encode} the hints read
 * {@code alphabet:"url" padding:false}. The words to the left of a call are its inputs, so which literal feeds which
 * input comes from the stack effect analysis; an anonymous input has no name and gets no hint. A declarative inlay
 * provider ({@code fl.parameter.names}), like the stack depth hints.
 */
public final class FLParameterNameHints implements InlayHintsProvider {
    public static final String PROVIDER_ID = "fl.parameter.names";

    @Nullable
    @Override
    public InlayHintsCollector createCollector(@NotNull PsiFile file, @NotNull Editor editor) {
        return new SharedBypassCollector() {
            @Override
            public void collectFromElement(@NotNull PsiElement element, @NotNull InlayTreeSink sink) {
                String name = parameterName(element);
                if (name == null) return;
                sink.addPresentation(new InlineInlayPosition(element.getTextRange().getStartOffset(), false, 0), List.of(),
                        null, true, b -> {
                            b.text(name + ":", null);
                            return Unit.INSTANCE;
                        });
            }
        };
    }

    /** The name of the input a literal or a literal quotation feeds, or null. */
    @Nullable
    public static String parameterName(@NotNull PsiElement element) {
        boolean literal = FLTokenTypes.LITERALS.contains(element.getNode().getElementType())
                || FLStackAnalysis.isQuotation(element);
        if (!literal) return null;
        FLDefinition def = PsiTreeUtil.getParentOfType(element, FLDefinition.class);
        if (def == null) return null;
        FLStackAnalysis.Use use = FLStackAnalysis.of(def).useOf(element);
        if (use == null) return null;
        List<FLEffect.Input> inputs = use.callee().effect().inputs();
        return use.index() < inputs.size() ? inputs.get(use.index()).name() : null;
    }
}
