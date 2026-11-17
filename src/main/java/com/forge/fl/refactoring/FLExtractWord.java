// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.refactoring;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.effects.FLCallee;
import com.forge.fl.effects.FLStackAnalysis;
import com.forge.fl.index.FLIndexLookup;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLLocal;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.psi.FLParameter;
import com.forge.fl.psi.FLResolver;
import com.forge.fl.psi.FLScope;
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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extract word (Refactor | Extract Method): the selected body code becomes a new colon definition placed before the
 * word it came from, and the selection becomes a call. The header is inferred: the values the selection takes from
 * the stack and leaves on it become anonymous {@code any} inputs and outputs, and each local of the old word that the
 * selection reads becomes a named input (with the parameter's type), pushed by name at the call:
 * {@code a b + n *} with locals {@code a b n} becomes {@code : _sum-times ( a:int b:int n:int -- any ) a b + n * ;}
 * and the call {@code a b n _sum-times}.
 */
public final class FLExtractWord implements RefactoringActionHandler {
    static final String TITLE = "Extract Word";

    /** What the refactoring will do. {@code locals}: name to header item, in order of first use. */
    public record Plan(@NotNull FLSelection selection, int stackIn, int stackOut, @NotNull Map<String, String> locals,
                       @NotNull String suggestedName) {
        /** The header of the new word. */
        @NotNull
        public String header() {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < stackIn; i++) sb.append(" any");
            for (String item : locals.values()) sb.append(' ').append(item);
            sb.append(" --");
            for (int i = 0; i < stackOut; i++) sb.append(" any");
            return sb.append(" )").toString();
        }
    }

    @NotNull
    public static Plan plan(@NotNull PsiFile file, int start, int end) throws FLSelection.Refused {
        FLSelection sel = FLSelection.of(file, start, end);
        TextRange range = sel.range();
        for (PsiElement item : sel.items()) {
            for (PsiElement leaf : PsiTreeUtil.collectElements(item, e -> e.getFirstChild() == null)) {
                if (leaf.getNode().getElementType() == FLTokenTypes.RECURSE_KW) {
                    throw new FLSelection.Refused("The selection uses 'recurse', which would call the new word");
                }
            }
            for (FLWordRef ref : refsIn(item)) {
                FLCallee callee = FLCallee.of(ref);
                if (callee != null && callee.isCore("exit")) {
                    throw new FLSelection.Refused("The selection uses 'exit', which would leave the new word instead");
                }
            }
        }
        int[] effect = FLStackAnalysis.effectOf(sel.definition(), sel.items());
        if (effect == null) throw new FLSelection.Refused("The stack effect of the selection cannot be inferred");
        Map<String, String> locals = new LinkedHashMap<>();
        for (PsiElement item : sel.items()) {
            for (FLWordRef ref : refsIn(item)) {
                PsiElement target = ref.getReference().resolve();
                if (!(target instanceof FLParameter || target instanceof FLLocal)) continue;
                if (range.contains(target.getTextRange())) continue; // bound inside the selection
                String name = ((FLNamedElement) target).getName();
                if (name != null) locals.putIfAbsent(name, headerItem((FLNamedElement) target));
            }
        }
        // A :> binding in the selection whose local is used after it would be lost.
        for (FLWordRef ref : refsIn(sel.definition())) {
            if (ref.getTextRange().getStartOffset() < range.getEndOffset()) continue;
            PsiElement target = ref.getReference().resolve();
            if (target instanceof FLLocal && range.contains(target.getTextRange())) {
                throw new FLSelection.Refused("The local '" + ((FLLocal) target).getName()
                        + "' is bound in the selection and used after it");
            }
        }
        return new Plan(sel, effect[0], effect[1], locals, suggestName(sel));
    }

    /** {@code n:int[1..5]=3} stays {@code n:int[1..5]} (a data input may not have a default); a label or a {@code :>} local is {@code name:any}. */
    private static String headerItem(FLNamedElement local) {
        if (local instanceof FLParameter p) {
            String text = p.getText();
            int colon = text.indexOf(':');
            if (colon < 0) return text + ":any";
            int eq = defaultStart(text);
            return eq < 0 ? text : text.substring(0, eq);
        }
        return local.getName() + ":any";
    }

    /** The {@code =} of a default, outside brackets, braces and strings; -1 when none. */
    private static int defaultStart(String item) {
        int depth = 0;
        boolean str = false;
        for (int i = 0; i < item.length(); i++) {
            char c = item.charAt(i);
            if (str) {
                if (c == '\\') i++;
                else if (c == '"') str = false;
            } else if (c == '"') str = true;
            else if (c == '[' || c == '{' || c == '<') depth++;
            else if (c == ']' || c == '}' || c == '>') depth--;
            else if (c == '=' && depth == 0) return i;
        }
        return -1;
    }

    static List<FLWordRef> refsIn(PsiElement e) {
        List<FLWordRef> out = new ArrayList<>(PsiTreeUtil.findChildrenOfType(e, FLWordRef.class));
        if (e instanceof FLWordRef ref) out.add(0, ref);
        return out;
    }

    private static String suggestName(FLSelection sel) {
        String base = "_" + sel.definition().getName() + "-part";
        String name = base;
        for (int k = 2; nameTaken(sel, name) && k < 100; k++) name = base + k;
        return name;
    }

    private static boolean nameTaken(FLSelection sel, String name) {
        PsiFile file = sel.definition().getContainingFile();
        String vocab = FLScope.of(file).vocabAt(sel.definition().getTextRange().getStartOffset());
        if (!FLScope.of(file).find(vocab, name).isEmpty()) return true;
        return FLIndexLookup.isAvailable(file.getProject()) && !FLIndexLookup.declarations(file.getProject(), vocab, name).isEmpty();
    }

    /** Why {@code name} cannot name the new word, or null. */
    @Nullable
    public static String nameProblem(@NotNull Plan plan, @NotNull String name) {
        String p = FLRefactorings.wordNameProblem(name);
        if (p != null) return p;
        if (nameTaken(plan.selection(), name)) return "'" + name + "' is already defined in this vocabulary (E_REDEFINED)";
        if (!FLResolver.isPrivateName(name) && plan.stackIn() > 1) {
            return "A public word's parameters must be named and typed (E_HEADER); the new word takes " + plan.stackIn()
                    + " values from the stack, so give it a private name starting with _";
        }
        return null;
    }

    /** Applies the plan: the new definition before the old word (and the comment above it), the call in place. */
    public static void perform(@NotNull Plan plan, @NotNull String name) {
        FLSelection sel = plan.selection();
        FLDefinition def = sel.definition();
        PsiFile file = def.getContainingFile();
        CharSequence text = file.getViewProvider().getContents();
        String body = reindent(sel.text(), FLRefactorings.indentOf(text, sel.range().getStartOffset()));
        String definition = ": " + name + " " + plan.header() + "\n  " + body + " ;\n\n";
        String call = String.join(" ", plan.locals().keySet());
        call = call.isEmpty() ? name : call + " " + name;
        List<FLRefactorings.Edit> edits = new ArrayList<>();
        int at = startWithLeadingComment(text, def.getTextRange().getStartOffset());
        edits.add(new FLRefactorings.Edit(at, at, definition));
        edits.add(new FLRefactorings.Edit(sel.range(), call));
        FLRefactorings.apply(file.getProject(), file, TITLE, edits);
    }

    /** Lines after the first lose the indentation of the selection's first line and gain the body's two spaces. */
    static String reindent(String code, String firstLineIndent) {
        String[] lines = code.split("\n", -1);
        StringBuilder sb = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            String rest = line.startsWith(firstLineIndent) ? line.substring(firstLineIndent.length()) : line.stripLeading();
            sb.append('\n').append(rest.isBlank() ? "" : "  " + rest);
        }
        return sb.toString();
    }

    /** The start of the comment lines directly above a definition (its documentation), or the definition's start. */
    static int startWithLeadingComment(CharSequence text, int defStart) {
        int at = defStart;
        while (true) {
            int lineEnd = at - 1;
            if (lineEnd < 0 || text.charAt(lineEnd) != '\n') return at;
            int lineStart = lineEnd;
            while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') lineStart--;
            String line = text.subSequence(lineStart, lineEnd).toString().strip();
            if (!line.startsWith("\\")) return at;
            at = lineStart;
        }
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
        String name = Messages.showInputDialog(project, "Name of the new word " + plan.header() + ":", TITLE, null,
                plan.suggestedName(), new InputValidatorEx() {
                    @Override
                    public String getErrorText(String inputString) {
                        return nameProblem(plan, inputString.strip());
                    }
                });
        if (name != null) perform(plan, name.strip());
    }

    @Override
    public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    }
}
