// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.effects;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLElementTypes;
import com.forge.fl.psi.FLHeader;
import com.forge.fl.psi.FLWordRef;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stack effect inference for one colon or {@code MEMO:} body (docs/LANGUAGE.md 7, 8, 10.5). The body starts with the
 * header's anonymous inputs on the stack (named inputs are locals) and must end with as many values as the header
 * has outputs. Literals, locals, {@code [ ]} and {@code { }} push one value, {@code :>} pops one, a call pops its
 * header's inputs and pushes its outputs. The variadic {@code core} words are followed where their quotations are
 * literal: {@code if}, {@code when}, {@code unless}, {@code call} and {@code try} run them inline, {@code exit} checks
 * the exit depth, {@code .s} keeps the stack and {@code clear} empties it. The quotation of an iterating combinator
 * ({@code each}, {@code map}, {@code while}, ...) is checked against the depth rule of section 8.
 * <p>
 * The analysis is deliberately partial: anything it cannot follow (a word that does not resolve to one declaration
 * with a header, a quotation that is not literal, {@code cond}, {@code case}, a malformed token) ends the analysis of
 * the rest of that body, so it never reports what it cannot be sure of. Each value on the abstract stack remembers
 * the element that pushed it, which gives the arguments of every call.
 */
public final class FLStackAnalysis {
    /** A problem: the element to mark and the message (with the error the engine would raise). */
    public record Problem(@NotNull PsiElement at, @NotNull String message) {
    }

    /** A value used as input {@code index} of {@code call}, whose callee is {@code callee}. */
    public record Use(@NotNull PsiElement call, int index, @NotNull FLCallee callee) {
    }

    /** The depth the quotation of an iterating combinator starts with above d, and the depth it must leave (section 8). */
    private record LoopRule(int pushed, int leaves) {
    }

    private static final Map<String, LoopRule> LOOP_RULES = Map.ofEntries(
            Map.entry("each", new LoopRule(1, 0)), Map.entry("map", new LoopRule(1, 1)),
            Map.entry("mapi", new LoopRule(2, 1)), Map.entry("filter", new LoopRule(1, 1)),
            Map.entry("reduce", new LoopRule(2, 1)), Map.entry("times", new LoopRule(0, 0)),
            Map.entry("loop", new LoopRule(0, 1)), Map.entry("sort-by", new LoopRule(1, 1)),
            Map.entry("find", new LoopRule(1, 1)), Map.entry("any?", new LoopRule(1, 1)),
            Map.entry("all?", new LoopRule(1, 1)), Map.entry("collect", new LoopRule(0, 0)),
            Map.entry("collect-bytes", new LoopRule(0, 0)), Map.entry("collect-str", new LoopRule(0, 0)),
            Map.entry("fork", new LoopRule(1, 1)));

    /** Values assumed below a quotation checked on its own: more than any quotation takes. */
    private static final int PHANTOMS = 10_000;

    private final List<Problem> problems;
    private final Map<PsiElement, Integer> depthAfter;
    private final Map<PsiElement, Use> uses;
    private final Map<PsiElement, List<PsiElement>> arguments;
    private final @Nullable FLEffect effect;
    private final boolean alwaysFails;

    private FLStackAnalysis(List<Problem> problems, Map<PsiElement, Integer> depthAfter, Map<PsiElement, Use> uses,
                            Map<PsiElement, List<PsiElement>> arguments, @Nullable FLEffect effect, boolean alwaysFails) {
        this.problems = problems;
        this.depthAfter = depthAfter;
        this.uses = uses;
        this.arguments = arguments;
        this.effect = effect;
        this.alwaysFails = alwaysFails;
    }

    /** The core failure words (docs/LANGUAGE.md 13): they raise and never return. */
    private static final java.util.Set<String> FAILURE_WORDS = java.util.Set.of("fail", "fail-coerce", "fail-range");

