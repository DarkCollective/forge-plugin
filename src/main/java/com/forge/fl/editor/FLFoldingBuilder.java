// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.editor;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.psi.FLChoiceType;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLElementTypes;
import com.forge.fl.psi.FLMetaBlock;
import com.forge.fl.psi.FLPsiElement;
import com.forge.fl.psi.FLWordRef;
import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Folding of multi-line colon definitions ({@code : name ( effect ) ...}), {@code META:} blocks, quotations and
 * list literals, and runs of three or more consecutive {@code \} comment lines. A licence header at the top of the
 * file folds by default when the platform setting "file header" says so.
 */
public final class FLFoldingBuilder extends FoldingBuilderEx implements DumbAware {
    private static final int MIN_COMMENT_RUN = 3;
    private static final int MAX_PLACEHOLDER = 100;

    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> out = new ArrayList<>();
        for (PsiElement e : PsiTreeUtil.findChildrenOfType(root, FLPsiElement.class)) {
            var type = e.getNode().getElementType();
            if (e instanceof FLDefinition d) {
                add(out, document, e, e.getTextRange(), definitionPlaceholder(d));
            } else if (e instanceof FLMetaBlock b) {
                FLWordRef t = b.getTarget();
                add(out, document, e, e.getTextRange(), "META: " + (t == null ? "" : t.getText()) + " ...");
            } else if (e instanceof FLChoiceType c) {
                add(out, document, e, e.getTextRange(), "CHOICE: " + (c.getName() == null ? "" : c.getName()) + " ...");
            } else if (type == FLElementTypes.QUOTATION) {
                add(out, document, e, e.getTextRange(), "[ ... ]");
            } else if (type == FLElementTypes.LIST_LITERAL) {
                add(out, document, e, e.getTextRange(), "{ ... }");
            }
        }
        List<PsiComment> seen = new ArrayList<>();
        for (PsiComment c : PsiTreeUtil.findChildrenOfType(root, PsiComment.class)) {
            if (c.getNode().getElementType() != FLTokenTypes.LINE_COMMENT || seen.contains(c)) continue;
            List<PsiComment> run = commentRun(c, document);
            seen.addAll(run);
            if (run.size() >= MIN_COMMENT_RUN || (run.size() >= 2 && isLicence(run) && c.getTextRange().getStartOffset() == 0)) {
                TextRange r = new TextRange(run.get(0).getTextRange().getStartOffset(),
                        run.get(run.size() - 1).getTextRange().getEndOffset());
                add(out, document, run.get(0), r, cap(run.get(0).getText().trim()) + " ...");
            }
        }
        return out.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    private static void add(List<FoldingDescriptor> out, Document doc, PsiElement anchor, TextRange r, String placeholder) {
        if (r.getLength() < 2 || doc.getLineNumber(r.getStartOffset()) == doc.getLineNumber(r.getEndOffset())) return;
        out.add(new FoldingDescriptor(anchor.getNode(), r, null, placeholder));
    }

    private static String definitionPlaceholder(FLDefinition d) {
        String eff = d.getEffectText();
        return d.getKeywordText() + " " + d.getName() + (eff == null ? "" : " " + cap(eff)) + " ...";
    }

    private static String cap(String s) {
        return s.length() <= MAX_PLACEHOLDER ? s : s.substring(0, MAX_PLACEHOLDER - 1) + "…";
    }

    /** Consecutive comment lines that each start their line, with no blank line between. */
    private static List<PsiComment> commentRun(PsiComment first, Document doc) {
        List<PsiComment> run = new ArrayList<>();
        if (!startsLine(first, doc)) return run;
        run.add(first);
        PsiElement n = first.getNextSibling();
        while (n != null) {
            if (n instanceof PsiWhiteSpace w) {
                if (com.intellij.openapi.util.text.StringUtil.countNewLines(w.getText()) != 1) break;
            } else if (n instanceof PsiComment c && c.getNode().getElementType() == FLTokenTypes.LINE_COMMENT) {
                run.add(c);
            } else {
                break;
            }
            n = n.getNextSibling();
        }
        return run;
    }

    private static boolean startsLine(PsiComment c, Document doc) {
        int start = c.getTextRange().getStartOffset();
        int lineStart = doc.getLineStartOffset(doc.getLineNumber(start));
        return doc.getText(new TextRange(lineStart, start)).isBlank();
    }

    private static boolean isLicence(List<PsiComment> run) {
        StringBuilder sb = new StringBuilder();
        for (PsiComment c : run) sb.append(c.getText()).append('\n');
        String t = sb.toString().toLowerCase();
        return t.contains("copyright") || t.contains("licence") || t.contains("license");
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        return "...";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        PsiElement psi = node.getPsi();
        if (psi instanceof PsiComment c && node.getStartOffset() == 0
                && node.getElementType() == FLTokenTypes.LINE_COMMENT) {
            List<PsiComment> run = new ArrayList<>();
            run.add(c);
            for (PsiElement n = c.getNextSibling(); n != null; n = n.getNextSibling()) {
                if (n instanceof PsiComment cc) run.add(cc);
                else if (!(n instanceof PsiWhiteSpace w) || com.intellij.openapi.util.text.StringUtil.countNewLines(w.getText()) != 1) break;
            }
            return isLicence(run) && CodeFoldingSettings.getInstance().COLLAPSE_FILE_HEADER;
        }
        return false;
    }
}
