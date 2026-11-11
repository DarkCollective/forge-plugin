// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.diagnostics;

import com.forge.fl.FLFile;
import com.forge.fl.FLTokenTypes;
import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.index.FLIndexLookup;
import com.forge.fl.lexer.FLSyntax;
import com.forge.fl.psi.FLConstant;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLElementTypes;
import com.forge.fl.psi.FLHeader;
import com.forge.fl.psi.FLInForm;
import com.forge.fl.psi.FLMetaBlock;
import com.forge.fl.psi.FLMetaClause;
import com.forge.fl.psi.FLParamRef;
import com.forge.fl.psi.FLParameter;
import com.forge.fl.psi.FLResolver;
import com.forge.fl.psi.FLScope;
import com.forge.fl.psi.FLVariable;
import com.forge.fl.psi.FLWordRef;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The compile-time checks of the FL compiler (docs/LANGUAGE.md section 11 and
 * packages/lang/src/compiler.ts) that can be made without running anything, over one file. Every message names
 * the spec error code. The result is shared by the annotator (errors) and the two inspections (weak warnings), and
 * by the corpus test.
 *
 * <p>What is checked is deliberately only what is certain: a check that cannot be decided from the PSI and the
 * index (an incomplete header, an index that is not ready, a target of another kind) stays silent.
 */
public final class FLDiagnostics {
    public enum Severity {ERROR, WARNING, WEAK}

    public static final String E_UNDEFINED = "E_UNDEFINED";
    public static final String E_NO_VOCAB = "E_NO_VOCAB";
    public static final String E_AMBIGUOUS = "E_AMBIGUOUS";
    public static final String E_PRIVATE = "E_PRIVATE";
    public static final String E_REDEFINED = "E_REDEFINED";
    public static final String E_NAME = "E_NAME";
    public static final String E_HEADER = "E_HEADER";
    public static final String E_META = "E_META";
    public static final String E_TOPLEVEL = "E_TOPLEVEL";
    public static final String E_SYNTAX = "E_SYNTAX";
    public static final String W_SHADOW = "W_SHADOW";

    /**
     * One finding.
     *
     * @param unknownSymbol highlight in the "unknown symbol" style (a name that does not resolve)
     * @param element       the word use the finding is about (for quick fixes), if any
     * @param qualifyAs     for E_AMBIGUOUS: the candidates as {@code vocab:name}, in USING: order
     */
    public record Problem(Severity severity, String code, String message, TextRange range, boolean unknownSymbol,
                          @Nullable PsiElement element, List<String> qualifyAs) {
    }

    private FLDiagnostics() {
    }

    /** All findings for the file, ordered by position; empty for files that are not user FL sources. */
    @NotNull
    public static List<Problem> compute(@NotNull PsiFile psi) {
        if (!(psi instanceof FLFile file) || FLBuiltins.isBuiltinFile(file)) return List.of();
        Checker c = new Checker(file);
        c.run();
        c.out.sort(Comparator.comparingInt(p -> p.range().getStartOffset()));
        return c.out;
    }

    private static final class Checker {
        final FLFile file;
        final Project project;
        final FLScope scope;
        final List<Problem> out = new ArrayList<>();
        /** The index is available and knows the built-in words, so that "not found" means "not defined". */
        boolean ready;

        Checker(FLFile file) {
            this.file = file;
            this.project = file.getProject();
            this.scope = FLScope.of(file);
        }

        void add(Severity sev, String code, String message, TextRange range) {
            out.add(new Problem(sev, code, message, range, false, null, List.of()));
        }

        void run() {
            ready = FLIndexLookup.isAvailable(project) && !FLIndexLookup.filesOfVocab(project, "core").isEmpty();
            boolean prevStray = false;
            for (PsiElement child = file.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof PsiWhiteSpace || child instanceof PsiComment) continue;
                boolean stray = isStray(child);
                if (stray && !prevStray) {
                    PsiElement first = PsiTreeUtil.firstChild(child);
                    add(Severity.WEAK, E_TOPLEVEL, "Executable code outside a definition is allowed at the console, not in a source unit ("
                            + E_TOPLEVEL + ")", first.getTextRange());
                }
                prevStray = stray;
                if (child instanceof FLDefinition d) {
                    definition(d);
                } else if (child instanceof FLInForm in) {
                    inForm(in);
                } else if (child.getNode().getElementType() == FLElementTypes.USING_FORM) {
                    using(child);
                } else if (child instanceof FLMetaBlock block) {
                    meta(block);
                }
            }
            references();
            redefinitions();
        }

