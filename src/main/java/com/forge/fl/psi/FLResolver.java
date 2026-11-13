// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.index.FLIndexEntry;
import com.forge.fl.index.FLIndexLookup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Name resolution of docs/LANGUAGE.md section 6, across files and into the built-in words.
 *
 * <p>Unqualified name N at a position with current vocabulary C and imports U (6.2):
 * <ol>
 *   <li>a local: a {@code :>} binding visible at the use (7.1), else a parameter of the enclosing definition;</li>
 *   <li>C defines N (public or private): the last declaration in this file that ends before the enclosing form,
 *       else a declaration in another file that declares into C;</li>
 *   <li>N public and defined by exactly one vocabulary of U: that word; by several: ambiguous (all returned, in
 *       U order);</li>
 *   <li>{@code core} defines N;</li>
 *   <li>as a last resort, a declaration later in this file (a forward reference: invalid FL, but the declaration
 *       is the best guess); otherwise unresolved.</li>
 * </ol>
 * Qualified {@code v:n} looks only at vocabulary {@code v} (never at U); a private {@code _n} only when C is
 * {@code v} (6.3). The target of a {@code META:} block and unqualified {@code see-also} names are looked up in C
 * only (5.9). In the vocabularies of other files there is no order: the declaration that ends last in the first
 * file (by path) wins.
 */
public final class FLResolver {
    private FLResolver() {
    }

    private enum Role {NORMAL, META_TARGET, SEE_ALSO}

    private static final class Context {
        final Project project;
        final FLScope scope;
        final int formStart;
        final VirtualFile self;

        Context(FLWordRef ref) {
            project = ref.getProject();
            PsiFile file = ref.getContainingFile();
            scope = FLScope.of(file);
            PsiElement top = ref;
            while (top.getParent() != null && !(top.getParent() instanceof PsiFile)) top = top.getParent();
            formStart = top.getTextRange().getStartOffset();
            self = file.getOriginalFile().getVirtualFile();
        }

        Context(PsiFile file, int formStart) {
            project = file.getProject();
            scope = FLScope.of(file);
            this.formStart = formStart;
            self = file.getOriginalFile().getVirtualFile();
        }
    }

    /**
     * Whether an unqualified {@code name} used at {@code formStart} (the start of a top-level form of {@code file})
     * would find a word by steps 2 to 4 of docs/LANGUAGE.md 6.2: the current vocabulary, the imports (public names)
     * or {@code core}. Locals are not considered. An ambiguous name counts as visible. This is the test behind
     * W_SHADOW (section 7).
     */
    public static boolean isVisible(@NotNull PsiFile file, @NotNull String name, int formStart) {
        Context c = new Context(file, formStart);
        String current = c.scope.vocabAt(formStart);
        if (definedIn(c, current, name, false) != null) return true;
        if (!isPrivateName(name)) {
            for (String u : c.scope.usingAt(formStart)) {
                if (!u.equals(current) && definedIn(c, u, name, false) != null) return true;
            }
        }
        return definedIn(c, "core", name, false) != null;
    }

    public static boolean isPrivateName(String name) {
        return name.startsWith("_");
    }

