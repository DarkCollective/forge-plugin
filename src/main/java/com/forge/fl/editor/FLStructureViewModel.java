package com.forge.fl.editor;

import com.forge.fl.psi.FLConstant;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLPrimitive;
import com.forge.fl.psi.FLVariable;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FLStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {
    public FLStructureViewModel(@NotNull PsiFile psiFile, @Nullable Editor editor) {
        super(psiFile, editor, new FLStructureViewElement(psiFile));
        withSuitableClasses(FLDefinition.class, FLConstant.class, FLVariable.class, FLPrimitive.class);
    }

    @Override
    public boolean isAlwaysShowsPlus(com.intellij.ide.structureView.StructureViewTreeElement element) {
        return false;
    }

    @Override
    public boolean isAlwaysLeaf(com.intellij.ide.structureView.StructureViewTreeElement element) {
        return !(element.getValue() instanceof PsiFile);
    }
}