    /**
     * Whether a call of {@code def} never returns: a core failure word, or a word every path of whose body raises.
     * A cycle of words is taken to return.
     */
    public static boolean neverReturns(@NotNull FLCallee callee) {
        FLDefinition def = callee.definition();
        if (def == null || callee.qname() == null) return false;
        if (callee.qname().startsWith("core:")) return FAILURE_WORDS.contains(callee.qname().substring(5)) && callee.isCore(callee.qname().substring(5));
        if (com.forge.fl.builtins.FLBuiltins.isBuiltin(def)) return false;
        Boolean fails = com.intellij.openapi.util.RecursionManager.doPreventingRecursion(def, false, () -> of(def).alwaysFails);
        return Boolean.TRUE.equals(fails);
    }

    /** The analysis of a definition, cached until any PSI changes (callees may be in other files). */
    @NotNull
    public static FLStackAnalysis of(@NotNull FLDefinition def) {
        return CachedValuesManager.getCachedValue(def, () ->
                CachedValueProvider.Result.create(compute(def), PsiModificationTracker.MODIFICATION_COUNT));
    }

    /** The problems found, in body order. */
    @NotNull
    public List<Problem> problems() {
        return problems;
    }

    /** The depth after a body item (or an item of a quotation run inline), where the analysis knows it. */
    @Nullable
    public Integer depthAfter(@NotNull PsiElement item) {
        return depthAfter.get(item);
    }

    @NotNull
    public Map<PsiElement, Integer> depths() {
        return Collections.unmodifiableMap(depthAfter);
    }

    /** How the value an element pushed is used: as which input of which call; null when unknown. */
    @Nullable
    public Use useOf(@NotNull PsiElement producer) {
        return uses.get(producer);
    }

    /** The elements that pushed the inputs of a call, deepest first (null where unknown); null for an unknown call. */
    @Nullable
    public List<PsiElement> argumentsOf(@NotNull PsiElement call) {
        return arguments.get(call);
    }

    @Nullable
    public FLEffect effect() {
        return effect;
    }

    private static FLStackAnalysis compute(FLDefinition def) {
        FLHeader header = def.getHeader();
        FLEffect effect = FLEffect.of(header);
        if (effect == null || effect.variadicIn()) {
            return new FLStackAnalysis(List.of(), Map.of(), Map.of(), Map.of(), effect, false);
        }
        Analyzer a = new Analyzer(def, effect);
        List<PsiElement> start = new ArrayList<>();
        for (int i = 0; i < effect.anonymousInputs(); i++) start.add(null);
        State end = a.run(bodyItems(def), new State(start));
        if (end != null && !end.terminal()) a.checkExit(endMarker(def), end, false);
        return new FLStackAnalysis(List.copyOf(a.problems.values()), a.depthAfter, a.uses, a.arguments, effect,
                end == State.FAILED);
    }

    /**
     * The stack effect of a run of body items on their own: how many values below them they take and how many they
     * leave, or null when the analysis cannot follow them or they end the word ({@code exit}, a failure). Locals are
     * those of {@code def}.
     */
    @Nullable
    public static int[] effectOf(@NotNull FLDefinition def, @NotNull List<PsiElement> items) {
        FLEffect effect = FLEffect.of(def.getHeader());
        if (effect == null) return null;
        Analyzer a = new Analyzer(def, effect);
        a.relative++;
        a.minDepth = PHANTOMS;
        State end = a.run(items, new State(new ArrayList<>(Collections.nCopies(PHANTOMS, null))));
        if (end == null || end.terminal()) return null;
        return new int[]{PHANTOMS - a.minDepth, end.depth() - a.minDepth};
    }

    /** The items of a definition body: after the header and the declarations, before the {@code ;}. */
    @NotNull
    public static List<PsiElement> bodyItems(@NotNull FLDefinition def) {
        List<PsiElement> out = new ArrayList<>();
        boolean inBody = false;
        for (PsiElement c = def.getFirstChild(); c != null; c = c.getNextSibling()) {
            IElementType t = c.getNode().getElementType();
            if (c instanceof FLHeader) {
                inBody = true;
                continue;
            }
            if (!inBody || c instanceof PsiWhiteSpace || c instanceof PsiComment || t == FLElementTypes.DOC_DECL) continue;
            if (t == FLTokenTypes.SEMI) break;
            out.add(c);
        }
        return out;
    }