        // ---- top level --------------------------------------------------------------------------------

        private boolean isStray(PsiElement child) {
            if (child instanceof PsiErrorElement) return false;
            IElementType t = child.getNode().getElementType();
            return t == FLElementTypes.WORD_REF || t == FLElementTypes.QUOTATION || t == FLElementTypes.LIST_LITERAL
                    || FLTokenTypes.LITERALS.contains(t) || t == FLTokenTypes.RECURSE_KW;
        }

        private void inForm(FLInForm in) {
            PsiElement first = in.getFirstChild();
            if (first != null && first.getNode().getElementType() == FLTokenTypes.VOCAB_KW) {
                add(Severity.WEAK, E_SYNTAX, "VOCAB is only available at the console; use IN: (" + E_SYNTAX + ")",
                        first.getTextRange());
            }
            PsiElement id = in.getVocabElement();
            if (id != null && !FLSyntax.isVocabName(id.getText())) {
                add(Severity.ERROR, E_NAME, "'" + id.getText() + "' is not a valid vocabulary name (" + E_NAME + ")",
                        id.getTextRange());
            }
        }

        private void using(PsiElement form) {
            for (PsiElement leaf = form.getFirstChild(); leaf != null; leaf = leaf.getNextSibling()) {
                if (leaf.getNode().getElementType() != FLTokenTypes.WORD_NAME) continue;
                String name = leaf.getText();
                if (!FLSyntax.isVocabName(name)) {
                    add(Severity.ERROR, E_NAME, "'" + name + "' is not a valid vocabulary name (" + E_NAME + ")",
                            leaf.getTextRange());
                } else if (ready && !name.equals("core") && !vocabularyExists(name)) {
                    add(Severity.ERROR, E_NO_VOCAB, "USING: names unknown vocabulary '" + name + "' (" + E_NO_VOCAB + ")",
                            leaf.getTextRange());
                }
            }
        }

        boolean vocabularyExists(String vocab) {
            return vocab.equals("core") || vocab.equals(FLScope.DEFAULT_VOCAB) || scope.vocabularies().contains(vocab)
                    || !FLIndexLookup.filesOfVocab(project, vocab).isEmpty();
        }

        // ---- definitions ------------------------------------------------------------------------------

        private void definition(FLDefinition d) {
            FLHeader h = d.getHeader();
            if (h == null) return; // the parser reports the missing header (E_HEADER)
            if (h.getNode().findChildByType(FLTokenTypes.HDR_RPAREN) != null && !h.hasArrow()) {
                add(Severity.ERROR, E_HEADER, "Stack-effect header without '--' (" + E_HEADER + ")", h.getTextRange());
            }
            Set<String> seen = new HashSet<>();
            int defStart = d.getTextRange().getStartOffset();
            for (PsiElement c = h.getFirstChild(); c != null; c = c.getNextSibling()) {
                IElementType t = c.getNode().getElementType();
                if (c instanceof FLParameter p) {
                    PsiElement id = p.getNameIdentifier();
                    String name = p.getName();
                    if (id == null || name == null) continue;
                    if (FLSyntax.isReserved(name)) {
                        add(Severity.ERROR, E_HEADER, "'" + name + "' is a reserved word and cannot be a parameter name ("
                                + E_HEADER + ")", id.getTextRange());
                    } else if (!FLSyntax.isPname(name)) {
                        add(Severity.ERROR, E_HEADER, "'" + name + "' is not a valid parameter name: lower-case letters, "
                                + "digits and '-', starting with a letter (" + E_HEADER + ")", id.getTextRange());
                    } else if (!seen.add(name)) {
                        add(Severity.ERROR, E_HEADER, "Duplicate parameter name '" + name + "' (" + E_HEADER + ")",
                                id.getTextRange());
                    } else if (ready && FLResolver.isVisible(file, name, defStart)) {
                        add(Severity.WEAK, W_SHADOW, "Parameter '" + name + "' hides the word of the same name ("
                                + W_SHADOW + ")", id.getTextRange());
                    }
                } else if ((t == FLTokenTypes.HDR_OTHER || t == FLTokenTypes.HDR_OUTPUT)
                        && FLSyntax.isReserved(c.getText())) {
                    add(Severity.ERROR, E_HEADER, "'" + c.getText() + "' is a reserved word and cannot be a name ("
                            + E_HEADER + ")", c.getTextRange());
                }
            }
        }

