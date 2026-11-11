package com.forge.fl.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Soft reference from a META: {@code param} clause to a header parameter. */
public class FLParamReference extends PsiReferenceBase<FLParamRef> {
    public FLParamReference(@NotNull FLParamRef element) {
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
        getElement().replaceName(newName);
        return getElement();
    }
}
