package com.forge.fl.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A soft reference from a word use to its declaration (docs/LANGUAGE.md 6.2, 6.3), in this file, in another file
 * or among the built-in words. It is poly-variant because two imports can define the same name: then
 * {@link #resolve()} is null and {@link #multiResolve} lists the candidates in USING: order. An unresolved name is
 * not an error here (diagnostics are a later phase).
 */
public class FLWordReference extends PsiPolyVariantReferenceBase<FLWordRef> {
    private static final ResolveCache.PolyVariantResolver<FLWordReference> RESOLVER = (ref, incomplete) -> {
        List<PsiElement> found = FLResolver.resolve(ref.getElement());
        ResolveResult[] out = new ResolveResult[found.size()];
        for (int i = 0; i < out.length; i++) out[i] = new PsiElementResolveResult(found.get(i));
        return out;
    };

    public FLWordReference(@NotNull FLWordRef element) {
        super(element, new TextRange(0, element.getTextLength()), true);
    }

    /** Always the current text: renaming replaces the leaf, which changes the length of the element. */
    @NotNull
    @Override
    public TextRange getRangeInElement() {
        return new TextRange(0, getElement().getTextLength());
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        return ResolveCache.getInstance(getElement().getProject()).resolveWithCaching(this, RESOLVER, false, incompleteCode);
    }

    /** The single target, or null when unresolved or ambiguous. */
    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] results = multiResolve(false);
        return results.length == 1 ? results[0].getElement() : null;
    }

    /** True for every candidate of an ambiguous name too, so that find usages shows it. */
    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        for (ResolveResult r : multiResolve(false)) {
            PsiElement target = r.getElement();
            if (target != null && getElement().getManager().areElementsEquivalent(target, element)) return true;
        }
        return false;
    }

    /** Whether the name is ambiguous between several imports. */
    public boolean isAmbiguous() {
        return multiResolve(false).length > 1;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return EMPTY_ARRAY;
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newName) throws IncorrectOperationException {
        PsiElement target = resolve();
        String bare = getElement().getBareName();
        boolean bang = target instanceof FLVariable v && bare.endsWith("!") && v.getName() != null
                && bare.equals(v.getName() + "!");
        getElement().replaceName(newName, bang);
        return getElement();
    }

    /**
     * Rewrites this use when {@code target} (named {@code oldName} until now) is renamed. Does not depend on
     * the order in which the declaration and its uses are renamed: a use spelled {@code old!} of a VARIABLE
     * becomes {@code <new>!}.
     */
    public void rename(PsiElement target, String oldName, String newName) {
        boolean bang = target instanceof FLVariable && getElement().getBareName().equals(oldName + "!");
        getElement().replaceName(newName, bang);
    }
}