        // ---- word uses ---------------------------------------------------------------------------------

        private void references() {
            if (!ready) return;
            for (FLWordRef ref : PsiTreeUtil.findChildrenOfType(file, FLWordRef.class)) {
                PsiElement parent = ref.getParent();
                // META: targets are checked with their block; see-also names need not exist (5.9)
                if (parent instanceof FLMetaBlock || parent instanceof FLMetaClause) continue;
                ResolveResult[] results = ref.getReference().multiResolve(false);
                if (results.length > 1) {
                    ambiguous(ref, results);
                } else if (results.length == 1) {
                    forwardReference(ref, results[0].getElement());
                } else {
                    unresolved(ref);
                }
            }
        }

        private void ambiguous(FLWordRef ref, ResolveResult[] results) {
            List<String> candidates = new ArrayList<>();
            for (ResolveResult r : results) {
                PsiElement e = r.getElement();
                if (e == null) continue;
                String vocab = FLScope.of(e.getContainingFile()).vocabAt(e.getTextOffset());
                String q = vocab + ":" + ref.getBareName();
                if (!candidates.contains(q)) candidates.add(q);
            }
            out.add(new Problem(Severity.ERROR, E_AMBIGUOUS, "Ambiguous word '" + ref.getText() + "': defined in "
                    + String.join(", ", candidates) + "; write vocab:" + ref.getBareName() + " (" + E_AMBIGUOUS + ")",
                    ref.getTextRange(), false, ref, candidates));
        }

        /** Docs/LANGUAGE.md 6.6: a word is visible only after its ';'. */
        private void forwardReference(FLWordRef ref, @Nullable PsiElement target) {
            if (target == null || target instanceof FLParameter) return;
            if (target.getContainingFile().getOriginalFile() != file.getOriginalFile()) return;
            PsiElement tf = topForm(target);
            PsiElement rf = topForm(ref);
            if (tf.getTextRange().getEndOffset() <= rf.getTextRange().getStartOffset()) return;
            String msg = tf == rf
                    ? "'" + ref.getText() + "' is not visible inside its own definition; use recurse to call it ("
                    + E_UNDEFINED + ")"
                    : "'" + ref.getText() + "' is used before its definition; a word is visible only after its ';' ("
                    + E_UNDEFINED + ")";
            out.add(new Problem(Severity.ERROR, E_UNDEFINED, msg, ref.getTextRange(), true, ref, List.of()));
        }

        private void unresolved(FLWordRef ref) {
            String vocab = ref.getVocab();
            String name = ref.getBareName();
            if (vocab == null) {
                out.add(new Problem(Severity.ERROR, E_UNDEFINED, "Undefined word '" + name + "' (" + E_UNDEFINED + ")",
                        ref.getTextRange(), true, ref, List.of()));
                return;
            }
            int start = ref.getTextRange().getStartOffset();
            if (!vocabularyExists(vocab)) {
                out.add(new Problem(Severity.ERROR, E_NO_VOCAB, "Unknown vocabulary '" + vocab + "' ("
                        + E_NO_VOCAB + ")", TextRange.from(start, vocab.length()), true, ref, List.of()));
            } else if (FLResolver.isPrivateName(name) && !vocab.equals(scope.vocabAt(start)) && declares(vocab, name)) {
                out.add(new Problem(Severity.ERROR, E_PRIVATE, "'" + vocab + ":" + name + "' is private to vocabulary '"
                        + vocab + "' (" + E_PRIVATE + ")", ref.getTextRange(), false, ref, List.of()));
            } else {
                out.add(new Problem(Severity.ERROR, E_UNDEFINED, "Undefined word '" + vocab + ":" + name + "' ("
                        + E_UNDEFINED + ")", ref.getTextRange(), true, ref, List.of()));
            }
        }

