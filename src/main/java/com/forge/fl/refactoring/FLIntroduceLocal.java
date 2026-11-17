// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.refactoring;

import com.forge.fl.effects.FLStackAnalysis;
import com.forge.fl.psi.FLElementTypes;
import com.forge.fl.psi.FLWordRef;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Introduce {@code :>} local (Refactor | Introduce Variable): the selected code, which must push exactly one value
 * and take none, is bound with {@code :>} and the value pushed again by name: {@code a b + c *} becomes
 * {@code a b + :> sum sum c *}, or, when the selection starts its line, {@code a b + :> sum} on its own line (the style
 * guide's "bind at the end of the line that computes the value"). Later identical code in the same body (the
 * local's scope) can be replaced by the name too.
 */
public final class FLIntroduceLocal implements RefactoringActionHandler {
    static final String TITLE = "Introduce :> Local";

    /** What the refactoring will do: the selection, the later identical occurrences, a suggested name. */
    public record Plan(@NotNull FLSelection selection, @NotNull List<TextRange> others, @NotNull String suggestedName) {
    }

    @NotNull
    public static Plan plan(@NotNull PsiFile file, int start, int end) throws FLSelection.Refused {
        FLSelection sel = FLSelection.of(file, start, end);
        if (sel.inListLiteral()) throw new FLSelection.Refused("':>' is not allowed inside a { } list literal (E_SYNTAX)");
        int[] effect = FLStackAnalysis.effectOf(sel.definition(), sel.items());
        if (effect == null) throw new FLSelection.Refused("The stack effect of the selection cannot be followed");
        if (effect[0] != 0 || effect[1] != 1) {
            throw new FLSelection.Refused("The selection must push exactly one value and take none; it takes " + effect[0]
                    + " and leaves " + effect[1]);
        }
        return new Plan(sel, occurrences(sel), suggestName(sel));
    }

    /** Applies the plan with {@code name}, also replacing the later occurrences when {@code all}. */
    public static void perform(@NotNull Plan plan, @NotNull String name, boolean all) {
        FLSelection sel = plan.selection();
        PsiFile file = sel.definition().getContainingFile();
        CharSequence text = file.getViewProvider().getContents();
        TextRange r = sel.range();
        String code = sel.text();
        String replacement = FLRefactorings.startsLine(text, r.getStartOffset())
                ? code + " :> " + name + "\n" + FLRefactorings.indentOf(text, r.getStartOffset()) + name
                : code + " :> " + name + " " + name;
        List<FLRefactorings.Edit> edits = new ArrayList<>();
        edits.add(new FLRefactorings.Edit(r, replacement));
        if (all) for (TextRange o : plan.others()) edits.add(new FLRefactorings.Edit(o, name));
        FLRefactorings.apply(file.getProject(), file, TITLE, edits);
    }

    /** Why {@code name} cannot be used, or null. */
    @Nullable
    public static String nameProblem(@NotNull Plan plan, @NotNull String name) {
        return FLRefactorings.localNameProblem(name, plan.selection().items().get(0));
    }

    /**
     * Later runs of items with the same code, in the selection's container and the quotations inside it (where the
     * new local is visible), not inside a list literal.
     */
    private static List<TextRange> occurrences(FLSelection sel) {
        List<TextRange> out = new ArrayList<>();
        String want = FLRefactorings.normalized(sel.text());
        int n = sel.items().size();
        int after = sel.range().getEndOffset();
        collect(sel.container(), want, n, after, out);
        return out;
    }

    private static void collect(PsiElement container, String want, int n, int after, List<TextRange> out) {
        List<PsiElement> items = container instanceof com.forge.fl.psi.FLDefinition d
                ? FLStackAnalysis.bodyItems(d) : FLSelection.quotationItems(container);
        CharSequence text = container.getContainingFile().getViewProvider().getContents();
        for (int i = 0; i < items.size(); i++) {
            PsiElement item = items.get(i);
            if (item.getTextRange().getStartOffset() < after) continue;
            if (i + n <= items.size()) {
                TextRange r = new TextRange(item.getTextRange().getStartOffset(), items.get(i + n - 1).getTextRange().getEndOffset());
                if (FLRefactorings.normalized(r.subSequence(text).toString()).equals(want)) {
                    out.add(r);
                    i += n - 1;
                    continue;
                }
            }
            if (item.getNode().getElementType() == FLElementTypes.QUOTATION) collect(item, want, n, after, out);
        }
    }

    /** A name from the last word of the selection ({@code str>bytes} gives {@code bytes}), else {@code value}. */
    private static String suggestName(FLSelection sel) {
        PsiElement last = sel.items().get(sel.items().size() - 1);
        String base = "value";
        if (last instanceof FLWordRef ref) {
            String w = ref.getBareName();
            int arrow = w.lastIndexOf('>');
            if (arrow >= 0 && arrow < w.length() - 1) w = w.substring(arrow + 1);
            w = w.replaceAll("[^a-z0-9-]", "");
            if (!w.isEmpty() && Character.isLetter(w.charAt(0))) base = w;
        }
        String name = base;
        for (int k = 2; FLRefactorings.localNameProblem(name, sel.items().get(0)) != null
                || com.forge.fl.psi.FLResolver.isVisible(sel.definition().getContainingFile(), name, sel.definition().getTextRange().getStartOffset()); k++) {
            name = base + k;
            if (k > 99) break;
        }
        return name;
    }

    // ---- the action ---------------------------------------------------------------------------------------------

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        if (editor == null || file == null) return;
        SelectionModel s = editor.getSelectionModel();
        int start = s.hasSelection() ? s.getSelectionStart() : editor.getCaretModel().getOffset();
        int end = s.hasSelection() ? s.getSelectionEnd() : start;
        Plan plan;
        try {
            plan = plan(file, start, end);
        } catch (FLSelection.Refused e) {
            CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), TITLE, null);
            return;
        }
        String name = Messages.showInputDialog(project, "Name of the local:", TITLE, null, plan.suggestedName(),
                new InputValidatorEx() {
                    @Override
                    public String getErrorText(String inputString) {
                        return nameProblem(plan, inputString.strip());
                    }
                });
        if (name == null) return;
        boolean all = false;
        if (!plan.others().isEmpty()) {
            all = Messages.showYesNoDialog(project, "Replace all " + (plan.others().size() + 1)
                    + " occurrences of this code with the local?", TITLE, null) == Messages.YES;
        }
        perform(plan, name.strip(), all);
    }

    @Override
    public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    }
}
