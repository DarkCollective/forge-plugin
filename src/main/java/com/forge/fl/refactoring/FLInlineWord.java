// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.refactoring;

import com.forge.fl.FLLanguage;
import com.forge.fl.FLTokenTypes;
import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.effects.FLCallee;
import com.forge.fl.effects.FLEffect;
import com.forge.fl.effects.FLStackAnalysis;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLElementTypes;
import com.forge.fl.psi.FLLocal;
import com.forge.fl.psi.FLMetaBlock;
import com.forge.fl.psi.FLParameter;
import com.forge.fl.psi.FLResolver;
import com.forge.fl.psi.FLScope;
import com.forge.fl.psi.FLWordRef;
import com.intellij.lang.Language;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inline word (Refactor | Inline): a call is replaced by the word's body. Named inputs are bound with {@code :>} first
 * (the top input first), and the code is wrapped in {@code [ … ] call} whenever it binds locals, so that they stay
 * scoped as they were: {@code : twice ( n:int -- int ) n 2 * ;} inlines {@code 5 twice} as
 * {@code 5 [ :> n n 2 * ] call}, and {@code : sq ( int -- int ) dup * ;} as {@code 5 dup *}. Inline all also deletes
 * the word with its comment and {@code META:} blocks once no other reference remains.
 * <p>
 * Refused: {@code MEMO:} words, bodies with {@code exit} or {@code recurse}, named inputs below anonymous ones; and per
 * call, a call in another file or vocabulary (names could resolve differently), one inside a {@code { }} literal when
 * locals are bound, and one where a local of the caller would capture a name of the body or clash with a local of it.
 */
public final class FLInlineWord extends InlineActionHandler {
    static final String TITLE = "Inline Word";