        private boolean declares(String vocab, String name) {
            return !scope.find(vocab, name).isEmpty()
                    || !FLIndexLookup.declarations(project, vocab, name).isEmpty();
        }

        // ---- META: -------------------------------------------------------------------------------------

        private void meta(FLMetaBlock block) {
            FLWordRef target = block.getTarget();
            if (target == null) return;
            String name = target.getBareName();
            String qname = scope.vocabAt(block.getTextRange().getStartOffset()) + ":" + name;
            PsiElement element = null;
            if (ready) {
                ResolveResult[] rs = target.getReference().multiResolve(false);
                element = rs.length == 0 ? null : rs[0].getElement();
                if (element == null) {
                    add(Severity.ERROR, E_META, "META: for undefined word '" + qname + "' (" + E_META + ")",
                            target.getTextRange());
                    return;
                }
                if (element.getContainingFile().getOriginalFile() == file.getOriginalFile()
                        && topForm(element).getTextRange().getEndOffset() > block.getTextRange().getStartOffset()) {
                    add(Severity.ERROR, E_META, "META: must follow the definition of '" + qname + "' (" + E_META + ")",
                            target.getTextRange());
                    return;
                }
            }
            if (FLResolver.isPrivateName(name)) {
                add(Severity.ERROR, E_META, "Private word '" + qname + "' may not have a META: block (" + E_META + ")",
                        target.getTextRange());
                return;
            }
            if (element == null) return;
            for (FLMetaClause clause : block.getClauses()) {
                if ("param".equals(clause.getKeyword())) {
                    FLParamRef p = PsiTreeUtil.getChildOfType(clause, FLParamRef.class);
                    if (p != null) checkParam(p, element, qname);
                }
            }
        }

        /** {@code param} must name a named input other than the first, which is the data input (meta.ts). */
        private void checkParam(FLParamRef p, PsiElement element, String qname) {
            String name = p.getText();
            if (element instanceof FLConstant || element instanceof FLVariable) {
                add(Severity.ERROR, E_META, "'" + qname + "' has no parameters (" + E_META + ")", p.getTextRange());
                return;
            }
            if (!(element instanceof FLDefinition d)) return;
            FLHeader h = d.getHeader();
            List<FLParameter> slots = h == null ? null : h.inputSlots();
            if (slots == null) return;
            for (int i = 0; i < slots.size(); i++) {
                FLParameter s = slots.get(i);
                if (s != null && name.equals(s.getName())) {
                    if (i >= 1) return;
                    add(Severity.ERROR, E_META, "'" + name + "' is the data input of '" + qname
                            + "', not a parameter (" + E_META + ")", p.getTextRange());
                    return;
                }
            }
            add(Severity.ERROR, E_META, "'" + qname + "' has no parameter '" + name + "' (" + E_META + ")",
                    p.getTextRange());
        }

        // ---- redefinition ------------------------------------------------------------------------------

        /** Docs/LANGUAGE.md 6.5: within one unit a second definition of a name in a vocabulary is an error. */
        private void redefinitions() {
            Map<String, FLScope.Decl> first = new HashMap<>();
            String text = file.getText();
            for (FLScope.Decl d : scope.all()) {
                FLScope.Decl earlier = first.putIfAbsent(d.vocab() + '\0' + d.name(), d);
                if (earlier == null) continue;
                PsiElement id = d.element().getNameIdentifier();
                if (id == null) continue;
                int line = StringUtil.offsetToLineNumber(text, earlier.element().getTextOffset()) + 1;
                add(Severity.ERROR, E_REDEFINED, "'" + d.vocab() + ":" + d.name() + "' is already defined in this file "
                        + "(line " + line + ") (" + E_REDEFINED + ")", id.getTextRange());
            }
        }

        private static PsiElement topForm(PsiElement e) {
            PsiElement top = e;
            while (top.getParent() != null && !(top.getParent() instanceof PsiFile)) top = top.getParent();
            return top;
        }
    }
}
