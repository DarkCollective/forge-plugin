// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.effects;

import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.index.FLIndexEntry;
import com.forge.fl.index.FLIndexLookup;
import com.forge.fl.psi.FLAlias;
import com.forge.fl.psi.FLConstant;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLLocal;
import com.forge.fl.psi.FLMetaBlock;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.psi.FLParameter;
import com.forge.fl.psi.FLPrimitive;
import com.forge.fl.psi.FLVariable;
import com.forge.fl.psi.FLWordRef;
import com.forge.fl.run.FLRunInputs;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a word use in a body does to the stack: the effect of the word it resolves to, and its qualified name when
 * it is a definition (so that the {@code core} combinators can be told apart). Null when the use does not resolve to
 * one declaration with a known effect: an analysis then stops rather than guess.
 */
public record FLCallee(@NotNull FLEffect effect, @Nullable String qname, @Nullable FLDefinition definition) {
    /** The callee of a word use, or null. */
    @Nullable
    public static FLCallee of(@NotNull FLWordRef ref) {
        PsiElement target = ref.getReference().resolve();
        return ofTarget(target, ref.getText().endsWith("!"), 0);
    }

    @Nullable
    private static FLCallee ofTarget(@Nullable PsiElement target, boolean setter, int depth) {
        if (target == null || depth > 4) return null;
        if (target instanceof FLParameter || target instanceof FLLocal || target instanceof FLConstant) {
            return new FLCallee(FLEffect.PUSH, null, null);
        }
        if (target instanceof FLVariable) return new FLCallee(setter ? FLEffect.POP : FLEffect.PUSH, null, null);
        if (target instanceof FLDefinition def) return ofDefinition(def);
        if (target instanceof FLAlias alias) {
            FLMetaBlock block = PsiTreeUtil.getParentOfType(alias, FLMetaBlock.class);
            FLWordRef of = block == null ? null : block.getTarget();
            return of == null ? null : ofTarget(of.getReference().resolve(), false, depth + 1);
        }
        if (target instanceof FLPrimitive prim) return ofPrimitive(prim);
        return null;
    }

    @Nullable
    public static FLCallee ofDefinition(@NotNull FLDefinition def) {
        FLEffect e = FLEffect.of(def.getHeader());
        return e == null || def.getName() == null ? null : new FLCallee(e, FLRunInputs.qualifiedName(def), def);
    }

    /** A {@code PRIMITIVE name} has no header in its source: its effect is the built-in word's. */
    @Nullable
    private static FLCallee ofPrimitive(FLPrimitive prim) {
        String name = prim.getName();
        if (name == null) return null;
        String vocab = com.forge.fl.psi.FLScope.of(prim.getContainingFile()).vocabAt(prim.getTextRange().getStartOffset());
        for (FLIndexLookup.Hit hit : FLIndexLookup.declarations(prim.getProject(), vocab, name)) {
            if (!FLBuiltins.isBuiltinFile(hit.file())) continue;
            for (FLIndexEntry e : hit.entries()) {
                FLNamedElement el = FLIndexLookup.element(prim.getProject(), hit.file(), e);
                if (el instanceof FLDefinition def) return ofDefinition(def);
            }
        }
        return null;
    }

    /** Whether this is the {@code core} word {@code name} (a built-in, not a word of the same name elsewhere). */
    public boolean isCore(@NotNull String name) {
        return ("core:" + name).equals(qname) && definition != null && FLBuiltins.isBuiltin(definition);
    }
}