    @NotNull
    public static List<PsiElement> resolve(@NotNull FLWordRef ref) {
        if (ref.getContainingFile() == null) return Collections.emptyList();
        Context c = new Context(ref);
        int pos = ref.getTextRange().getStartOffset();
        String current = c.scope.vocabAt(pos);
        String qualifier = ref.getVocab();
        String name = ref.getBareName();

        if (qualifier != null) {
            if (isPrivateName(name) && !qualifier.equals(current)) return Collections.emptyList();
            return single(definedIn(c, qualifier, name, true));
        }
        PsiElement parent = ref.getParent();
        Role role = parent instanceof FLMetaBlock ? Role.META_TARGET
                : parent instanceof FLMetaClause ? Role.SEE_ALSO : Role.NORMAL;
        if (role != Role.NORMAL) return single(definedIn(c, current, name, true));

        // 1. locals: mid-body :> bindings (innermost first), then the header
        FLNamedElement local = findLocal(ref, name);
        if (local != null) return Collections.singletonList(local);
        // 2. the current vocabulary
        PsiElement own = definedIn(c, current, name, false);
        if (own != null) return Collections.singletonList(own);
        // 3. imports (public names only)
        if (!isPrivateName(name)) {
            List<PsiElement> imported = new ArrayList<>();
            for (String u : c.scope.usingAt(pos)) {
                if (u.equals(current)) continue;
                PsiElement e = definedIn(c, u, name, false);
                if (e != null) imported.add(e);
            }
            if (!imported.isEmpty()) return imported;
        }
        // 4. core
        PsiElement core = definedIn(c, "core", name, false);
        if (core != null) return Collections.singletonList(core);
        // 5. nothing visible: a forward reference in this file is the best guess
        return single(FLScope.pick(c.scope.find(current, name), c.formStart));
    }

    /**
     * The local named {@code name} visible at {@code at} (docs/LANGUAGE.md 7, 7.1): a {@code :>} binding whose body
     * encloses {@code at} and that comes before it, innermost body first and, in one body, the nearest binding;
     * else a named input of the enclosing definition's header. Null when there is none.
     */
    @Nullable
    public static FLNamedElement findLocal(@NotNull PsiElement at, @NotNull String name) {
        int offset = at.getTextRange().getStartOffset();
        for (PsiElement owner = at.getParent(); owner != null && !(owner instanceof PsiFile); owner = owner.getParent()) {
            boolean body = owner instanceof FLDefinition || owner.getNode().getElementType() == FLElementTypes.QUOTATION;
            if (!body) continue;
            FLLocal best = null;
            for (PsiElement c = owner.getFirstChild(); c != null; c = c.getNextSibling()) {
                if (c.getTextRange().getStartOffset() >= offset) break;
                if (c instanceof FLLocal l && name.equals(l.getName()) && l.isVisibleAt(offset)) best = l;
            }
            if (best != null) return best;
            if (owner instanceof FLDefinition def) return def.findParameter(name);
        }
        return null;
    }

    /** Every local visible at {@code at}, nearest first, each name once (for completion). */
    @NotNull
    public static List<FLNamedElement> visibleLocals(@NotNull PsiElement at) {
        java.util.Map<String, FLNamedElement> out = new java.util.LinkedHashMap<>();
        int offset = at.getTextRange().getStartOffset();
        for (PsiElement owner = at.getParent(); owner != null && !(owner instanceof PsiFile); owner = owner.getParent()) {
            boolean body = owner instanceof FLDefinition || owner.getNode().getElementType() == FLElementTypes.QUOTATION;
            if (!body) continue;
            List<FLLocal> here = new ArrayList<>();
            for (PsiElement c = owner.getFirstChild(); c != null; c = c.getNextSibling()) {
                if (c.getTextRange().getStartOffset() >= offset) break;
                if (c instanceof FLLocal l && l.getName() != null && l.isVisibleAt(offset)) here.add(0, l);
            }
            for (FLLocal l : here) out.putIfAbsent(l.getName(), l);
            if (owner instanceof FLDefinition def) {
                for (FLParameter p : def.getParameters()) {
                    if (p.getName() != null) out.putIfAbsent(p.getName(), p);
                }
                break;
            }
        }
        return new ArrayList<>(out.values());
    }

    /**
     * A shared choice type (docs/LANGUAGE.md 6.8): qualified {@code v:name} through exactly {@code v} (a private
     * name only from {@code v} itself); unqualified the current vocabulary, then the public types of the imports
     * ({@code core} has none). Null when unresolved or ambiguous.
     */
    @Nullable
    public static FLChoiceType resolveChoice(@NotNull FLChoiceRef ref) {
        List<FLChoiceType> all = resolveChoiceAll(ref);
        return all.size() == 1 ? all.get(0) : null;
    }

