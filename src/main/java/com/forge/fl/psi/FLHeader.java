package com.forge.fl.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** The stack-effect header {@code ( inputs -- outputs )}. */
public class FLHeader extends FLPsiElement {
    public FLHeader(@NotNull ASTNode node) {
        super(node);
    }

    /** Named inputs and bare labels before {@code --}; not bare type names and not outputs. */
    @NotNull
    public List<FLParameter> getParameters() {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, FLParameter.class);
    }
}
