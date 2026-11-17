// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.refactoring;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.effects.FLStackAnalysis;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLElementTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A run of whole body items selected in the editor: words, literals, quotations, list literals and {@code :>} binds
 * of one definition body or one quotation. The refactorings work on these, never on parts of an item.
 */
public record FLSelection(@NotNull FLDefinition definition, @NotNull PsiElement container, @NotNull List<PsiElement> items) {
    /** Thrown with a message for the user when a selection cannot be used. */
    public static final class Refused extends Exception {
        public Refused(String message) {
            super(message);
        }
    }

    @NotNull
    public TextRange range() {
        return new TextRange(items.get(0).getTextRange().getStartOffset(), items.get(items.size() - 1).getTextRange().getEndOffset());
    }

    @NotNull
    public String text() {
        return range().subSequence(definition.getContainingFile().getViewProvider().getContents()).toString();
    }

    /** Whether the selection is lexically inside a {@code { }} list literal, where {@code :>} is not allowed. */
    public boolean inListLiteral() {
        for (PsiElement e = container; e != null && e != definition; e = e.getParent()) {
            if (e.getNode().getElementType() == FLElementTypes.LIST_LITERAL) return true;
        }
        return false;
    }

    /**
     * The items between {@code start} and {@code end} (whitespace and comments around them ignored). With an empty
     * range, the single item at the caret.
     */
    @NotNull
    public static FLSelection of(@NotNull PsiFile file, int start, int end) throws Refused {
        CharSequence text = file.getViewProvider().getContents();
        while (start < end && Character.isWhitespace(text.charAt(start))) start++;
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) end--;
        PsiElement first = file.findElementAt(start);
        PsiElement last = file.findElementAt(start == end ? start : end - 1);
        if (start == end && (first == null || first instanceof PsiWhiteSpace) && start > 0) {
            first = last = file.findElementAt(start - 1);
        }
        if (first == null || last == null) throw new Refused("Select code in the body of a word");
        PsiElement common = PsiTreeUtil.findCommonParent(first, last);
        PsiElement container = containerOf(common, start, end);
        FLDefinition def = PsiTreeUtil.getParentOfType(container, FLDefinition.class, false);
        if (container == null || def == null) throw new Refused("Select code in the body of a word");
        List<PsiElement> all = container instanceof FLDefinition d ? FLStackAnalysis.bodyItems(d) : quotationItems(container);
        int from = start == end ? first.getTextRange().getStartOffset() : start;
        int to = start == end ? last.getTextRange().getEndOffset() : end;
        List<PsiElement> items = new ArrayList<>();
        for (PsiElement item : all) {
            TextRange r = item.getTextRange();
            if (r.getEndOffset() <= from || r.getStartOffset() >= to) continue;
            if (r.getStartOffset() < from || r.getEndOffset() > to) {
                throw new Refused("The selection cuts '" + shorten(item.getText()) + "': select whole words, literals and quotations");
            }
            items.add(item);
        }
        if (items.isEmpty()) throw new Refused("Select code in the body of a word");
        return new FLSelection(def, container, items);
    }

    /**
     * The innermost definition body, quotation or list literal that contains {@code e}. One the selection covers whole
     * is an item of the container around it, not the container.
     */
    @Nullable
    private static PsiElement containerOf(@Nullable PsiElement e, int start, int end) {
        for (PsiElement p = e; p != null; p = p.getParent()) {
            if (p.getNode() == null) return null;
            if (p instanceof FLDefinition) return p;
            if (p.getNode().getElementType() == FLElementTypes.QUOTATION || p.getNode().getElementType() == FLElementTypes.LIST_LITERAL) {
                TextRange r = p.getTextRange();
                boolean whole = start <= r.getStartOffset() && end >= r.getEndOffset();
                if (!whole) return p;
            }
        }
        return null;
    }

    @NotNull
    static List<PsiElement> quotationItems(@NotNull PsiElement quotation) {
        List<PsiElement> out = new ArrayList<>();
        for (PsiElement c = quotation.getFirstChild(); c != null; c = c.getNextSibling()) {
            IElementType t = c.getNode().getElementType();
            if (c instanceof PsiWhiteSpace || c instanceof PsiComment || t == FLTokenTypes.LBRACKET || t == FLTokenTypes.RBRACKET
                    || t == FLTokenTypes.LBRACE || t == FLTokenTypes.RBRACE) continue;
            out.add(c);
        }
        return out;
    }

    static String shorten(String s) {
        String one = s.replaceAll("\\s+", " ");
        return one.length() > 30 ? one.substring(0, 27) + "..." : one;
    }
}