    /** The items of a quotation, between its brackets. */
    @NotNull
    static List<PsiElement> quotationItems(@NotNull PsiElement quotation) {
        List<PsiElement> out = new ArrayList<>();
        for (PsiElement c = quotation.getFirstChild(); c != null; c = c.getNextSibling()) {
            IElementType t = c.getNode().getElementType();
            if (c instanceof PsiWhiteSpace || c instanceof PsiComment || t == FLTokenTypes.LBRACKET || t == FLTokenTypes.RBRACKET) continue;
            out.add(c);
        }
        return out;
    }

    private static PsiElement endMarker(FLDefinition def) {
        PsiElement semi = def.getNode().findChildByType(FLTokenTypes.SEMI) == null ? null
                : def.getNode().findChildByType(FLTokenTypes.SEMI).getPsi();
        if (semi != null) return semi;
        PsiElement name = def.getNameIdentifier();
        return name != null ? name : def;
    }

    static boolean isQuotation(@Nullable PsiElement e) {
        return e != null && e.getNode().getElementType() == FLElementTypes.QUOTATION;
    }

    /** The abstract data stack above the frame's floor: the element that pushed each value, deepest first. */
    private static final class State {
        /** A path that left the word by {@code exit}: it reaches no later item, and the word returns. */
        static final State EXITED = new State(List.of());
        /** A path that raised ({@code fail}, a word that never returns): it reaches no later item. */
        static final State FAILED = new State(List.of());

        boolean terminal() {
            return this == EXITED || this == FAILED;
        }

        final List<PsiElement> stack;

        State(List<PsiElement> stack) {
            this.stack = new ArrayList<>(stack);
        }

        State copy() {
            return new State(stack);
        }

        int depth() {
            return stack.size();
        }
    }

    private static final class Analyzer {
        final FLDefinition def;
        final FLEffect effect;
        final Map<String, Problem> problems = new LinkedHashMap<>();
        final Map<PsiElement, Integer> depthAfter = new HashMap<>();
        final Map<PsiElement, Use> uses = new HashMap<>();
        final Map<PsiElement, List<PsiElement>> arguments = new HashMap<>();
        /** Inside a quotation checked on its own (phantom values below): no depths, no exit checks. */
        int relative;
        /** Inside the body or handler of a {@code try}: the floor is the try's, so {@code clear} is not followed. */
        int inTry;
        /** The lowest depth reached (for the effect of a selection, analysed above phantom values). */
        int minDepth = Integer.MAX_VALUE;

        void popped(State s) {
            minDepth = Math.min(minDepth, s.depth());
        }

        Analyzer(FLDefinition def, FLEffect effect) {
            this.def = def;
            this.effect = effect;
        }

        void problem(PsiElement at, String message) {
            problems.putIfAbsent(at.getTextRange().getStartOffset() + "\0" + message, new Problem(at, message));
        }

        /** Runs items; returns the state after them, {@link State#DIVERGED}, or null when the analysis stops. */
        @Nullable
        State run(List<PsiElement> items, State s) {
            for (PsiElement item : items) {
                s = step(item, s);
                if (s == null || s.terminal()) return s;
                if (relative == 0) depthAfter.put(item, s.depth());
            }
            return s;
        }

        @Nullable
        State step(PsiElement item, State s) {
            IElementType t = item.getNode().getElementType();
            if (FLTokenTypes.LITERALS.contains(t) || t == FLElementTypes.QUOTATION || t == FLElementTypes.LIST_LITERAL) {
                s.stack.add(item);
                return s;
            }
            if (t == FLElementTypes.LOCAL_BIND) {
                if (s.depth() < 1) {
                    problem(item, "E_STACK_UNDERFLOW: ':>' binds the top of the stack, but the stack is empty here");
                    return null;
                }
                s.stack.remove(s.depth() - 1);
                popped(s);
                return s;
            }
            if (t == FLTokenTypes.RECURSE_KW) {
                FLCallee self = FLCallee.ofDefinition(def);
                return self == null ? null : apply(item, "recurse", self, s);
            }
            if (item instanceof FLWordRef ref) {
                FLCallee callee = FLCallee.of(ref);
                if (callee == null) return null;
                return call(ref, callee, s);
            }
            return null;
        }

