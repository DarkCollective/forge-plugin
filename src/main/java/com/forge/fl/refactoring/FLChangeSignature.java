// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.refactoring;

import com.forge.fl.FLFile;
import com.forge.fl.FLLanguage;
import com.forge.fl.FLTokenTypes;
import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.diagnostics.FLDiagnostics;
import com.forge.fl.effects.FLCallee;
import com.forge.fl.effects.FLEffect;
import com.forge.fl.effects.FLStackAnalysis;
import com.forge.fl.psi.FLConstant;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLElementTypes;
import com.forge.fl.psi.FLLocal;
import com.forge.fl.psi.FLMetaBlock;
import com.forge.fl.psi.FLMetaClause;
import com.forge.fl.psi.FLParamRef;
import com.forge.fl.psi.FLParameter;
import com.forge.fl.psi.FLScope;
import com.forge.fl.psi.FLWordRef;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Change signature of a colon word: its inputs (renamed, reordered, added, removed) and its outputs. Updates the
 * header, the uses of renamed parameters in the body, the {@code META: param} clauses (renamed, removed, reordered to
 * header order), and the calls: where the arguments that move are simple values right before the call (literals,
 * quotations, list literals, locals, constants), they are rewritten in the new order, a new input gets its value at
 * calls, and a removed input's argument goes away.
 * <p>
 * Anonymous inputs stay on the stack in the body, so they cannot be added, removed or reordered, and an input cannot
 * change between named and anonymous. A removed parameter must not be used in the body.
 */
public final class FLChangeSignature {
    public static final String TITLE = "Change Signature";

    /** One input of the new header: its old position (-1 for a new one), its header item, its value at calls (new ones). */
    public record Row(int oldIndex, @NotNull String item, @Nullable String callValue) {
    }

    /** The edits per file, the calls left unchanged with why, and the errors that stop the refactoring. */
    public record Plan(@NotNull Map<PsiFile, List<FLRefactorings.Edit>> edits, @NotNull List<String> skippedCalls,
                       @NotNull List<String> errors) {
    }

    /** The current inputs as rows. */
    @NotNull
    public static List<Row> rows(@NotNull FLDefinition def) {
        FLEffect e = FLEffect.of(def.getHeader());
        List<Row> out = new ArrayList<>();
        if (e == null) return out;
        for (int i = 0; i < e.inputs().size(); i++) out.add(new Row(i, e.inputs().get(i).text(), null));
        return out;
    }

    /** The current outputs, as written after {@code --}. */
    @NotNull
    public static String outputs(@NotNull FLDefinition def) {
        FLEffect e = FLEffect.of(def.getHeader());
        if (e == null) return "";
        return e.uncheckedOut() ? ".." : String.join(" ", e.outputs());
    }

    /** {@code ( items -- outputs )}. */
    @NotNull
    public static String header(@NotNull List<Row> rows, @NotNull String outputs) {
        StringBuilder sb = new StringBuilder("(");
        for (Row r : rows) sb.append(' ').append(r.item().strip());
        sb.append(" --");
        if (!outputs.isBlank()) sb.append(' ').append(outputs.strip());
        return sb.append(" )").toString();
    }

    @NotNull
    public static Plan plan(@NotNull FLDefinition def, @NotNull List<Row> rows, @NotNull String outputs) {
        List<String> errors = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<PsiFile, List<FLRefactorings.Edit>> edits = new LinkedHashMap<>();
        FLEffect old = FLEffect.of(def.getHeader());
        if (old == null || old.variadicIn() || FLBuiltins.isBuiltin(def)) {
            errors.add("The header of '" + def.getName() + "' cannot be changed here");
            return new Plan(edits, skipped, errors);
        }
        String newHeader = header(rows, outputs);
        FLEffect neu = parse(def, newHeader, errors);
        if (neu == null) return new Plan(edits, skipped, errors);

        // Kinds and anonymous inputs
        List<Integer> oldAnon = new ArrayList<>();
        for (int i = 0; i < old.arity(); i++) if (old.inputs().get(i).name() == null) oldAnon.add(i);
        List<Integer> newAnon = new ArrayList<>();
        Map<Integer, String> newNameOfOld = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            String newName = neu.inputs().get(i).name();
            if (r.oldIndex() < 0) {
                if (newName == null) errors.add("A new input must be named: anonymous inputs stay on the stack in the body");
                if (r.callValue() == null || r.callValue().isBlank()) errors.add("Give the new input '" + r.item() + "' a value at calls");
                continue;
            }
            String oldName = old.inputs().get(r.oldIndex()).name();
            if ((oldName == null) != (newName == null)) {
                errors.add("'" + old.inputs().get(r.oldIndex()).text() + "' cannot change between named and anonymous");
            }
            if (newName == null) newAnon.add(r.oldIndex());
            else newNameOfOld.put(r.oldIndex(), newName);
        }
        if (!newAnon.equals(oldAnon)) {
            errors.add("Anonymous inputs stay on the stack in the body: they cannot be added, removed or reordered");
        }

