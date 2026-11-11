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
 *   <li>a parameter of the enclosing colon definition;</li>
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

        // 1. locals
        FLDefinition def = PsiTreeUtil.getParentOfType(ref, FLDefinition.class);
        if (def != null) {
            FLParameter p = def.findParameter(name);
            if (p != null) return Collections.singletonList(p);
        }
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
