package com.forge.fl.editor;

import com.forge.fl.psi.FLConstant;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.psi.FLPrimitive;
import com.forge.fl.psi.FLVariable;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Structure view: a file lists its definitions, constants, variables and primitives. */
public class FLStructureViewElement extends PsiTreeElementBase<PsiElement> {
    public FLStructureViewElement(PsiElement element) {
        super(element);
    }

    @NotNull
    @Override
    public Collection<StructureViewTreeElement> getChildrenBase() {
        List<StructureViewTreeElement> result = new ArrayList<>();
        PsiElement element = getElement();
        if (element instanceof PsiFile) {
            for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof FLDefinition || child instanceof FLConstant || child instanceof FLVariable
                        || child instanceof FLPrimitive) {
                    result.add(new FLStructureViewElement(child));
                }
            }
        }
        return result;
    }

    @Nullable
    @Override
    public String getPresentableText() {
        PsiElement element = getElement();
        if (element instanceof FLNamedElement named) return named.getName();
        return element == null ? null : element.getContainingFile().getName();
    }

    @Nullable
    @Override
    public String getLocationString() {
        PsiElement element = getElement();
        return element instanceof FLNamedElement named ? named.getLocationText() : null;
    }

    @Nullable
    @Override
    public Icon getIcon(boolean open) {
        PsiElement element = getElement();
        return element == null ? null : element.getIcon(0);
    }
}
