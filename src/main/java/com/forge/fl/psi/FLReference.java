package com.forge.fl.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FLReference extends PsiReferenceBase<FLWordElement> {
    private final String name;

    public FLReference(@NotNull FLWordElement element) {
        super(element, new TextRange(0, element.getTextLength()));
        this.name = element.getText();
    }

    @Override
    @Nullable
    public PsiElement resolve() {
        PsiElement file = myElement.getContainingFile();
        if (file == null) {
            return null;
        }
        for (FLDefinition definition : PsiTreeUtil.findChildrenOfType(file, FLDefinition.class)) {
            if (name.equals(definition.getName())) {
                return definition;
            }
        }
        return null;
    }

    @Override
    @NotNull
    public Object @NotNull [] getVariants() {
        return new Object[0];
    }

    @Override
    public PsiElement handleElementRename(String newName) {
        return myElement.setName(newName);
    }

    @Override
    public boolean isSoft() {
        return true;
    }
}
