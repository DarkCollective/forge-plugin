package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/** A variable declaration (see docs/LANGUAGE.md section 5). */
public class FLVariable extends FLNamedElement {
    public FLVariable(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    protected IElementType nameTokenType() {
        return FLTokenTypes.WORD_NAME;
    }

    @Override
    public String getKindName() {
        return "variable";
    }

    @Override
    protected PsiElement createNameLeaf(String name) {
        return FLElementFactory.createVariableName(getProject(), name);
    }

    /** The literal that initialises the declaration. */
    @Override
    public String getLocationText() {
        PsiElement first = getFirstChild();
        return first == null ? null : first.getText();
    }
}
