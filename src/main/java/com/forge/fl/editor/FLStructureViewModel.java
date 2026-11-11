package com.forge.fl.editor;

import com.forge.fl.psi.FLDefinition;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FLStructureViewModel extends StructureViewModelBase {
    public FLStructureViewModel(@NotNull PsiFile psiFile, @Nullable Editor editor) {
        super(psiFile, editor, new FLStructureViewElement(psiFile));
        withSuitableClasses(FLDefinition.class);
    }

}
