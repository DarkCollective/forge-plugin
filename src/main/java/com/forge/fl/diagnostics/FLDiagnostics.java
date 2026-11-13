// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.diagnostics;

import com.forge.fl.FLFile;
import com.forge.fl.FLTokenTypes;
import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.index.FLIndexLookup;
import com.forge.fl.lexer.FLSyntax;
import com.forge.fl.psi.FLChoiceRef;
import com.forge.fl.psi.FLChoiceType;
import com.forge.fl.psi.FLConstant;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLElementTypes;
import com.forge.fl.psi.FLHeader;
import com.forge.fl.psi.FLInForm;
import com.forge.fl.psi.FLLocal;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.psi.FLPsiElement;
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
    public static final String E_FORBIDDEN = "E_FORBIDDEN";
    public static final String W_SHADOW = "W_SHADOW";

    /** The words whose literal quotations may hold {@code exit} (docs/LANGUAGE.md 8). */
    private static final Set<String> EXIT_COMBINATORS = Set.of("if", "when", "unless", "while", "loop");
    /** {@code core} words a MEMO: body may not call (5.13): they depend on the session or print. */
    private static final Map<String, String> MEMO_FORBIDDEN = Map.of(
            "help", "state", "see", "state", "words", "state", "vocabs", "state", "current-vocab", "state",
            ".", "output", ".s", "output");
    /** The link URL grammar of docs/LANGUAGE.md 5.9. */
    private static final java.util.regex.Pattern LINK_URL = java.util.regex.Pattern.compile(
            "https://((?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)"
                    + "(?::([0-9]{1,5}))?([/?#](?:[A-Za-z0-9\\-._~:/?#@!$&'()*+,;=]|%[0-9A-Fa-f]{2})*)?");

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
                } else if (child instanceof FLChoiceType type) {
                    choiceType(type);
                }
            }
            references();
            locals();
            choiceRefs();
            exits();
            redefinitions();
            linkLimits();
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
            if (d.isMemo()) memo(d, h);
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

        /**
         * MEMO: (5.13): the header is exactly {@code ( -- T )} with T a type other than quot and any; recurse is
         * E_SYNTAX; the body may not use variables, session words or printing (E_FORBIDDEN). Effect labels
         * (+async, +random, +time) and callees are not followed: the plugin has no effect data.
         */
        private void memo(FLDefinition d, FLHeader h) {
            boolean arrow = false;
            int outputs = 0;
            boolean bad = false;
            for (PsiElement c = h.getFirstChild(); c != null; c = c.getNextSibling()) {
                IElementType t = c.getNode().getElementType();
                if (t == FLTokenTypes.HDR_LPAREN || t == FLTokenTypes.HDR_RPAREN || c instanceof PsiWhiteSpace
                        || c instanceof PsiComment) continue;
                if (t == FLTokenTypes.HDR_ARROW) {
                    arrow = true;
                } else if (!arrow) {
                    bad = true; // any input
                } else {
                    outputs++;
                    String text = c.getText();
                    String type = text.contains(":") ? text.substring(text.indexOf(':') + 1) : text;
                    if (t == FLTokenTypes.HDR_DOTS || !FLSyntax.isTypeName(type) || type.equals("quot") || type.equals("any")) {
                        bad = true;
                    }
                }
            }
            if (arrow && (bad || outputs != 1)) {
                add(Severity.ERROR, E_HEADER, "A MEMO: word has the header ( -- T ): no input and one output whose type is "
                        + "not quot or any (" + E_HEADER + ")", h.getTextRange());
            }
            for (PsiElement e : PsiTreeUtil.findChildrenOfAnyType(d, FLWordRef.class)) {
                FLWordRef ref = (FLWordRef) e;
                PsiElement target = ready ? ref.getReference().resolve() : null;
                String label = null;
                if (target instanceof FLVariable) {
                    label = "state";
                } else if (target instanceof FLDefinition def && FLBuiltins.isBuiltin(def)
                        && "core".equals(FLScope.of(def.getContainingFile()).vocabAt(def.getTextOffset()))) {
                    label = MEMO_FORBIDDEN.get(ref.getBareName());
                }
                if (label != null) {
                    add(Severity.ERROR, E_FORBIDDEN, "A MEMO: word must be pure: '" + ref.getText() + "' "
                            + (label.equals("output") ? "prints" : "depends on run or session state") + " (" + E_FORBIDDEN
                            + ")", ref.getTextRange());
                }
            }
            for (PsiElement leaf : PsiTreeUtil.collectElements(d, x -> x.getNode().getElementType() == FLTokenTypes.RECURSE_KW)) {
                add(Severity.ERROR, E_SYNTAX, "recurse is not allowed in a MEMO: word (" + E_SYNTAX + ")", leaf.getTextRange());
            }
        }

        // ---- locals (:>) -------------------------------------------------------------------------------

        /** Docs/LANGUAGE.md 7.1: where {@code :>} may be, duplicate locals, and shadowing. */
        private void locals() {
            for (FLLocal l : PsiTreeUtil.findChildrenOfType(file, FLLocal.class)) {
                PsiElement id = l.getNameIdentifier();
                PsiElement kw = l.getFirstChild();
                TextRange at = id != null ? id.getTextRange() : kw.getTextRange();
                FLDefinition def = PsiTreeUtil.getParentOfType(l, FLDefinition.class);
                if (def == null) {
                    add(Severity.ERROR, E_SYNTAX, "':>' is only allowed inside a definition (" + E_SYNTAX + ")", kw.getTextRange());
                    continue;
                }
                if (PsiTreeUtil.getParentOfType(l, FLPsiElement.class) != null && insideList(l, def)) {
                    add(Severity.ERROR, E_SYNTAX, "':>' is not allowed inside a { } list literal (" + E_SYNTAX + ")",
                            kw.getTextRange());
                    continue;
                }
                String name = l.getName();
                if (id == null || name == null) continue;
                if (!FLSyntax.isDefinableName(name)) {
                    add(Severity.ERROR, E_NAME, "'" + name + "' is not a valid local name (" + E_NAME + ")", at);
                    continue;
                }
                FLNamedElement earlier = FLResolver.findLocal(l, name);
                if (earlier != null) {
                    add(Severity.ERROR, E_REDEFINED, "Local '" + name + "' is already defined in this body ("
                            + E_REDEFINED + ")", at);
                } else if (ready && FLResolver.isVisible(file, name, def.getTextRange().getStartOffset())) {
                    add(Severity.WEAK, W_SHADOW, "Local '" + name + "' hides the word of the same name (" + W_SHADOW + ")", at);
                }
            }
        }

        private static boolean insideList(PsiElement e, PsiElement stop) {
            for (PsiElement p = e.getParent(); p != null && p != stop; p = p.getParent()) {
                if (p.getNode().getElementType() == FLElementTypes.LIST_LITERAL) return true;
            }
            return false;
        }

        // ---- exit --------------------------------------------------------------------------------------

        /**
         * Docs/LANGUAGE.md 8: {@code exit} is allowed in a word body and in a literal quotation passed to if,
         * when, unless, while or loop (nested to any depth), and not in a while/loop that contains a {@code :>}.
         * Only a use that resolves to {@code core:exit} is checked (a vocabulary may define its own exit).
         */
        private void exits() {
            if (!ready) return;
            for (FLWordRef ref : PsiTreeUtil.findChildrenOfType(file, FLWordRef.class)) {
                if (!"exit".equals(ref.getBareName())) continue;
                PsiElement target = ref.getReference().resolve();
                if (!(target instanceof FLDefinition def) || !FLBuiltins.isBuiltin(def)) continue;
                String why = exitProblem(ref);
                if (why != null) add(Severity.ERROR, E_SYNTAX, why + " (" + E_SYNTAX + ")", ref.getTextRange());
            }
        }

        @Nullable
        private String exitProblem(FLWordRef ref) {
            for (PsiElement e = ref, p = ref.getParent(); p != null; e = p, p = p.getParent()) {
                if (p instanceof FLDefinition) return null;
                if (p instanceof PsiFile) return "exit is only allowed inside a definition";
                IElementType t = p.getNode().getElementType();
                if (t == FLElementTypes.LIST_LITERAL) return "exit is not allowed inside a { } list literal";
                if (t != FLElementTypes.QUOTATION) continue;
                // the run of literal quotations that p belongs to, and the word after it
                List<PsiElement> run = new ArrayList<>();
                PsiElement first = p;
                for (PsiElement q = PsiTreeUtil.skipWhitespacesAndCommentsBackward(p); q != null
                        && q.getNode().getElementType() == FLElementTypes.QUOTATION;
                     q = PsiTreeUtil.skipWhitespacesAndCommentsBackward(q)) {
                    first = q;
                }
                PsiElement next = first;
                while (next != null && next.getNode().getElementType() == FLElementTypes.QUOTATION) {
                    run.add(next);
                    next = PsiTreeUtil.skipWhitespacesAndCommentsForward(next);
                }
                String word = next instanceof FLWordRef w && (w.getVocab() == null || "core".equals(w.getVocab()))
                        ? w.getBareName() : null;
                if (word == null || !EXIT_COMBINATORS.contains(word)) {
                    return "exit is only allowed in a word body or in a quotation passed to if, when, unless, while or loop";
                }
                if (word.equals("while") || word.equals("loop")) {
                    for (PsiElement q : run) {
                        if (PsiTreeUtil.findChildOfType(q, FLLocal.class) != null) {
                            return "exit is not allowed in a " + word + " whose quotations bind a local with ':>'";
                        }
                    }
                }
            }
            return null;
        }

        // ---- choice types ------------------------------------------------------------------------------

        /** Docs/LANGUAGE.md 5.12: the type name and its options. */
        private void choiceType(FLChoiceType type) {
            PsiElement id = type.getNameIdentifier();
            String name = type.getName();
            if (id != null && name != null && (name.indexOf('<') >= 0 || name.indexOf('>') >= 0)) {
                add(Severity.ERROR, E_NAME, "A choice type name may not contain '<' or '>' (" + E_NAME + ")", id.getTextRange());
            }
            Set<String> seen = new HashSet<>();
            int count = 0;
            for (PsiElement clause : type.getOptionClauses()) {
                PsiElement arg = FLChoiceType.argument(clause, 0);
                String option = FLChoiceType.optionOf(clause);
                if (arg == null || option == null) continue;
                count++;
                if (option.isEmpty() || option.codePointCount(0, option.length()) > 128) {
                    add(Severity.ERROR, E_META, "An option is 1 to 128 characters (" + E_META + ")", arg.getTextRange());
                } else if (!seen.add(option)) {
                    add(Severity.ERROR, E_META, "Duplicate option '" + option + "' (" + E_META + ")", arg.getTextRange());
                }
                PsiElement helpLeaf = FLChoiceType.argument(clause, 1);
                String help = FLChoiceType.helpOf(clause);
                if (helpLeaf != null && help != null && (help.codePointCount(0, help.length()) > 200 || hasControl(help))) {
                    add(Severity.ERROR, E_META, "Option help is one line of at most 200 characters (" + E_META + ")",
                            helpLeaf.getTextRange());
                }
            }
            if (count > 1024) {
                add(Severity.ERROR, E_META, "A choice type has at most 1024 options (" + E_META + ")",
                        id != null ? id.getTextRange() : type.getFirstChild().getTextRange());
            }
        }

        /** Docs/LANGUAGE.md 5.4, 6.8: {@code name:choice<type>} references and their defaults. */
        private void choiceRefs() {
            for (FLChoiceRef ref : PsiTreeUtil.findChildrenOfType(file, FLChoiceRef.class)) {
                FLParameter param = PsiTreeUtil.getParentOfType(ref, FLParameter.class);
                if (!FLSyntax.isWordName(ref.getBareName()) || ref.getText().indexOf('<') >= 0
                        || (ref.getVocab() != null && !FLSyntax.isVocabName(ref.getVocab()))) {
                    add(Severity.ERROR, E_HEADER, "Malformed choice type reference (" + E_HEADER + ")", ref.getTextRange());
                    continue;
                }
                if (param != null && !param.getText().contains(">")) {
                    add(Severity.ERROR, E_HEADER, "Unterminated choice<...> (" + E_HEADER + ")", ref.getTextRange());
                    continue;
                }
                if (!ready) continue;
                List<FLChoiceType> found = FLResolver.resolveChoiceAll(ref);
                String vocab = ref.getVocab();
                String name = ref.getBareName();
                if (found.size() > 1) {
                    List<String> qs = new ArrayList<>();
                    for (FLChoiceType t : found) qs.add(FLScope.of(t.getContainingFile()).vocabAt(t.getTextOffset()) + ":" + name);
                    add(Severity.ERROR, E_AMBIGUOUS, "Ambiguous choice type '" + name + "': defined in "
                            + String.join(", ", qs) + " (" + E_AMBIGUOUS + ")", ref.getTextRange());
                } else if (found.isEmpty()) {
                    int start = ref.getTextRange().getStartOffset();
                    if (vocab != null && !vocabularyExists(vocab)) {
                        add(Severity.ERROR, E_NO_VOCAB, "Unknown vocabulary '" + vocab + "' (" + E_NO_VOCAB + ")",
                                TextRange.from(start, vocab.length()));
                    } else if (vocab != null && FLResolver.isPrivateName(name) && !vocab.equals(scope.vocabAt(start))
                            && FLResolver.typeDeclared(file, vocab, name)) {
                        add(Severity.ERROR, E_PRIVATE, "Choice type '" + vocab + ":" + name + "' is private (" + E_PRIVATE + ")",
                                ref.getTextRange());
                    } else {
                        out.add(new Problem(Severity.ERROR, E_UNDEFINED, "Undefined choice type '" + ref.getText() + "' ("
                                + E_UNDEFINED + ")", ref.getTextRange(), true, null, List.of()));
                    }
                } else if (param != null) {
                    checkChoiceDefault(param, found.get(0));
                }
            }
        }

        /** A default after {@code choice<type>} must be one of the type's options (5.4). */
        private void checkChoiceDefault(FLParameter param, FLChoiceType type) {
            String text = param.getText();
            int gt = text.indexOf('>');
            if (gt < 0 || gt + 1 >= text.length()) return;
            String rest = text.substring(gt + 1);
            if (rest.startsWith("[")) {
                add(Severity.ERROR, E_HEADER, "A choice parameter takes no constraint (" + E_HEADER + ")", param.getTextRange());
                return;
            }
            if (!rest.startsWith("=")) return;
            String def = rest.substring(1);
            String value = def.startsWith("\"") ? FLSyntax.decodeString(def) : def;
            if (!type.getOptions().contains(value)) {
                add(Severity.ERROR, E_HEADER, "Default '" + value + "' is not an option of choice type '" + type.getName()
                        + "' " + type.getEffectText() + " (" + E_HEADER + ")", param.getTextRange());
            }
        }

        private static boolean hasControl(String s) {
            for (int i = 0; i < s.length(); i++) {
                if (s.charAt(i) < 0x20) return true;
            }
            return false;
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
            if (target == null || target instanceof FLParameter || target instanceof FLLocal) return;
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
            for (FLMetaClause clause : block.getClauses()) {
                String key = clause.getKeyword();
                if ("use-when".equals(key)) checkUseWhen(clause);
                else if ("link".equals(key)) checkLink(clause, qname);
                else if ("inverse".equals(key)) checkInverse(clause, block);
            }
            if (element == null) return;
            for (FLMetaClause clause : block.getClauses()) {
                if ("param".equals(clause.getKeyword())) {
                    FLParamRef p = PsiTreeUtil.getChildOfType(clause, FLParamRef.class);
                    if (p != null) checkParam(p, element, qname);
                }
            }
        }

        /** {@code use-when} (5.9): one line of plain text, 1 to 160 characters, not padded with white space. */
        private void checkUseWhen(FLMetaClause clause) {
            PsiElement str = stringArg(clause, 0);
            if (str == null) return;
            String v = FLSyntax.decodeString(str.getText());
            String why = v.isBlank() ? "must not be empty"
                    : hasControl(v) ? "must be one line without tabs or control characters"
                    : !v.equals(v.strip()) ? "must not start or end with white space"
                    : v.codePointCount(0, v.length()) > 160 ? "is at most 160 characters" : null;
            if (why != null) add(Severity.ERROR, E_META, "use-when text " + why + " (" + E_META + ")", str.getTextRange());
        }

        /** Links per word for the 8-link cap and duplicate URLs: {@code qname -> URLs} in file order. */
        private final Map<String, List<PsiElement>> links = new java.util.LinkedHashMap<>();

        /** {@code link} (5.9): an https URL of the grammar, at most 300 characters; a label of at most 80, one line. */
        private void checkLink(FLMetaClause clause, String qname) {
            PsiElement url = stringArg(clause, 0);
            if (url == null) return;
            String u = FLSyntax.decodeString(url.getText());
            String why = linkProblem(u);
            if (why != null) add(Severity.ERROR, E_META, "Invalid link: " + why + " (" + E_META + ")", url.getTextRange());
            List<PsiElement> list = links.computeIfAbsent(qname, k -> new ArrayList<>());
            for (PsiElement other : list) {
                if (FLSyntax.decodeString(other.getText()).equals(u)) {
                    add(Severity.ERROR, E_META, "Duplicate link '" + u + "' (" + E_META + ")", url.getTextRange());
                    break;
                }
            }
            list.add(url);
            PsiElement label = stringArg(clause, 1);
            if (label != null) {
                String l = FLSyntax.decodeString(label.getText());
                if (l.codePointCount(0, l.length()) > 80 || hasControl(l)) {
                    add(Severity.ERROR, E_META, "A link label is one line of at most 80 characters (" + E_META + ")",
                            label.getTextRange());
                }
            }
        }

        private void linkLimits() {
            for (Map.Entry<String, List<PsiElement>> e : links.entrySet()) {
                List<PsiElement> list = e.getValue();
                for (int i = 8; i < list.size(); i++) {
                    add(Severity.ERROR, E_META, "A word has at most 8 links ('" + e.getKey() + "') (" + E_META + ")",
                            list.get(i).getTextRange());
                }
            }
        }

        /** The reason a link URL breaks the grammar of docs/LANGUAGE.md 5.9, or null. */
        @Nullable
        static String linkProblem(String u) {
            if (u.codePointCount(0, u.length()) > 300) return "a URL is at most 300 characters";
            if (!u.startsWith("https://")) return "the URL must start with https://";
            java.util.regex.Matcher m = LINK_URL.matcher(u);
            if (!m.matches()) return "not a valid https URL (no user info, spaces or the characters \" < > \\ ^ ` { | } [ ])";
            String host = m.group(1);
            String last = host.substring(host.lastIndexOf('.') + 1);
            if (!last.matches(".*[A-Za-z].*")) return "the host must be a domain name (no IP address or localhost)";
            if (m.group(2) != null) {
                int port = Integer.parseInt(m.group(2));
                if (port < 1 || port > 65535) return "the port must be 1 to 65535";
            }
            return null;
        }

        /**
         * {@code inverse} (5.9): one name of the current vocabulary, defined (possibly later) in this unit or
         * already, and public.
         */
        private void checkInverse(FLMetaClause clause, FLMetaBlock block) {
            FLWordRef ref = PsiTreeUtil.getChildOfType(clause, FLWordRef.class);
            if (ref == null) return;
            String current = scope.vocabAt(block.getTextRange().getStartOffset());
            String vocab = ref.getVocab();
            String q = (vocab == null ? current : vocab) + ":" + ref.getBareName();
            if (vocab != null && !vocab.equals(current)) {
                add(Severity.ERROR, E_META, "inverse names a word of the current vocabulary '" + current
                        + "'; use see-also for another vocabulary (" + E_META + ")", ref.getTextRange());
            } else if (FLResolver.isPrivateName(ref.getBareName())) {
                add(Severity.ERROR, E_META, "The inverse '" + q + "' must be public (" + E_META + ")", ref.getTextRange());
            } else if (ready && ref.getReference().multiResolve(false).length == 0) {
                add(Severity.ERROR, E_META, "The inverse '" + q + "' is not defined (" + E_META + ")", ref.getTextRange());
            }
        }

        @Nullable
        private static PsiElement stringArg(PsiElement clause, int k) {
            int i = 0;
            for (PsiElement c = clause.getFirstChild(); c != null; c = c.getNextSibling()) {
                if (c.getNode().getElementType() == FLTokenTypes.STRING && i++ == k) return c;
            }
            return null;
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
            Map<String, FLScope.Decl> firstType = new HashMap<>();
            for (FLScope.Decl d : scope.types()) {
                FLScope.Decl earlier = firstType.putIfAbsent(d.vocab() + '\0' + d.name(), d);
                PsiElement id = d.element().getNameIdentifier();
                if (earlier == null || id == null) continue;
                int line = StringUtil.offsetToLineNumber(text, earlier.element().getTextOffset()) + 1;
                add(Severity.ERROR, E_REDEFINED, "Choice type '" + d.vocab() + ":" + d.name() + "' is already defined in "
                        + "this file (line " + line + ") (" + E_REDEFINED + ")", id.getTextRange());
            }
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
