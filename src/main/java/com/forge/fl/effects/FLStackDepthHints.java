// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.effects;

import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.psi.FLDefinition;
import com.intellij.codeInsight.hints.declarative.EndOfLinePosition;
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector;
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider;
import com.intellij.codeInsight.hints.declarative.InlayTreeSink;
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The stack depth at the end of each line of a body, as an end-of-line hint ({@code → 2}), where the stack effect
 * analysis knows it. A line gets a hint only when its last item is a body item (or an item of a quotation run inline
 * by {@code if}, {@code when}, {@code unless}, {@code call} or {@code try}); only a {@code ;} or a comment may follow.
 */
public final class FLStackDepthHints implements InlayHintsProvider {
    public static final String PROVIDER_ID = "fl.stack.depth";
    private static final Pattern REST_OF_LINE = Pattern.compile("\\s*;?\\s*(\\\\.*|\\(.*\\))?\\s*");

    @Nullable
    @Override
    public InlayHintsCollector createCollector(@NotNull PsiFile file, @NotNull Editor editor) {
        Document doc = editor.getDocument();
        return new SharedBypassCollector() {
            @Override
            public void collectFromElement(@NotNull PsiElement element, @NotNull InlayTreeSink sink) {
                if (!(element instanceof FLDefinition def)) return;
                for (Map.Entry<Integer, Integer> e : lines(def, doc).entrySet()) {
                    int depth = e.getValue();
                    sink.addPresentation(new EndOfLinePosition(e.getKey()), List.of(),
                            depth == 1 ? "1 value on the data stack" : depth + " values on the data stack", true,
                            b -> {
                                b.text("→ " + depth, null);
                                return Unit.INSTANCE;
                            });
                }
            }
        };
    }

    /** Line number (0-based) to the depth after that line, for the lines that get a hint. */
    @NotNull
    public static Map<Integer, Integer> lines(@NotNull FLDefinition def, @Nullable Document doc) {
        Map<Integer, Integer> out = new TreeMap<>();
        if (doc == null || FLBuiltins.isBuiltin(def)) return out;
        Map<Integer, PsiElement> last = new TreeMap<>();
        Map<PsiElement, Integer> depths = FLStackAnalysis.of(def).depths();
        for (PsiElement item : depths.keySet()) {
            int end = item.getTextRange().getEndOffset();
            int line = doc.getLineNumber(end);
            PsiElement before = last.get(line);
            if (before == null || before.getTextRange().getEndOffset() < end) last.put(line, item);
        }
        for (Map.Entry<Integer, PsiElement> e : last.entrySet()) {
            int line = e.getKey();
            int end = e.getValue().getTextRange().getEndOffset();
            String rest = doc.getCharsSequence().subSequence(end, doc.getLineEndOffset(line)).toString();
            if (REST_OF_LINE.matcher(rest).matches()) out.put(line, depths.get(e.getValue()));
        }
        return out;
    }
}
