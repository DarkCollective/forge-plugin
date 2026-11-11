package com.forge.fl.psi;

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
import java.util.List;
import java.util.Map;

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
    private final List<int[]> inOffsets = new ArrayList<>(); // {end offset of the IN: form}
    private final List<String> inVocabs = new ArrayList<>();

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
            } else if (child instanceof FLDefinition || child instanceof FLConstant || child instanceof FLPrimitive) {
                s.declare((FLNamedElement) child, child);
            } else if (child instanceof FLVariable v) {
                s.declare(v, child);
                String name = v.getName();
                if (name != null) s.add(s.vocabAt(child.getTextRange().getStartOffset()), name + "!", v, child);
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

    /** Declarations of {@code name} in {@code vocab}, in file order. */
    @NotNull
    public List<Decl> find(String vocab, String name) {
        return byKey.getOrDefault(vocab + '\0' + name, Collections.emptyList());
    }

    @NotNull
    public List<Decl> all() {
        return all;
    }

    /**
     * No forward references: prefer the last declaration that ends before {@code formStart} (the start of the
     * top-level form that holds the use); inside its own body a name means the earlier definition. Otherwise
     * fall back to the first declaration of the name in that vocabulary.
     */
    @Nullable
    public static FLNamedElement pick(List<Decl> candidates, int formStart) {
        FLNamedElement best = null;
        for (Decl d : candidates) {
            if (d.end() <= formStart) best = d.element();
        }
        if (best != null) return best;
        return candidates.isEmpty() ? null : candidates.get(0).element();
    }
}
