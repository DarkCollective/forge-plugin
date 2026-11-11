package com.forge.fl.psi;

import com.forge.fl.builtins.FLBuiltins;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renames the declaration and every use in every file (an ambiguous use is left alone; a built-in is refused). Uses are rewritten first, from the old name, so the
 * {@code n!} form of a VARIABLE becomes {@code <new>!} whatever order the platform would have used.
 */
public class FLRenamePsiElementProcessor extends RenamePsiElementProcessor {
    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
        return element instanceof FLNamedElement;
    }

    @Override
    public boolean isInplaceRenameSupported() {
        return true;
    }

    @Override
    public void renameElement(@NotNull PsiElement element, @NotNull String newName, UsageInfo @NotNull [] usages,
                              @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
        if (FLBuiltins.isBuiltin(element)) {
            throw new IncorrectOperationException("A built-in word is implemented in TypeScript and cannot be renamed");
        }
        String oldName = ((PsiNamedElement) element).getName();
        for (UsageInfo usage : usages) {
            PsiReference ref = usage.getReference();
            if (ref == null) continue;
            // A name that is ambiguous between several imports does not compile; renaming one candidate must not
            // silently pick it.
            if (ref instanceof FLWordReference word && word.isAmbiguous()) continue;
            if (ref instanceof FLWordReference word && oldName != null) word.rename(element, oldName, newName);
            else ref.handleElementRename(newName);
        }
        ((PsiNamedElement) element).setName(newName);
        if (listener != null) listener.elementRenamed(element);
    }
}
