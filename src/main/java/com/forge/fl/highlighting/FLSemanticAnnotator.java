// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.highlighting;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLElementTypes;
import com.forge.fl.psi.FLInForm;
import com.forge.fl.psi.FLLocal;
import com.forge.fl.psi.FLMetaClause;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.psi.FLParamRef;
import com.forge.fl.psi.FLParameter;
import com.forge.fl.psi.FLWordRef;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;

/**
 * Colours what the lexer cannot know: the clause words of META: blocks and the {@code doc} / {@code category} of
 * leading declarations (keywords), declared names, uses of parameters inside bodies, uses of built-in words and
 * the vocabulary prefix of qualified names.
 */
public final class FLSemanticAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof FLMetaClause clause
                || element.getNode().getElementType() == FLElementTypes.CHOICE_OPTION) {
            PsiElement clause = element;
            PsiElement kw = clause.getFirstChild();
            if (kw != null && kw.getNode().getElementType() == FLTokenTypes.WORD_NAME) {
                mark(holder, kw.getTextRange(), FLSyntaxHighlighter.META_KEYWORD);
            }
        } else if (element.getNode().getElementType() == FLElementTypes.DOC_DECL) {
            PsiElement word = element.getLastChild();
            if (word != null) mark(holder, word.getTextRange(), FLSyntaxHighlighter.META_KEYWORD);
        } else if (element instanceof FLNamedElement named) {
            PsiElement id = named.getNameIdentifier();
            if (id != null && element instanceof FLLocal) {
                mark(holder, id.getTextRange(), FLSyntaxHighlighter.PARAMETER_USE);
            } else if (id != null && !(element instanceof FLParameter)) {
                mark(holder, id.getTextRange(), FLSyntaxHighlighter.DECLARATION_NAME);
            }
        } else if (element instanceof FLInForm in) {
            PsiElement v = in.getVocabElement();
            if (v != null) mark(holder, v.getTextRange(), FLSyntaxHighlighter.VOCABULARY);
        } else if (element.getNode().getElementType() == FLElementTypes.USING_FORM) {
            for (PsiElement c = element.getFirstChild(); c != null; c = c.getNextSibling()) {
                if (c.getNode().getElementType() == FLTokenTypes.WORD_NAME) {
                    mark(holder, c.getTextRange(), FLSyntaxHighlighter.VOCABULARY);
                }
            }
        } else if (element instanceof FLParamRef) {
            mark(holder, element.getTextRange(), FLSyntaxHighlighter.PARAMETER_USE);
        } else if (element instanceof FLWordRef ref) {
            word(ref, holder);
        }
    }

    private static void word(FLWordRef ref, AnnotationHolder holder) {
        TextRange r = ref.getTextRange();
        String vocab = ref.getVocab();
        if (vocab != null) {
            mark(holder, TextRange.from(r.getStartOffset(), vocab.length()), FLSyntaxHighlighter.VOCABULARY);
        }
        ResolveResult[] results = ref.getReference().multiResolve(false);
        if (results.length != 1) return;
        PsiElement target = results[0].getElement();
        if (target instanceof FLParameter || target instanceof FLLocal) {
            mark(holder, r, FLSyntaxHighlighter.PARAMETER_USE);
        } else if (target instanceof FLDefinition && FLBuiltins.isBuiltin(target)) {
            int from = vocab == null ? r.getStartOffset() : r.getStartOffset() + vocab.length() + 1;
            mark(holder, new TextRange(from, r.getEndOffset()), FLSyntaxHighlighter.BUILTIN_WORD);
        }
    }

    private static void mark(AnnotationHolder holder, TextRange range, TextAttributesKey key) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(range).textAttributes(key).create();
    }
}