        @Nullable
        State call(FLWordRef ref, FLCallee callee, State s) {
            String name = ref.getBareName();
            if (callee.definition() != null && callee.qname() != null && callee.qname().startsWith("core:")) {
                String core = callee.qname().substring(5);
                if (callee.isCore(core)) {
                    switch (core) {
                        case "if":
                            return branches(ref, callee, s, 2);
                        case "when":
                        case "unless":
                            return branches(ref, callee, s, 1);
                        case "call":
                            return callQuotation(ref, callee, s);
                        case "try":
                            return tryCatch(ref, callee, s);
                        case "exit":
                            if (relative > 0) return null;
                            checkExit(ref, s, true);
                            return State.EXITED;
                        case ".s":
                            return s;
                        case "clear":
                            if (inTry > 0 || relative > 0) return null;
                            s.stack.clear();
                            return s;
                        case "cond":
                        case "case":
                            return null;
                        default:
                            if (core.equals("while")) checkWhile(ref, s);
                            else if (LOOP_RULES.containsKey(core)) checkLoop(ref, core, LOOP_RULES.get(core), s);
                    }
                }
            }
            if (neverReturns(callee)) {
                if (!takeInputs(ref, name, callee, s)) return null;
                return State.FAILED;
            }
            return apply(ref, name, callee, s);
        }

        /** Pops a call's inputs (recording who pushed them) and pushes its outputs. */
        @Nullable
        State apply(PsiElement at, String name, FLCallee callee, State s) {
            FLEffect e = callee.effect();
            if (e.variadicIn()) return null;
            if (!takeInputs(at, name, callee, s)) return null;
            if (e.uncheckedOut()) return null;
            for (int i = 0; i < e.outputs().size(); i++) s.stack.add(at);
            return s;
        }

        /** Records and pops the inputs of a call; false (after a problem) when there are not enough values. */
        boolean takeInputs(PsiElement at, String name, FLCallee callee, State s) {
            int n = callee.effect().arity();
            if (s.depth() < n) {
                problem(at, "E_STACK_UNDERFLOW: '" + name + "' takes " + values(n) + ", but the stack holds "
                        + (s.depth() == 0 ? "none" : "only " + s.depth()) + " here");
                return false;
            }
            List<PsiElement> args = new ArrayList<>(s.stack.subList(s.depth() - n, s.depth()));
            for (int k = 0; k < n; k++) {
                PsiElement p = args.get(k);
                if (p != null && p != at) uses.put(p, new Use(at, k, callee));
            }
            arguments.put(at, Collections.unmodifiableList(args));
            for (int k = 0; k < n; k++) s.stack.remove(s.depth() - 1);
            popped(s);
            return true;
        }

        /** {@code if} (2 quotations) or {@code when}/{@code unless} (1): literal quotations run inline, then merge. */
        @Nullable
        State branches(FLWordRef ref, FLCallee callee, State s, int quotations) {
            if (s.depth() < callee.effect().arity()) return apply(ref, ref.getBareName(), callee, s);
            List<PsiElement> qs = new ArrayList<>(s.stack.subList(s.depth() - quotations, s.depth()));
            if (!qs.stream().allMatch(FLStackAnalysis::isQuotation)) return null;
            if (!takeInputs(ref, ref.getBareName(), callee, s)) return null;
            State first = run(quotationItems(qs.get(0)), s.copy());
            State second = quotations == 2 ? run(quotationItems(qs.get(1)), s.copy()) : s;
            return merge(ref, first, second);
        }

        @Nullable
        State callQuotation(FLWordRef ref, FLCallee callee, State s) {
            if (s.depth() < 1 || !isQuotation(s.stack.get(s.depth() - 1))) return s.depth() < 1 ? apply(ref, "call", callee, s) : null;
            PsiElement q = s.stack.get(s.depth() - 1);
            if (!takeInputs(ref, "call", callee, s)) return null;
            return run(quotationItems(q), s);
        }

