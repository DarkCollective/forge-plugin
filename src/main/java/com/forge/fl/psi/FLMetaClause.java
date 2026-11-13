// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One META: clause: doc, category, version, param, alias, see-also, and (FL v0.2) use-when, link, inverse, helper
 * or the reserved checks. The URL of a {@code link} clause is a web reference, so it can be opened from the editor.
 */
public class FLMetaClause extends FLPsiElement {
    public FLMetaClause(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public com.intellij.psi.PsiReference @NotNull [] getReferences() {
        if (!"link".equals(getKeyword())) return com.intellij.psi.PsiReference.EMPTY_ARRAY;
        for (com.intellij.psi.PsiElement c = getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNode().getElementType() != com.forge.fl.FLTokenTypes.STRING) continue;
            String url = com.forge.fl.lexer.FLSyntax.decodeString(c.getText());
            if (!url.startsWith("https://") || c.getTextLength() < 2) break;
            int from = c.getStartOffsetInParent() + 1;
            return new com.intellij.psi.PsiReference[]{new com.intellij.openapi.paths.WebReference(this,
                    new com.intellij.openapi.util.TextRange(from, from + c.getTextLength() - 2), url)};
        }
        return com.intellij.psi.PsiReference.EMPTY_ARRAY;
    }

    @Nullable
    public String getKeyword() {
        ASTNode first = getNode().getFirstChildNode();
        return first == null ? null : first.getText();
    }
}
