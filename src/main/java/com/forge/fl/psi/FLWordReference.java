package com.forge.fl.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A soft reference from a word use to its declaration. Core words and words of other files do not resolve;
 * that is not an error (cross-file and core resolution are a later phase).
 */
public class FLWordReference extends PsiReferenceBase<FLWordRef> {
    public FLWordReference(@NotNull FLWordRef element) {
        super(element, new TextRange(0, element.getTextLength()), true);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        return getElement().resolveTarget();
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
