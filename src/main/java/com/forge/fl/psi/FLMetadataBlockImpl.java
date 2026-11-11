package com.forge.fl.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class FLMetadataBlockImpl extends ASTWrapperPsiElement implements FLMetadataBlock {
    public FLMetadataBlockImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public String getTargetName() {
        PsiElement parent = getParent();
        if (parent == null) {
            return "";
        }
        String text = parent.getText();
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] lines = text.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("name") || trimmed.startsWith("qname") || trimmed.startsWith("vocab")) {
                int idx = trimmed.indexOf(':');
                if (idx >= 0) {
                    return trimmed.substring(idx + 1).trim();
                }
            }
        }
        return "";
    }
}
