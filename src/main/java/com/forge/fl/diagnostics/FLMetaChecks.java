// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.diagnostics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The rules of the {@code checks} and {@code expects} META: clauses (docs/LANGUAGE.md 5.9), ported from Forge's
 * {@code packages/lang/src/meta-checks.ts} with the same reasons. One thing is not checked: whether a {@code regex}
 * pattern is valid JavaScript ({@code new RegExp(p, "u")}); Java's regex syntax differs too much to decide it.
 */
public final class FLMetaChecks {
    /** At most this many {@code checks} per word, across all META: blocks. */
    public static final int CHECKS_MAX = 8;
    static final int CHECK_ARG_MAX = 1024;
    static final int EXPECTS_MAX = 64;
    static final int MAGIC_WINDOW = 4096;
    static final int FORMAT_ID_MAX = 64;
    /** {@code limits.maxRegexLength} by default. */
    static final int MAX_REGEX_LENGTH = 1024;

    private static final Pattern INT_RE = Pattern.compile("-?(?:0x[0-9a-fA-F](?:_?[0-9a-fA-F])*|0b[01](?:_?[01])*|[0-9](?:_?[0-9])*)");
    private static final Pattern FLOAT_RE = Pattern.compile(
            "-?[0-9](?:_?[0-9])*(?:\\.[0-9](?:_?[0-9])*(?:[eE][+-]?[0-9](?:_?[0-9])*)?|[eE][+-]?[0-9](?:_?[0-9])*)");
    private static final Pattern FORMAT_ID_RE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern HEX_RE = Pattern.compile("(?:[0-9a-fA-F]{2})+");
    private static final List<String> TESTS = List.of("regex", "charset", "entropy", "magic-bytes", "utf8", "binary", "printable");

    /** One argument as written: a string's decoded value, or the text of a word or number token. */
    public record Arg(boolean isString, @NotNull String text) {
        public static Arg str(String value) {
            return new Arg(true, value);
        }

        public static Arg name(String text) {
            return new Arg(false, text);
        }
    }

    private FLMetaChecks() {
    }

    private record Num(double value, boolean isInt) {
    }

    @Nullable
    private static Num toNumber(String text) {
        if (INT_RE.matcher(text).matches()) {
            boolean neg = text.startsWith("-");
            String digits = text.replace("_", "").substring(neg ? 1 : 0);
            BigInteger big = digits.startsWith("0x") ? new BigInteger(digits.substring(2), 16)
                    : digits.startsWith("0b") ? new BigInteger(digits.substring(2), 2) : new BigInteger(digits);
            if (big.compareTo(BigInteger.valueOf(9007199254740991L)) > 0) return null;
            return new Num(neg ? -big.doubleValue() : big.doubleValue(), true);
        }
        if (FLOAT_RE.matcher(text).matches()) {
            double v = Double.parseDouble(text.replace("_", ""));
            return Double.isFinite(v) ? new Num(v, false) : null;
        }
        return null;
    }

    private static int cpLength(String s) {
        return s.codePointCount(0, s.length());
    }