    /** As {@link #resolveChoice}, but every candidate of an ambiguous name, in USING: order. */
    @NotNull
    public static List<FLChoiceType> resolveChoiceAll(@NotNull FLChoiceRef ref) {
        if (ref.getContainingFile() == null) return Collections.emptyList();
        Context c = new Context(ref.getContainingFile(), topStart(ref));
        int pos = ref.getTextRange().getStartOffset();
        String current = c.scope.vocabAt(pos);
        String name = ref.getBareName();
        String qualifier = ref.getVocab();
        if (qualifier != null) {
            if (isPrivateName(name) && !qualifier.equals(current)) return Collections.emptyList();
            FLChoiceType t = typeIn(c, qualifier, name);
            return t == null ? Collections.emptyList() : Collections.singletonList(t);
        }
        FLChoiceType own = typeIn(c, current, name);
        if (own != null) return Collections.singletonList(own);
        List<FLChoiceType> out = new ArrayList<>();
        if (!isPrivateName(name)) {
            for (String u : c.scope.usingAt(pos)) {
                if (u.equals(current)) continue;
                FLChoiceType t = typeIn(c, u, name);
                if (t != null) out.add(t);
            }
        }
        return out;
    }

    /** Whether any vocabulary declares a choice type {@code vocab:name} (this file or the index). */
    public static boolean typeDeclared(@NotNull PsiFile file, @NotNull String vocab, @NotNull String name) {
        return !FLScope.of(file).findType(vocab, name).isEmpty()
                || !FLIndexLookup.typeDeclarations(file.getProject(), vocab, name).isEmpty();
    }

    @Nullable
    private static FLChoiceType typeIn(Context c, String vocab, String name) {
        FLNamedElement earlier = FLScope.pickEarlier(c.scope.findType(vocab, name), c.formStart);
        if (earlier instanceof FLChoiceType t) return t;
        for (FLIndexLookup.Hit hit : FLIndexLookup.typeDeclarations(c.project, vocab, name)) {
            if (hit.file().equals(c.self)) continue;
            List<FLIndexEntry> entries = hit.entries();
            for (int k = entries.size() - 1; k >= 0; k--) {
                if (FLIndexLookup.element(c.project, hit.file(), entries.get(k)) instanceof FLChoiceType t) return t;
            }
        }
        return null;
    }

    private static int topStart(PsiElement e) {
        PsiElement top = e;
        while (top.getParent() != null && !(top.getParent() instanceof PsiFile)) top = top.getParent();
        return top.getTextRange().getStartOffset();
    }

    private static List<PsiElement> single(@Nullable PsiElement e) {
        return e == null ? Collections.emptyList() : Collections.singletonList(e);
    }

    /**
     * The declaration of {@code name} in {@code vocab}: this file before the use, else another file; with
     * {@code allowForward} also a later declaration in this file.
     */
    @Nullable
    private static PsiElement definedIn(Context c, String vocab, String name, boolean allowForward) {
        List<FLScope.Decl> local = c.scope.find(vocab, name);
        FLNamedElement earlier = FLScope.pickEarlier(local, c.formStart);
        if (earlier != null) return earlier;
        for (FLIndexLookup.Hit hit : FLIndexLookup.declarations(c.project, vocab, name)) {
            if (hit.file().equals(c.self)) continue;
            List<FLIndexEntry> entries = hit.entries();
            for (int k = entries.size() - 1; k >= 0; k--) {
                if (!entries.get(k).isDeclaration()) continue;
                FLNamedElement e = FLIndexLookup.element(c.project, hit.file(), entries.get(k));
                if (e != null) return e;
            }
        }
        return allowForward ? FLScope.pick(local, c.formStart) : null;
    }
}
