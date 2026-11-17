// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.refactoring;

import com.forge.fl.psi.FLNamesValidator;
import com.forge.fl.psi.FLResolver;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared pieces of the FL refactorings: names, text edits, line layout. */
final class FLRefactorings {
    private FLRefactorings() {
    }

    /** One replacement in a document. */
    record Edit(int start, int end, String text) {
        Edit(TextRange r, String text) {
            this(r.getStartOffset(), r.getEndOffset(), text);
        }
    }

    /** Applies edits (non-overlapping) from the end, as one undoable command, and commits the PSI. */
    static void apply(@NotNull Project project, @NotNull PsiFile file, @NotNull String name, @NotNull List<Edit> edits) {
        Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
        if (doc == null) return;
        List<Edit> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator.comparingInt(Edit::start).reversed());
        WriteCommandAction.runWriteCommandAction(project, name, null, () -> {
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(doc);
            for (Edit e : sorted) doc.replaceString(e.start(), e.end(), e.text());
            PsiDocumentManager.getInstance(project).commitDocument(doc);
        }, file);
    }

    /** Why {@code name} cannot name a new local at {@code at}, or null. */
    @Nullable
    static String localNameProblem(@NotNull String name, @NotNull PsiElement at) {
        String p = wordNameProblem(name);
        if (p != null) return p;
        if (FLResolver.findLocal(at, name) != null) return "'" + name + "' is already a local here (E_REDEFINED)";
        return null;
    }

    /** Why {@code name} is not a valid, unreserved, unqualified word name, or null. */
    @Nullable
    static String wordNameProblem(@NotNull String name) {
        FLNamesValidator v = new FLNamesValidator();
        if (name.isEmpty() || name.indexOf(':') >= 0 || !v.isIdentifier(name, null)) return "'" + name + "' is not a valid name";
        if (v.isKeyword(name, null)) return "'" + name + "' is a reserved word";
        return null;
    }

    /** The whitespace that starts the line of {@code offset}, and whether only whitespace precedes it on the line. */
    static String indentOf(@NotNull CharSequence text, int offset) {
        int lineStart = offset;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
        int i = lineStart;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
        return text.subSequence(lineStart, i).toString();
    }

    static boolean startsLine(@NotNull CharSequence text, int offset) {
        for (int i = offset - 1; i >= 0 && text.charAt(i) != '\n'; i--) {
            if (!Character.isWhitespace(text.charAt(i))) return false;
        }
        return true;
    }

    /** The items before {@code item} in its definition body, quotation or list literal, in order. */
    @NotNull
    static List<PsiElement> itemsBefore(@NotNull PsiElement item) {
        List<PsiElement> out = new ArrayList<>();
        PsiElement container = item.getParent();
        List<PsiElement> all = container instanceof com.forge.fl.psi.FLDefinition d
                ? com.forge.fl.effects.FLStackAnalysis.bodyItems(d) : FLSelection.quotationItems(container);
        for (PsiElement e : all) {
            if (e == item) break;
            out.add(e);
        }
        return out;
    }

    /** Text with runs of whitespace (outside nothing: FL strings never span lines) folded, for comparing code. */
    static String normalized(@NotNull String code) {
        return code.strip().replaceAll("\\s+", " ");
    }
}