    private static boolean hasControl(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < 0x20) return true;
        }
        return false;
    }

    /** JavaScript's {@code \s} (with the u flag): white space and line terminators. */
    private static boolean isJsSpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0xFEFF;
    }

    /** The reason an {@code expects} text is refused (the use-when rules, 64 code points), or null. */
    @Nullable
    public static String checkExpects(@NotNull String text) {
        if (hasControl(text)) return "expects must be a single line without control characters";
        if (text.codePoints().allMatch(FLMetaChecks::isJsSpace)) return "expects must not be empty or only white space";
        if (isJsSpace(text.codePointAt(0)) || isJsSpace(text.codePointBefore(text.length()))) {
            return "expects must not start or end with a space";
        }
        if (cpLength(text) > EXPECTS_MAX) return "expects is longer than " + EXPECTS_MAX + " code points";
        return null;
    }

    /** The reason a charset alphabet is refused, or null. {@code x-y} is a range; any other {@code -} is itself. */
    @Nullable
    static String checkAlphabet(@NotNull String alphabet) {
        int[] cps = alphabet.codePoints().toArray();
        if (cps.length == 0) return "the charset alphabet must not be empty";
        for (int i = 0; i < cps.length; i++) {
            if (i + 1 < cps.length && cps[i + 1] == '-' && i + 2 < cps.length) {
                if (cps[i] > cps[i + 2]) {
                    return "empty range " + Character.toString(cps[i]) + "-" + Character.toString(cps[i + 2])
                            + " in the charset alphabet";
                }
                i += 2;
            }
        }
        return null;
    }

    /** The reason a {@code checks} clause with these arguments is refused, or null when it is valid. */
    @Nullable
    public static String checkProblem(@NotNull List<Arg> args) {
        return new Parser(args).parse();
    }

    private static final class Parser {
        private final List<Arg> rest;
        private String name = "";
        private int at;
        private String error;

        Parser(List<Arg> args) {
            this.args = args;
            this.rest = args.size() > 2 ? args.subList(2, args.size()) : List.of();
        }

        private final List<Arg> args;

        private boolean more() {
            return at < rest.size();
        }

        @Nullable
        private String str(String what) {
            Arg a = at < rest.size() ? rest.get(at) : null;
            at++;
            if (a != null && a.isString()) return a.text();
            error = name + " needs " + what + " as a string";
            return null;
        }

        @Nullable
        private Num num(String what, double lo, double hi, boolean integer) {
            Arg a = at < rest.size() ? rest.get(at) : null;
            at++;
            Num n = a != null && !a.isString() ? toNumber(a.text()) : null;
            if (n == null) {
                error = name + " needs " + what + " as a number";
            } else if (integer && !n.isInt()) {
                error = what + " must be an integer";
            } else if (n.value() < lo || n.value() > hi) {
                error = what + " must be from " + fmt(lo) + " to " + fmt(hi);
            } else {
                return n;
            }
            return null;
        }

        private static String fmt(double d) {
            if (d == Double.MIN_VALUE) return "5e-324";
            return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
        }

        private String done() {
            return more() ? "too many arguments for " + name : null;
        }

        /** A minimum share above 0 and at most 1, if one is written; false when it is bad. */
        private boolean share() {
            return !more() || num("the minimum share", Double.MIN_VALUE, 1, false) != null;
        }

        String parse() {
            if (args.size() < 2 || args.get(0).isString() || args.get(1).isString()) {
                return "takes a scope and a test: checks <input|output> <test> <arguments> ;";
            }
            String scope = args.get(0).text();
            if (!scope.equals("input") && !scope.equals("output")) {
                return "the scope must be 'input' or 'output', not '" + scope + "'";
            }
            name = args.get(1).text();
            if (!TESTS.contains(name)) return "'" + name + "' is not a test (" + String.join(", ", TESTS) + ")";
            for (Arg a : rest) {
                if (a.isString() && cpLength(a.text()) > CHECK_ARG_MAX) {
                    return "an argument is longer than " + CHECK_ARG_MAX + " code points";
                }
            }
            switch (name) {
                case "utf8", "binary" -> {
                    return rest.isEmpty() ? null : name + " takes no arguments";
                }
                case "regex" -> {
                    String p = str("a pattern");
                    if (p == null) return error;
                    if (p.isEmpty()) return "the pattern must not be empty";
                    if (cpLength(p) > MAX_REGEX_LENGTH) return "a pattern may be at most " + MAX_REGEX_LENGTH + " code points";
                    return done();
                }
                case "charset" -> {
                    String alphabet = str("an alphabet");
                    if (alphabet == null) return error;
                    String bad = checkAlphabet(alphabet);
                    if (bad != null) return bad;
                    return share() ? done() : error;
                }
                case "entropy" -> {
                    Num min = num("the minimum entropy", 0, 8, false);
                    if (min == null) return error;
                    Num max = num("the maximum entropy", 0, 8, false);
                    if (max == null) return error;
                    if (min.value() > max.value()) return "the minimum entropy is above the maximum";
                    return done();
                }
                case "printable" -> {
                    return share() ? done() : error;
                }
                default -> { // magic-bytes: format "<id>", or "<hex>" [<offset>]
                    Arg first = rest.isEmpty() ? null : rest.get(0);
                    if (first != null && !first.isString() && first.text().equals("format")) {
                        at = 1;
                        String id = str("a format id");
                        if (id == null) return error;
                        if (id.length() > FORMAT_ID_MAX || !FORMAT_ID_RE.matcher(id).matches()) {
                            return "the format id must be lower-case words joined by hyphens, at most " + FORMAT_ID_MAX + " characters";
                        }
                        return done();
                    }
                    String hex = str("a hex string");
                    if (hex == null) return error;
                    if (!HEX_RE.matcher(hex).matches()) return "the hex string must be one or more whole bytes";
                    double offset = 0;
                    if (more()) {
                        Num o = num("the offset", 0, MAGIC_WINDOW - 1, true);
                        if (o == null) return error;
                        offset = o.value();
                    }
                    if (offset + hex.length() / 2.0 > MAGIC_WINDOW) {
                        return "the offset and the bytes must end within the first " + MAGIC_WINDOW + " bytes";
                    }
                    return done();
                }
            }
        }
    }
}
