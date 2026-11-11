package com.forge.fl.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One META: clause: doc, category, version, param, alias or see-also. */
public class FLMetaClause extends FLPsiElement {
    public FLMetaClause(@NotNull ASTNode node) {
        super(node);
    }

    @Nullable
    public String getKeyword() {
        ASTNode first = getNode().getFirstChildNode();
        return first == null ? null : first.getText();
    }
}
