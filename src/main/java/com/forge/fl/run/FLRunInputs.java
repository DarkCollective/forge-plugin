// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.psi.FLChoiceRef;
import com.forge.fl.psi.FLChoiceType;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLHeader;
import com.forge.fl.psi.FLParameter;
import com.forge.fl.psi.FLResolver;
import com.forge.fl.psi.FLScope;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The inputs of a word as the run dialog shows them, and the FL program that runs the word with them.
 * Defaults are metadata only (docs/LANGUAGE.md 5.4): the VM never fills one in, so the program pushes a value
 * for every input, the default included.
 */
public final class FLRunInputs {
    /** Types whose value is a string literal; raw text typed for one of them is quoted. */
    private static final List<String> STRING_TYPES = List.of("str", "choice", "regex", "charset", "toggle", "key");

    /**
     * One input slot, deepest first.
     *
     * @param name        the parameter name, or null for an anonymous input (a bare type in the header)
     * @param type        the base type: {@code str}, {@code int}, {@code choice}, ... ({@code any} for a bare label)
     * @param defaultText the default as written in the header (a string default keeps its quotes), or null
     * @param options     the options of a {@code choice} type (unquoted), or null when not a choice or unresolved
     */
    public record Slot(@Nullable String name, @NotNull String type, @Nullable String defaultText,
                       @Nullable List<String> options) {
        public String label() {
            return name == null ? type : name + ":" + type;
        }
    }

    private FLRunInputs() {
    }

    /** The vocabulary the definition belongs to. */
    @NotNull
    public static String vocabOf(@NotNull FLDefinition def) {
        return FLScope.of(def.getContainingFile()).vocabAt(def.getTextOffset());
    }

    @NotNull
    public static String qualifiedName(@NotNull FLDefinition def) {
        return vocabOf(def) + ":" + def.getName();
    }

    /** The input slots of the header, deepest first, or null when the header is missing or not understood. */
    @Nullable
    public static List<Slot> slots(@NotNull FLDefinition def) {
        FLHeader header = def.getHeader();
        if (header == null || header.inputSlots() == null) return null;
        List<Slot> out = new ArrayList<>();
        for (PsiElement c = header.getFirstChild(); c != null; c = c.getNextSibling()) {
            var t = c.getNode().getElementType();
            if (t == FLTokenTypes.HDR_ARROW) break;
            if (c instanceof FLParameter p) {
                out.add(parameterSlot(p));
            } else if (t == FLTokenTypes.HDR_TYPE) {
                out.add(new Slot(null, c.getText(), null, null));
            }
        }
        return out;
    }

    private static Slot parameterSlot(FLParameter p) {
        String text = p.getText();
        int colon = text.indexOf(':');
        if (colon < 0) return new Slot(text, "any", null, null);
        String rest = text.substring(colon + 1);
        int end = 0;
        while (end < rest.length() && "{<[=".indexOf(rest.charAt(end)) < 0) end++;
        String type = rest.substring(0, end);
        String def = defaultOf(rest);
        List<String> options = null;
        if (type.equals("choice")) {
            if (end < rest.length() && rest.charAt(end) == '{') {
                options = inlineOptions(rest.substring(end));
            } else {
                FLChoiceRef ref = PsiTreeUtil.findChildOfType(p, FLChoiceRef.class);
                FLChoiceType target = ref == null ? null : FLResolver.resolveChoice(ref);
                if (target != null) options = target.getOptions().stream().map(FLRunInputs::unquote).toList();
            }
        }
        return new Slot(p.getName(), type, def, options);
    }

    /** The text after the first {@code =} at nesting depth 0, outside strings; null when there is none. */
    @Nullable
    static String defaultOf(String rest) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < rest.length(); i++) {
            char ch = rest.charAt(i);
            if (inString) {
                if (ch == '\\') i++;
                else if (ch == '"') inString = false;
            } else if (ch == '"') {
                inString = true;
            } else if (ch == '{' || ch == '[' || ch == '<') {
                depth++;
            } else if (ch == '}' || ch == ']' || ch == '>') {
                depth--;
            } else if (ch == '=' && depth == 0) {
                return rest.substring(i + 1);
            }
        }
        return null;
    }

    /** The options of {@code {a|"b c"|d}}, unquoted. */
    static List<String> inlineOptions(String braces) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inString = false;
        for (int i = 1; i < braces.length(); i++) {
            char ch = braces.charAt(i);
            if (inString) {
                cur.append(ch);
                if (ch == '\\' && i + 1 < braces.length()) cur.append(braces.charAt(++i));
                else if (ch == '"') inString = false;
            } else if (ch == '"') {
                inString = true;
                cur.append(ch);
            } else if (ch == '|' || ch == '}') {
                out.add(unquote(cur.toString()));
                cur.setLength(0);
                if (ch == '}') break;
            } else {
                cur.append(ch);
            }
        }
        return out;
    }

    /** {@code "a\"b"} becomes {@code a"b}; any other text is returned as is. */
    static String unquote(String s) {
        if (s.length() < 2 || s.charAt(0) != '"' || s.charAt(s.length() - 1) != '"') return s;
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < s.length() - 1; i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length() - 1) {
                char e = s.charAt(++i);
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '0' -> out.append('\0');
                    default -> {
                        if (e == 'x' || e == 'u') out.append('\\');
                        out.append(e);
                    }
                }
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    /** {@code a"b} becomes {@code "a\"b"}. Raw line breaks are allowed inside FL strings (2.6). */
    public static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Whether the slot takes a string literal, so that raw text typed for it is quoted. */
    public static boolean isStringType(@NotNull Slot slot) {
        return STRING_TYPES.contains(slot.type());
    }

    /** The text the dialog shows first for the slot: its default, or empty. */
    @NotNull
    public static String initialText(@NotNull Slot slot) {
        return slot.defaultText() == null ? "" : slot.defaultText();
    }

    /**
     * The FL literal for what the user typed into the slot's field. For a string-typed slot, text that does not
     * start with {@code "} is taken as the string itself and quoted; anything else is FL source pushed as is
     * ({@code 42}, {@code #[ 01 02 ]}, {@code [ 1 2 ]}, {@code "text"} for a bytes input).
     *
     * @throws IllegalArgumentException when the field is empty and the slot is not a string type
     */
    @NotNull
    public static String literal(@NotNull Slot slot, @NotNull String text) {
        if (isStringType(slot)) {
            return text.startsWith("\"") ? text : quote(text);
        }
        if (text.isBlank()) throw new IllegalArgumentException(slot.label() + " needs a value");
        return text.strip();
    }

    /** {@code lit1 lit2 ... vocab:word}. */
    @NotNull
    public static String program(@NotNull String qname, @NotNull List<String> literals) {
        StringBuilder sb = new StringBuilder();
        for (String l : literals) sb.append(l).append(' ');
        return sb.append(qname).toString();
    }
}
