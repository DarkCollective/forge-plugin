package com.forge.fl.editor;

import com.forge.fl.psi.FLDefinition;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.List;

public class FLStructureViewElement implements StructureViewTreeElement {
    private final PsiElement element;

    public FLStructureViewElement(@NotNull PsiElement element) {
        this.element = element;
    }

    @Override
    public Object getValue() {
        return element;
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (element instanceof NavigatablePsiElement navigable) {
            navigable.navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        return element instanceof NavigatablePsiElement navigable && navigable.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return element instanceof NavigatablePsiElement navigable && navigable.canNavigateToSource();
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public String getPresentableText() {
                if (element instanceof FLDefinition definition) {
                    return definition.getName();
                }
                return element.getText() == null || element.getText().isBlank()
                        ? element.getClass().getSimpleName()
                        : element.getText();
            }

            @Override
            public String getLocationString() {
                return null;
            }

            @Override
            public Icon getIcon(boolean unused) {
                return null;
            }
        };
    }

    @Override
    public StructureViewTreeElement @NotNull [] getChildren() {
        List<PsiElement> defs = new ArrayList<>();
        for (PsiElement child : PsiTreeUtil.getChildrenOfAnyType(element, FLDefinition.class)) {
            defs.add(child);
        }
        StructureViewTreeElement[] result = new StructureViewTreeElement[defs.size()];
        for (int i = 0; i < defs.size(); i++) {
            result[i] = new FLStructureViewElement(defs.get(i));
        }
        return result;
    }
}