        // Removed and renamed parameters
        Map<FLParameter, String> renames = new LinkedHashMap<>();
        Set<FLParameter> removed = new HashSet<>();
        for (int i = 0; i < old.arity(); i++) {
            FLParameter p = old.inputs().get(i).parameter();
            if (p == null) continue;
            String newName = newNameOfOld.get(i);
            if (newName == null) {
                removed.add(p);
                if (usedInBody(def, p)) errors.add("The parameter '" + p.getName() + "' is used in the body and cannot be removed");
            } else if (!newName.equals(p.getName())) {
                renames.put(p, newName);
                for (FLLocal l : PsiTreeUtil.findChildrenOfType(def, FLLocal.class)) {
                    if (newName.equals(l.getName())) errors.add("'" + newName + "' is already a local of the body (E_REDEFINED)");
                }
            }
        }
        if (!errors.isEmpty()) return new Plan(edits, skipped, errors);

        PsiFile file = def.getContainingFile();
        add(edits, file, new FLRefactorings.Edit(def.getHeader().getTextRange(), newHeader));
        for (FLWordRef ref : PsiTreeUtil.findChildrenOfType(def, FLWordRef.class)) {
            PsiElement target = ref.getReference().resolve();
            if (target instanceof FLParameter p && renames.containsKey(p)) add(edits, file, new FLRefactorings.Edit(ref.getTextRange(), renames.get(p)));
        }
        metaClauses(def, rows, neu, renames, removed, edits);
        calls(def, old, rows, edits, skipped);
        return new Plan(edits, skipped, errors);
    }

    /** The new header parsed in a copy of the word (same vocabulary and name, so the public-word rules apply). */
    @Nullable
    private static FLEffect parse(FLDefinition def, String header, List<String> errors) {
        String vocab = FLScope.of(def.getContainingFile()).vocabAt(def.getTextRange().getStartOffset());
        String text = "IN: " + vocab + "\n" + def.getKeywordText() + " " + def.getName() + " " + header + " ;\n";
        PsiFile copy = PsiFileFactory.getInstance(def.getProject()).createFileFromText("signature.fth", FLLanguage.INSTANCE, text);
        FLDefinition d = PsiTreeUtil.findChildOfType(copy, FLDefinition.class);
        FLEffect e = d == null ? null : FLEffect.of(d.getHeader());
        if (copy instanceof FLFile) {
            for (FLDiagnostics.Problem p : FLDiagnostics.compute(copy)) {
                if ("E_HEADER".equals(p.code()) || "E_NAME".equals(p.code())) errors.add(p.message());
            }
        }
        if (e == null && errors.isEmpty()) errors.add("'" + header + "' is not a valid header");
        return errors.isEmpty() ? e : null;
    }

    private static boolean usedInBody(FLDefinition def, FLParameter p) {
        for (FLWordRef ref : PsiTreeUtil.findChildrenOfType(def, FLWordRef.class)) {
            if (ref.getReference().resolve() == p) return true;
        }
        return false;
    }

    /** The param clauses of the word's META: blocks: renamed, removed (with their line), reordered to header order. */
    private static void metaClauses(FLDefinition def, List<Row> rows, FLEffect neu, Map<FLParameter, String> renames,
                                    Set<FLParameter> removed, Map<PsiFile, List<FLRefactorings.Edit>> edits) {
        List<String> order = new ArrayList<>();
        for (FLEffect.Input in : neu.inputs()) if (in.name() != null) order.add(in.name());
        for (PsiReference r : ReferencesSearch.search(def, GlobalSearchScope.projectScope(def.getProject())).findAll()) {
            if (!(r.getElement() instanceof FLWordRef target)) continue;
            if (!(target.getParent() instanceof FLMetaBlock block) || block.getTarget() != target) continue;
            PsiFile file = block.getContainingFile();
            CharSequence text = file.getViewProvider().getContents();
            List<FLMetaClause> kept = new ArrayList<>();
            List<String> keptText = new ArrayList<>();
            List<String> keptName = new ArrayList<>();
            for (FLMetaClause clause : block.getClauses()) {
                if (!"param".equals(clause.getKeyword())) continue;
                FLParamRef ref = PsiTreeUtil.findChildOfType(clause, FLParamRef.class);
                PsiElement p = ref == null || ref.getReference() == null ? null : ref.getReference().resolve();
                if (p instanceof FLParameter param && removed.contains(param)) {
                    add(edits, file, lineDeletion(text, clause.getTextRange()));
                    continue;
                }
                String clauseText = clause.getText();
                String name = ref == null ? null : ref.getText();
                if (p instanceof FLParameter param && renames.containsKey(param)) {
                    int at = ref.getTextRange().getStartOffset() - clause.getTextRange().getStartOffset();
                    clauseText = clauseText.substring(0, at) + renames.get(param) + clauseText.substring(at + ref.getTextLength());
                    name = renames.get(param);
                }
                kept.add(clause);
                keptText.add(clauseText);
                keptName.add(name);
            }
            // Slot i of the kept clauses gets the clause whose name is i-th in header order.
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < kept.size(); i++) idx.add(i);
            idx.sort(Comparator.comparingInt(i -> {
                int k = order.indexOf(keptName.get(i));
                return k < 0 ? Integer.MAX_VALUE : k;
            }));
            for (int slot = 0; slot < kept.size(); slot++) {
                String t = keptText.get(idx.get(slot));
                if (!t.equals(kept.get(slot).getText())) add(edits, file, new FLRefactorings.Edit(kept.get(slot).getTextRange(), t));
            }
        }
    }

    /** A clause alone on its line goes with the line; otherwise just its text. */
    private static FLRefactorings.Edit lineDeletion(CharSequence text, TextRange r) {
        int s = r.getStartOffset();
        int e = r.getEndOffset();
        int ls = s;
        while (ls > 0 && (text.charAt(ls - 1) == ' ' || text.charAt(ls - 1) == '\t')) ls--;
        int le = e;
        while (le < text.length() && (text.charAt(le) == ' ' || text.charAt(le) == '\t')) le++;
        boolean alone = (ls == 0 || text.charAt(ls - 1) == '\n') && (le == text.length() || text.charAt(le) == '\n');
        return alone ? new FLRefactorings.Edit(ls, Math.min(le + 1, text.length()), "") : new FLRefactorings.Edit(s, e, "");
    }

    /** Rewrites the arguments of every call from the first input whose position changes to the top. */
    private static void calls(FLDefinition def, FLEffect old, List<Row> rows, Map<PsiFile, List<FLRefactorings.Edit>> edits, List<String> skipped) {
        int k = 0;
        while (k < rows.size() && k < old.arity() && rows.get(k).oldIndex() == k) k++;
        if (k == rows.size() && k == old.arity()) return; // only names, types or outputs changed
        for (PsiReference r : ReferencesSearch.search(def, GlobalSearchScope.projectScope(def.getProject())).findAll()) {
            if (!(r.getElement() instanceof FLWordRef call)) continue;
            if (PsiTreeUtil.getParentOfType(call, FLMetaBlock.class) != null) continue;
            PsiFile file = call.getContainingFile();
            String where = file.getName() + ":" + (lineOf(call) + 1);
            FLDefinition caller = PsiTreeUtil.getParentOfType(call, FLDefinition.class);
            if (caller == null || PsiTreeUtil.isAncestor(caller.getHeader(), call, false)) {
                skipped.add(where + ": not a call in a word body");
                continue;
            }
            List<PsiElement> args = FLStackAnalysis.of(caller).argumentsOf(call);
            if (args == null || args.size() != old.arity()) {
                skipped.add(where + ": the arguments of this call cannot be followed");
                continue;
            }
            List<PsiElement> items = siblingsBefore(call);
            int moved = old.arity() - k;
            boolean simple = items.size() >= moved;
            for (int j = 0; simple && j < moved; j++) {
                PsiElement arg = args.get(k + j);
                simple = arg == items.get(items.size() - moved + j) && movable(arg);
            }
            if (!simple) {
                skipped.add(where + ": the arguments that move are not simple values right before the call");
                continue;
            }
            List<String> parts = new ArrayList<>();
            for (int i = k; i < rows.size(); i++) {
                Row row = rows.get(i);
                parts.add(row.oldIndex() < 0 ? Objects.requireNonNull(row.callValue()).strip() : args.get(row.oldIndex()).getText());
            }
            int start = moved > 0 ? items.get(items.size() - moved).getTextRange().getStartOffset() : call.getTextRange().getStartOffset();
            String text = String.join(" ", parts);
            add(edits, file, new FLRefactorings.Edit(start, call.getTextRange().getStartOffset(), text.isEmpty() ? "" : text + " "));
        }
    }

    /** The items of the call's body or quotation before it, in order. */
    private static List<PsiElement> siblingsBefore(FLWordRef call) {
        List<PsiElement> out = new ArrayList<>();
        PsiElement container = call.getParent();
        List<PsiElement> all = container instanceof FLDefinition d ? FLStackAnalysis.bodyItems(d) : FLSelection.quotationItems(container);
        for (PsiElement e : all) {
            if (e == call) break;
            out.add(e);
        }
        return out;
    }

    /** A value that can be moved without changing what runs: a literal, a quotation, a list literal, a local, a constant. */
    private static boolean movable(PsiElement e) {
        var t = e.getNode().getElementType();
        if (FLTokenTypes.LITERALS.contains(t) || t == FLElementTypes.QUOTATION || t == FLElementTypes.LIST_LITERAL) return true;
        if (e instanceof FLWordRef ref) {
            PsiElement target = ref.getReference().resolve();
            return target instanceof FLParameter || target instanceof FLLocal || target instanceof FLConstant;
        }
        return false;
    }

    private static int lineOf(PsiElement e) {
        Document doc = PsiDocumentManager.getInstance(e.getProject()).getDocument(e.getContainingFile());
        return doc == null ? 0 : doc.getLineNumber(e.getTextRange().getStartOffset());
    }

    private static void add(Map<PsiFile, List<FLRefactorings.Edit>> edits, PsiFile file, FLRefactorings.Edit e) {
        edits.computeIfAbsent(file, f -> new ArrayList<>()).add(e);
    }

    /** Applies the plan's edits, all files in one command. */
    public static void perform(@NotNull Project project, @NotNull Plan plan) {
        WriteCommandAction.runWriteCommandAction(project, TITLE, null, () -> {
            for (var entry : plan.edits().entrySet()) {
                Document doc = PsiDocumentManager.getInstance(project).getDocument(entry.getKey());
                if (doc == null) continue;
                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(doc);
                List<FLRefactorings.Edit> sorted = new ArrayList<>(entry.getValue());
                sorted.sort(Comparator.comparingInt(FLRefactorings.Edit::start).reversed());
                for (FLRefactorings.Edit e : sorted) doc.replaceString(e.start(), e.end(), e.text());
                PsiDocumentManager.getInstance(project).commitDocument(doc);
            }
        }, plan.edits().keySet().toArray(PsiFile[]::new));
    }

    /** The word a change signature acts on: a definition, or what a word use resolves to; null otherwise. */
    @Nullable
    public static FLDefinition target(@Nullable PsiElement e) {
        if (e == null) return null;
        FLDefinition def = PsiTreeUtil.getParentOfType(e, FLDefinition.class, false);
        FLWordRef ref = PsiTreeUtil.getParentOfType(e, FLWordRef.class, false);
        if (ref != null) {
            PsiElement t = ref.getReference().resolve();
            if (t instanceof FLParameter || t instanceof FLLocal) {
                // a local: the word it belongs to
            } else {
                FLCallee c = FLCallee.of(ref);
                def = c == null ? null : c.definition();
            }
        }
        return def == null || FLBuiltins.isBuiltin(def) ? null : def;
    }
}
