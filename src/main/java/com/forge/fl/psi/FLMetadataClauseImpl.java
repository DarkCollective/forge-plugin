package com.forge.fl.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class FLMetadataClauseImpl extends ASTWrapperPsiElement implements FLMetadataClause {
    public FLMetadataClauseImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public String getClauseName() {
        for (var child : getChildren()) {
            if (child instanceof FLWordElement) {
                return child.getText();
            }
        }
        return "";
    }

    @Override
    public String getClauseValue() {
        String text = getText();
        if (text == null || text.isBlank()) {
            return "";
        }
        int idx = text.indexOf(' ');
        if (idx < 0) {
            return "";
        }
        return text.substring(idx + 1).trim();
    }
}