    /** The inlined code for any call, and the calls with what stops each (null when it can be inlined). */
    public record Plan(@NotNull FLDefinition definition, @NotNull String code, boolean scoped,
                       @NotNull Map<FLWordRef, String> calls, int otherReferences) {
        @NotNull
        public List<FLWordRef> inlinable() {
            return calls.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).toList();
        }
    }

    @Override
    public boolean isEnabledForLanguage(Language l) {
        return l == FLLanguage.INSTANCE;
    }

    @Override
    public boolean canInlineElement(PsiElement element) {
        return element instanceof FLDefinition def && !FLBuiltins.isBuiltin(def) && def.getName() != null;
    }

    @NotNull
    public static Plan plan(@NotNull FLDefinition def) throws FLSelection.Refused {
        if (def.isMemo()) throw new FLSelection.Refused("A MEMO: word is computed once; inlining it would change that");
        FLEffect effect = FLEffect.of(def.getHeader());
        if (effect == null || effect.variadicIn()) throw new FLSelection.Refused("The header of '" + def.getName() + "' is not understood");
        List<PsiElement> body = FLStackAnalysis.bodyItems(def);
        for (PsiElement leaf : PsiTreeUtil.collectElements(def, e -> e.getFirstChild() == null)) {
            if (leaf.getNode().getElementType() == FLTokenTypes.RECURSE_KW) throw new FLSelection.Refused("'" + def.getName() + "' uses recurse");
        }
        for (FLWordRef ref : PsiTreeUtil.findChildrenOfType(def, FLWordRef.class)) {
            FLCallee c = FLCallee.of(ref);
            if (c != null && c.isCore("exit")) throw new FLSelection.Refused("'" + def.getName() + "' uses exit, which would leave the caller");
        }
        // Named inputs are bound top first with :>; they must all be above the anonymous ones, which stay on the stack.
        List<FLEffect.Input> inputs = effect.inputs();
        boolean seenNamed = false;
        for (FLEffect.Input in : inputs) {
            if (in.name() != null) seenNamed = true;
            else if (seenNamed) throw new FLSelection.Refused("A named input of '" + def.getName() + "' is below an anonymous one");
        }
        StringBuilder code = new StringBuilder();
        for (int i = inputs.size() - 1; i >= 0 && inputs.get(i).name() != null; i--) code.append(":> ").append(inputs.get(i).name()).append(' ');
        String bodyText = "";
        if (!body.isEmpty()) {
            CharSequence text = def.getContainingFile().getViewProvider().getContents();
            bodyText = text.subSequence(body.get(0).getTextRange().getStartOffset(), body.get(body.size() - 1).getTextRange().getEndOffset()).toString();
            if (!bodyText.contains("\\")) bodyText = bodyText.replaceAll("\\s*\n\\s*", " ");
        }
        code.append(bodyText);
        boolean scoped = code.length() > bodyText.length() || body.stream().anyMatch(e -> e instanceof FLLocal);
        String inlined = scoped ? "[ " + code.toString().strip() + " ] call" : code.toString().strip();

        Set<String> bodyLocals = new LinkedHashSet<>();
        for (FLParameter p : def.getParameters()) if (p.getName() != null) bodyLocals.add(p.getName());
        for (FLLocal l : PsiTreeUtil.findChildrenOfType(def, FLLocal.class)) if (l.getName() != null) bodyLocals.add(l.getName());
        Set<String> freeNames = new LinkedHashSet<>();
        for (FLWordRef ref : PsiTreeUtil.findChildrenOfType(def, FLWordRef.class)) {
            if (ref.isQualified() || def.getHeader() != null && PsiTreeUtil.isAncestor(def.getHeader(), ref, false)) continue;
            PsiElement target = ref.getReference().resolve();
            if (!(target instanceof FLParameter || target instanceof FLLocal)) freeNames.add(ref.getBareName());
        }

        Map<FLWordRef, String> calls = new LinkedHashMap<>();
        int others = 0;
        PsiFile file = def.getContainingFile();
        String vocab = FLScope.of(file).vocabAt(def.getTextRange().getStartOffset());
        for (PsiReference r : ReferencesSearch.search(def, GlobalSearchScope.projectScope(def.getProject())).findAll()) {
            if (!(r.getElement() instanceof FLWordRef ref)) {
                others++;
                continue;
            }
            if (PsiTreeUtil.getParentOfType(ref, FLMetaBlock.class) != null) continue; // its own META: blocks go with it
            FLDefinition caller = PsiTreeUtil.getParentOfType(ref, FLDefinition.class);
            if (caller == null || caller.getHeader() == null || PsiTreeUtil.isAncestor(caller.getHeader(), ref, false)) {
                others++;
                continue;
            }
            calls.put(ref, callProblem(ref, file, vocab, scoped, bodyLocals, freeNames));
        }
        return new Plan(def, inlined, scoped, calls, others);
    }

    @Nullable
    private static String callProblem(FLWordRef ref, PsiFile file, String vocab, boolean scoped, Set<String> bodyLocals,
                                      Set<String> freeNames) {
        if (ref.getContainingFile() != file) return "it is in another file, where names may resolve differently";
        if (!vocab.equals(FLScope.of(file).vocabAt(ref.getTextRange().getStartOffset()))) {
            return "it is in another vocabulary, where names may resolve differently";
        }
        if (scoped) {
            for (PsiElement p = ref.getParent(); p != null && !(p instanceof FLDefinition); p = p.getParent()) {
                if (p.getNode().getElementType() == FLElementTypes.LIST_LITERAL) return "it is inside a { } list literal, where ':>' is not allowed";
            }
        }
        for (String n : bodyLocals) {
            if (FLResolver.findLocal(ref, n) != null) return "the caller has a local '" + n + "', which the inlined code would bind again";
        }
        for (String n : freeNames) {
            if (FLResolver.findLocal(ref, n) != null) return "the caller's local '" + n + "' would hide the word '" + n + "' of the inlined code";
        }
        return null;
    }

    /** Inlines {@code calls} (inlinable ones of the plan); deletes the word when {@code remove} and nothing else refers to it. */
    public static boolean perform(@NotNull Plan plan, @NotNull List<FLWordRef> calls, boolean remove) {
        FLDefinition def = plan.definition();
        PsiFile file = def.getContainingFile();
        CharSequence text = file.getViewProvider().getContents();
        List<FLRefactorings.Edit> edits = new ArrayList<>();
        for (FLWordRef call : calls) {
            int s = call.getTextRange().getStartOffset();
            int e = call.getTextRange().getEndOffset();
            if (plan.code().isEmpty()) {
                // nothing to put there: drop the call and one space next to it
                if (e < text.length() && text.charAt(e) == ' ') e++;
                else if (s > 0 && text.charAt(s - 1) == ' ') s--;
            }
            FLRefactorings.Edit substituted = substitution(def, call);
            edits.add(substituted != null ? substituted : new FLRefactorings.Edit(s, e, plan.code()));
        }
        boolean removed = remove && plan.otherReferences() == 0 && calls.size() == plan.calls().size();
        if (removed) {
            edits.add(deletion(text, FLExtractWord.startWithLeadingComment(text, def.getTextRange().getStartOffset()), def.getTextRange().getEndOffset()));
            for (FLMetaBlock block : PsiTreeUtil.findChildrenOfType(file, FLMetaBlock.class)) {
                if (block.getTarget() != null && block.getTarget().getReference().resolve() == def) {
                    edits.add(deletion(text, block.getTextRange().getStartOffset(), block.getTextRange().getEndOffset()));
                }
            }
        }
        FLRefactorings.apply(file.getProject(), file, TITLE, edits);
        return removed;
    }

    /**
     * The cleaner form for a call whose named-input arguments are simple values (literals, locals, constants) right
     * before it: those values are written into the body in place of the parameters, so {@code w table _word-code}
     * becomes the body with {@code w} and {@code table} in it instead of {@code [ :> table :> word ... ] call}. Null when
     * the call does not qualify (the bound form is used then).
     */
    @Nullable
    private static FLRefactorings.Edit substitution(FLDefinition def, FLWordRef call) {
        FLEffect effect = FLEffect.of(def.getHeader());
        if (effect == null) return null;
        int named = 0;
        for (FLEffect.Input in : effect.inputs()) if (in.name() != null) named++;
        if (named == 0) return null;
        FLDefinition caller = PsiTreeUtil.getParentOfType(call, FLDefinition.class);
        if (caller == null) return null;
        List<PsiElement> args = FLStackAnalysis.of(caller).argumentsOf(call);
        List<PsiElement> before = FLRefactorings.itemsBefore(call);
        int arity = effect.arity();
        if (args == null || args.size() != arity || before.size() < named) return null;
        Set<String> bodyLocals = new LinkedHashSet<>();
        for (FLLocal l : PsiTreeUtil.findChildrenOfType(def, FLLocal.class)) if (l.getName() != null) bodyLocals.add(l.getName());
        Map<FLParameter, String> values = new LinkedHashMap<>();
        for (int j = 0; j < named; j++) {
            PsiElement arg = args.get(arity - named + j);
            if (arg != before.get(before.size() - named + j) || !simpleValue(arg) || bodyLocals.contains(arg.getText())) return null;
            values.put(effect.inputs().get(arity - named + j).parameter(), arg.getText());
        }
        List<PsiElement> body = FLStackAnalysis.bodyItems(def);
        String code = "";
        if (!body.isEmpty()) {
            int start = body.get(0).getTextRange().getStartOffset();
            int end = body.get(body.size() - 1).getTextRange().getEndOffset();
            CharSequence text = def.getContainingFile().getViewProvider().getContents();
            StringBuilder sb = new StringBuilder();
            int at = start;
            List<FLWordRef> refs = new ArrayList<>(PsiTreeUtil.findChildrenOfType(def, FLWordRef.class));
            refs.sort(java.util.Comparator.comparingInt(r -> r.getTextRange().getStartOffset()));
            for (FLWordRef ref : refs) {
                int rs = ref.getTextRange().getStartOffset();
                if (rs < start || rs >= end) continue;
                PsiElement target = ref.getReference().resolve();
                if (!(target instanceof FLParameter p) || !values.containsKey(p)) continue;
                sb.append(text, at, rs).append(values.get(p));
                at = ref.getTextRange().getEndOffset();
            }
            sb.append(text, at, end);
            code = sb.toString();
            if (!code.contains("\\")) code = code.replaceAll("\\s*\n\\s*", " ");
            if (body.stream().anyMatch(e -> e instanceof FLLocal)) code = "[ " + code.strip() + " ] call";
        }
        int from = before.get(before.size() - named).getTextRange().getStartOffset();
        return new FLRefactorings.Edit(from, call.getTextRange().getEndOffset(), code.strip());
    }

    /** A literal, or a local, parameter or constant: a value that can be written in another place unchanged. */
    private static boolean simpleValue(PsiElement e) {
        if (FLTokenTypes.LITERALS.contains(e.getNode().getElementType())) return true;
        if (!(e instanceof FLWordRef ref) || ref.isQualified()) return false;
        PsiElement target = ref.getReference().resolve();
        return target instanceof FLParameter || target instanceof FLLocal || target instanceof com.forge.fl.psi.FLConstant;
    }

    /** A top-level form's text with the line break after it and one blank line. */
    private static FLRefactorings.Edit deletion(CharSequence text, int start, int end) {
        int e = end;
        if (e < text.length() && text.charAt(e) == '\n') e++;
        int blank = e;
        while (blank < text.length() && (text.charAt(blank) == ' ' || text.charAt(blank) == '\t')) blank++;
        if (blank < text.length() && text.charAt(blank) == '\n') e = blank + 1;
        return new FLRefactorings.Edit(start, e, "");
    }

    @Override
    public void inlineElement(Project project, Editor editor, PsiElement element) {
        FLDefinition def = (FLDefinition) element;
        Plan plan;
        try {
            plan = plan(def);
        } catch (FLSelection.Refused e) {
            CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), TITLE, null);
            return;
        }
        FLWordRef here = null;
        if (editor != null) {
            PsiElement at = def.getContainingFile().findElementAt(editor.getCaretModel().getOffset());
            FLWordRef ref = PsiTreeUtil.getParentOfType(at, FLWordRef.class, false);
            if (ref != null && plan.calls().containsKey(ref)) here = ref;
        }
        List<String> blocked = new ArrayList<>();
        plan.calls().forEach((ref, why) -> {
            if (why != null) blocked.add(ref.getContainingFile().getName() + ": " + why);
        });
        if (here != null && plan.calls().get(here) != null) {
            CommonRefactoringUtil.showErrorHint(project, editor, "This call cannot be inlined: " + plan.calls().get(here), TITLE, null);
            return;
        }
        int n = plan.inlinable().size();
        String message = "Inline '" + def.getName() + "' as " + FLSelection.shorten(plan.code().isEmpty() ? "nothing" : plan.code()) + "?"
                + (blocked.isEmpty() ? "" : "\n\n" + blocked.size() + " call(s) cannot be inlined and stay:\n" + String.join("\n", blocked));
        String[] options = here != null
                ? new String[]{"All " + n + " Calls and Remove the Word", "This Call Only", "Cancel"}
                : new String[]{"All " + n + " Calls and Remove the Word", "Cancel"};
        int choice = Messages.showDialog(project, message, TITLE, options, 0, Messages.getQuestionIcon());
        if (choice == 0) perform(plan, plan.inlinable(), true);
        else if (choice == 1 && here != null) perform(plan, List.of(here), false);
    }
}
