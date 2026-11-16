// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.debug;

import com.forge.fl.FLFile;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLLocal;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.psi.FLParameter;
import com.forge.fl.psi.FLScope;
import com.forge.fl.psi.FLWordRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Evaluates FL in one paused frame: Evaluate Expression, watches and hover. The expression is body code of the
 * frame's word: it sees the frame's locals by name, the paused data stack (a copy), the program's variables and the
 * vocabularies visible at the frame's position. The runner does the work ({@code evaluate} command).
 */
final class FLEvaluator extends XDebuggerEvaluator {
    /** The debug session's side: sends an {@code evaluate} command and completes with the {@code evaluated} event. */
    interface Backend {
        @NotNull
        CompletableFuture<JsonObject> evaluate(@NotNull JsonObject command);

        @NotNull
        Project project();
    }

    private final Backend backend;
    private final int frameIndex;
    private final FLStackFrame.Info info;
    private final @Nullable XSourcePosition position;

    FLEvaluator(@NotNull Backend backend, int frameIndex, @NotNull FLStackFrame.Info info, @Nullable XSourcePosition position) {
        this.backend = backend;
        this.frameIndex = frameIndex;
        this.info = info;
        this.position = position;
    }

    @Override
    public void evaluate(@NotNull String expression, @NotNull XEvaluationCallback callback,
                         @Nullable XSourcePosition expressionPosition) {
        String expr = expression.strip();
        if (expr.isEmpty()) {
            callback.errorOccurred("Nothing to evaluate");
            return;
        }
        // A local by name needs no round trip (hover, the commonest watch).
        FLValue local = local(expr);
        if (local != null) {
            callback.evaluated(local);
            return;
        }
        backend.evaluate(command(expr)).whenComplete((event, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                callback.errorOccurred(cause.getMessage() == null ? cause.toString() : cause.getMessage());
                return;
            }
            FLEvalResult result = FLEvalResult.of(event);
            if (result.error() != null) callback.errorOccurred(result.error());
            else callback.evaluated(result);
        });
    }

    @Nullable
    private FLValue local(String name) {
        for (FLValue v : info.locals()) {
            if (v.getName().equals(name)) return v;
        }
        return null;
    }

    /** The {@code evaluate} command, with the vocabulary and imports at the frame's position. */
    @NotNull
    JsonObject command(@NotNull String expr) {
        JsonObject c = new JsonObject();
        c.addProperty("cmd", "evaluate");
        c.addProperty("expr", expr);
        c.addProperty("frame", frameIndex);
        if (position != null) {
            ApplicationManager.getApplication().runReadAction(() -> {
                PsiFile file = PsiManager.getInstance(backend.project()).findFile(position.getFile());
                if (!(file instanceof FLFile)) return;
                FLScope scope = FLScope.of(file);
                int offset = position.getOffset();
                c.addProperty("vocab", scope.vocabAt(offset));
                JsonArray using = new JsonArray();
                for (String u : scope.usingAt(offset)) using.add(u);
                c.add("using", using);
            });
        }
        return c;
    }

    /**
     * Hover: only a use or a declaration of a local of this frame (a header parameter or a {@code :>} local of the
     * paused word), so that hovering a word never runs it.
     */
    @Nullable
    @Override
    public TextRange getExpressionRangeAtOffset(Project project, Document document, int offset, boolean sideEffectsAllowed) {
        return ApplicationManager.getApplication().runReadAction((Computable<TextRange>) () -> {
            PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
            if (!(file instanceof FLFile) || position == null || !position.getFile().equals(file.getVirtualFile())) return null;
            PsiElement leaf = file.findElementAt(offset);
            if (leaf == null || local(leaf.getText()) == null) return null;
            PsiElement parent = leaf.getParent();
            PsiElement target;
            TextRange range;
            if (parent instanceof FLWordRef ref && !ref.isQualified()) {
                target = ref.getReference().resolve();
                range = ref.getTextRange();
            } else if (parent instanceof FLNamedElement named && named.getNameIdentifier() == leaf) {
                target = parent;
                range = leaf.getTextRange();
            } else {
                return null;
            }
            if (!(target instanceof FLParameter || target instanceof FLLocal)) return null;
            FLDefinition def = PsiTreeUtil.getParentOfType(target, FLDefinition.class);
            return def != null && bareName(info.word()).equals(def.getName()) ? range : null;
        });
    }

    private static String bareName(String qname) {
        return qname.substring(qname.indexOf(':') + 1);
    }
}