        /** {@code try}: the body from the snapshot, or the handler from the snapshot plus the error record. */
        @Nullable
        State tryCatch(FLWordRef ref, FLCallee callee, State s) {
            if (s.depth() < 2) return apply(ref, "try", callee, s);
            PsiElement body = s.stack.get(s.depth() - 2);
            PsiElement handler = s.stack.get(s.depth() - 1);
            if (!isQuotation(body) || !isQuotation(handler)) return null;
            if (!takeInputs(ref, "try", callee, s)) return null;
            inTry++;
            try {
                State done = run(quotationItems(body), s.copy());
                State caught = s.copy();
                caught.stack.add(ref);
                caught = run(quotationItems(handler), caught);
                return merge(ref, done, caught);
            } finally {
                inTry--;
            }
        }

        @Nullable
        State merge(FLWordRef ref, @Nullable State a, @Nullable State b) {
            if (a == null || b == null) return null;
            if (a.terminal() && b.terminal()) return a == State.EXITED || b == State.EXITED ? State.EXITED : State.FAILED;
            if (a.terminal()) return b;
            if (b.terminal()) return a;
            if (a.depth() != b.depth()) {
                if (!effect.uncheckedOut() && relative == 0) {
                    problem(ref, "E_EFFECT: the two ways through '" + ref.getBareName() + "' leave "
                            + a.depth() + " and " + b.depth() + " values on the stack");
                }
                return null;
            }
            List<PsiElement> merged = new ArrayList<>();
            for (int i = 0; i < a.depth(); i++) {
                PsiElement x = a.stack.get(i);
                merged.add(Objects.equals(x, b.stack.get(i)) ? x : ref);
            }
            return new State(merged);
        }

        /** The exit check of section 10.5 at the end of the body or at an {@code exit}. */
        void checkExit(PsiElement at, State s, boolean isExit) {
            if (effect.uncheckedOut() || relative > 0) return;
            int want = effect.outputs().size();
            if (s.depth() == want) return;
            problem(at, "E_EFFECT: " + (isExit ? "at this exit" : "at the end of the body") + " the stack holds "
                    + values(s.depth()) + ", but the header " + effect.text() + " declares " + values(want));
        }

        void checkLoop(FLWordRef ref, String core, LoopRule rule, State s) {
            if (s.depth() < 1) return;
            PsiElement q = s.stack.get(s.depth() - 1);
            if (isQuotation(q)) checkQuotation(q, core, rule);
        }

        void checkWhile(FLWordRef ref, State s) {
            if (s.depth() < 2) return;
            PsiElement cond = s.stack.get(s.depth() - 2);
            PsiElement body = s.stack.get(s.depth() - 1);
            if (isQuotation(cond)) checkQuotation(cond, "while", new LoopRule(0, 1));
            if (isQuotation(body)) checkQuotation(body, "while", new LoopRule(0, 0));
        }

        /** A quotation run by an iterating combinator must change the depth as section 8 says. */
        void checkQuotation(PsiElement q, String combinator, LoopRule rule) {
            List<PsiElement> phantoms = new ArrayList<>(Collections.nCopies(PHANTOMS + rule.pushed(), null));
            relative++;
            State end;
            try {
                end = run(quotationItems(q), new State(phantoms));
            } finally {
                relative--;
            }
            if (end == null || end.terminal()) return;
            int change = end.depth() - PHANTOMS - rule.pushed();
            int want = rule.leaves() - rule.pushed();
            if (change == want) return;
            String given = switch (rule.pushed()) {
                case 0 -> "";
                case 1 -> " (it receives " + (combinator.equals("reduce") ? "the accumulator" : "the element") + ")";
                default -> combinator.equals("reduce") ? " (it receives the accumulator and the element)"
                        : " (it receives the element and its index)";
            };
            problem(q, "E_EFFECT: a quotation run by '" + combinator + "' must change the stack by " + signed(want)
                    + given + ", but this one changes it by " + signed(change));
        }

        private static String signed(int n) {
            return n > 0 ? "+" + n : Integer.toString(n);
        }
    }

    static String values(int n) {
        return n == 1 ? "1 value" : n + " values";
    }
}
