// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.formatting;

import com.forge.fl.FLFile;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor;
import org.jetbrains.annotations.NotNull;

public final class FLTrailingWhitespaceProcessor implements PostFormatProcessor {
    @NotNull
    @Override
    public PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
        if (source instanceof FLFile file) trimTrailingWhitespace(file);
        return source;
    }

    @NotNull
    @Override
    public TextRange processText(@NotNull PsiFile source, @NotNull TextRange range,
                                 @NotNull CodeStyleSettings settings) {
        if (source instanceof FLFile file) trimTrailingWhitespace(file);
        return source.getTextRange();
    }

    @Override
    public boolean isWhitespaceOnly() {
        return true;
    }

    public static void trimTrailingWhitespace(FLFile file) {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) return;

        CharSequence text = document.getCharsSequence();
        int contentEnd = text.length();
        while (contentEnd > 0) {
            char c = text.charAt(contentEnd - 1);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') break;
            contentEnd--;
        }
        if (contentEnd == text.length()) return;

        String ending = "";
        for (int i = contentEnd; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                ending = i > contentEnd && text.charAt(i - 1) == '\r' ? "\r\n" : "\n";
                break;
            }
            if (c == '\r') {
                ending = i + 1 < text.length() && text.charAt(i + 1) == '\n' ? "\r\n" : "\r";
                break;
            }
        }
        if (text.subSequence(contentEnd, text.length()).toString().equals(ending)) return;
        document.replaceString(contentEnd, text.length(), ending);
        PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
    }
}
