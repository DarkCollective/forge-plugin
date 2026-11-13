// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The declarations of one file, grouped by vocabulary (docs/LANGUAGE.md 6.1, 6.2 restricted to a single file).
 * The current vocabulary at a position is the last {@code IN:} (or {@code VOCAB}) before it, default {@code user}.
 */
public final class FLScope {
    public static final String DEFAULT_VOCAB = "user";

    /** One name a declaration makes visible. A VARIABLE declares both {@code n} and {@code n!}. */
    public record Decl(String vocab, String name, FLNamedElement element, int start, int end) {
    }

    private static final Key<CachedValue<FLScope>> KEY = Key.create("com.forge.fl.scope");

    private final Map<String, List<Decl>> byKey = new HashMap<>();
    private final List<Decl> all = new ArrayList<>();
    /** Shared choice types (FL v0.2, 5.12): a namespace of their own, keyed like {@link #byKey}. */
    private final Map<String, List<Decl>> typesByKey = new HashMap<>();
    private final List<Decl> types = new ArrayList<>();
    private final List<int[]> inOffsets = new ArrayList<>(); // {end offset of the IN: form}
    private final List<String> inVocabs = new ArrayList<>();
    /** Per USING: form: {start offset} and its names, in file order. */
    private final List<Integer> usingStarts = new ArrayList<>();
    private final List<List<String>> usingNames = new ArrayList<>();

    @NotNull
    public static FLScope of(@NotNull PsiFile file) {
        return CachedValuesManager.getManager(file.getProject()).getCachedValue(file, KEY,
                () -> CachedValueProvider.Result.create(build(file), PsiModificationTracker.MODIFICATION_COUNT), false);
    }

    private static FLScope build(PsiFile file) {
        FLScope s = new FLScope();
        for (PsiElement child = file.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof FLInForm in) {
                String v = in.getVocabName();
                if (v != null) {
                    s.inOffsets.add(new int[]{in.getTextRange().getEndOffset()});
                    s.inVocabs.add(v);
                }
            } else if (child.getNode().getElementType() == FLElementTypes.USING_FORM) {
                List<String> names = new ArrayList<>();
                for (PsiElement leaf = child.getFirstChild(); leaf != null; leaf = leaf.getNextSibling()) {
                    if (leaf.getNode().getElementType() == FLTokenTypes.WORD_NAME) names.add(leaf.getText());
                }
                s.usingStarts.add(child.getTextRange().getStartOffset());
                s.usingNames.add(names);
            } else if (child instanceof FLDefinition || child instanceof FLConstant || child instanceof FLPrimitive) {
                s.declare((FLNamedElement) child, child);
            } else if (child instanceof FLVariable v) {
                s.declare(v, child);
                String name = v.getName();
                if (name != null) s.add(s.vocabAt(child.getTextRange().getStartOffset()), name + "!", v, child);
            } else if (child instanceof FLChoiceType type) {
                String name = type.getName();
                if (name != null) {
                    Decl d = new Decl(s.vocabAt(child.getTextRange().getStartOffset()), name, type,
                            child.getTextRange().getStartOffset(), child.getTextRange().getEndOffset());
                    s.types.add(d);
                    s.typesByKey.computeIfAbsent(d.vocab() + '\0' + name, k -> new ArrayList<>()).add(d);
                }
            } else if (child instanceof FLMetaBlock block) {
                for (FLAlias alias : PsiTreeUtil.findChildrenOfType(block, FLAlias.class)) {
                    s.declare(alias, block);
                }
            }
        }
        return s;
    }

    private void declare(FLNamedElement e, PsiElement form) {
        String name = e.getName();
        if (name != null) add(vocabAt(form.getTextRange().getStartOffset()), name, e, form);
    }

    private void add(String vocab, String name, FLNamedElement e, PsiElement form) {
        Decl d = new Decl(vocab, name, e, form.getTextRange().getStartOffset(), form.getTextRange().getEndOffset());
        all.add(d);
        byKey.computeIfAbsent(vocab + '\0' + name, k -> new ArrayList<>()).add(d);
    }

    /** The current vocabulary at {@code offset}. */
    @NotNull
    public String vocabAt(int offset) {
        String v = DEFAULT_VOCAB;
        for (int i = 0; i < inOffsets.size(); i++) {
            if (inOffsets.get(i)[0] <= offset) v = inVocabs.get(i);
            else break;
        }
        return v;
    }

    /**
     * The import list at {@code offset} (docs/LANGUAGE.md 5.3): the names of every USING: form that starts before
     * it, in order, without duplicates. {@code core} is dropped: it is always implicitly last.
     */
    @NotNull
    public List<String> usingAt(int offset) {
        Set<String> seen = new LinkedHashSet<>();
        for (int k = 0; k < usingStarts.size() && usingStarts.get(k) < offset; k++) {
            for (String name : usingNames.get(k)) {
                if (!name.equals("core")) seen.add(name);
            }
        }
        return new ArrayList<>(seen);
    }

    /** Every vocabulary this file switches to or declares into (not the implicit {@code user} unless used). */
    @NotNull
    public Set<String> vocabularies() {
        Set<String> out = new LinkedHashSet<>(inVocabs);
        for (Decl d : all) out.add(d.vocab());
        for (Decl d : types) out.add(d.vocab());
        return out;
    }

    /** Declarations of {@code name} in {@code vocab}, in file order. */
    @NotNull
    public List<Decl> find(String vocab, String name) {
        return byKey.getOrDefault(vocab + '\0' + name, Collections.emptyList());
    }

    @NotNull
    public List<Decl> all() {
        return all;
    }

    /** Choice types named {@code name} in {@code vocab}, in file order. */
    @NotNull
    public List<Decl> findType(String vocab, String name) {
        return typesByKey.getOrDefault(vocab + '\0' + name, Collections.emptyList());
    }

    /** Every choice type declared in this file. */
    @NotNull
    public List<Decl> types() {
        return types;
    }

    /**
     * No forward references: the last declaration that ends before {@code formStart} (the start of the top-level
     * form that holds the use); inside its own body a name means the earlier definition. Null when there is none.
     */
    @Nullable
    public static FLNamedElement pickEarlier(List<Decl> candidates, int formStart) {
        FLNamedElement best = null;
        for (Decl d : candidates) {
            if (d.end() <= formStart) best = d.element();
        }
        return best;
    }

    /** The phase 1 rule: {@link #pickEarlier}, else the first declaration (a forward reference), else null. */
    @Nullable
    public static FLNamedElement pick(List<Decl> candidates, int formStart) {
        FLNamedElement best = pickEarlier(candidates, formStart);
        if (best != null) return best;
        return candidates.isEmpty() ? null : candidates.get(0).element();
    }
}
