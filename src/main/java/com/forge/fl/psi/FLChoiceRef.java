// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The type of {@code name:choice<type>} or {@code name:choice<vocab:type>} in a header (FL v0.2, docs/LANGUAGE.md
 * 5.4, 6.8). It refers to a {@link FLChoiceType}.
 */
public class FLChoiceRef extends FLPsiElement {
    public FLChoiceRef(@NotNull ASTNode node) {
        super(node);
    }

    @Nullable
    public String getVocab() {
        String t = getText();
        int c = t.indexOf(':');
        return c > 0 ? t.substring(0, c) : null;
    }

    @NotNull
    public String getBareName() {
        String t = getText();
        return t.substring(t.indexOf(':') + 1);
    }

    @NotNull
    @Override
    public Reference getReference() {
        return new Reference(this);
    }

    public static final class Reference extends PsiReferenceBase<FLChoiceRef> {
        Reference(FLChoiceRef element) {
            super(element, new TextRange(0, element.getTextLength()), true);
        }

        @NotNull
        @Override
        public TextRange getRangeInElement() {
            String vocab = getElement().getVocab();
            int from = vocab == null ? 0 : vocab.length() + 1;
            return new TextRange(from, getElement().getTextLength());
        }

        @Nullable
        @Override
        public PsiElement resolve() {
            return FLResolver.resolveChoice(getElement());
        }

        @Override
        public PsiElement handleElementRename(@NotNull String newName) throws IncorrectOperationException {
            PsiElement leaf = getElement().getFirstChild();
            String vocab = getElement().getVocab();
            if (leaf != null) {
                leaf.replace(FLElementFactory.createChoiceRefLeaf(getElement().getProject(),
                        (vocab == null ? "" : vocab + ":") + newName));
            }
            return getElement();
        }
    }
}
