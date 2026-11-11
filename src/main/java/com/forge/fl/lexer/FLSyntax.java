package com.forge.fl.lexer;

import com.forge.fl.FLTokenTypes;
import com.intellij.psi.tree.IElementType;

import java.util.Set;
import java.util.regex.Pattern;

/** Lexical predicates of the FL language (docs/LANGUAGE.md section 2 and 5.4). */
public final class FLSyntax {
    /** Reserved words (spec 2.11): cannot be defined as words, constants, variables, aliases or parameters. */
    public static final Set<String> RESERVED = Set.of(
            ":", ";", "IN:", "USING:", "META:", "VOCAB", "CONSTANT", "VARIABLE", "PRIMITIVE", "recurse", "true", "false");

    /** Header type names (spec 5.4). */
    public static final Set<String> TYPE_NAMES = Set.of(
            "any", "bytes", "str", "int", "num", "number", "bool", "list", "quot", "record", "file", "regex", "charset");

    /** META: clause words (spec 5.9). */
    public static final Set<String> META_CLAUSES = Set.of("doc", "category", "version", "param", "alias", "see-also");

    private static final String SYMBOLS = "!$%&*+,./<=>?@^|~";
    private static final int MAX_WORD_NAME = 128;

    static final Pattern DEC_INT = Pattern.compile("^-?[0-9]+(?:_[0-9]+)*$");
    static final Pattern HEX_INT = Pattern.compile("^-?0x[0-9a-fA-F]+(?:_[0-9a-fA-F]+)*$");
    static final Pattern BIN_INT = Pattern.compile("^-?0b[01]+(?:_[01]+)*$");
    private static final String DIGITS = "[0-9]+(?:_[0-9]+)*";
    private static final String EXPONENT = "[eE][+-]?" + DIGITS;
    static final Pattern FLOAT = Pattern.compile(
            "^-?" + DIGITS + "(?:\\." + DIGITS + "(?:" + EXPONENT + ")?|" + EXPONENT + ")$");
    private static final Pattern VOCAB_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,63}$");
    private static final Pattern PNAME = Pattern.compile("^[a-z][a-z0-9-]*$");

    private FLSyntax() {
    }

    /** The six whitespace characters of spec 2.1 and nothing else. */
    public static boolean isWs(int c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == 0x0B || c == 0x0C;
    }

    static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    /** Zs, Zl, Zp and Cf characters are never part of a token (spec 2.1). */
    static boolean hasInvisible(CharSequence s) {
        for (int i = 0; i < s.length(); ) {
            int cp = Character.codePointAt(s, i);
            int t = Character.getType(cp);
            if (t == Character.SPACE_SEPARATOR || t == Character.LINE_SEPARATOR
                    || t == Character.PARAGRAPH_SEPARATOR || t == Character.FORMAT) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static boolean isAsciiNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '_' || c == '-' || SYMBOLS.indexOf(c) >= 0;
    }

    /** Whether {@code s} is a word-name (spec 2.11): 1 to 128 code points, not number-like, no ':'. */
    public static boolean isWordName(CharSequence s) {
        int n = s.length();
        if (n == 0) return false;
        char first = s.charAt(0);
        if (isDigit(first) || (first == '-' && n > 1 && isDigit(s.charAt(1)))) return false;
        int count = 0;
        for (int i = 0; i < n; ) {
            int cp = Character.codePointAt(s, i);
            if (++count > MAX_WORD_NAME) return false;
            if (cp < 0x80) {
                if (!isAsciiNameChar((char) cp)) return false;
            } else if (cp <= 0x9f) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    public static boolean isVocabName(CharSequence s) {
        return VOCAB_NAME.matcher(s).matches();
    }

    public static boolean isReserved(String s) {
        return RESERVED.contains(s);
    }

    public static boolean isTypeName(String s) {
        return TYPE_NAMES.contains(s);
    }

    /** Parameter name (spec 5.4): lower, then lower, digit or '-'; not reserved. */
    public static boolean isPname(String s) {
        return PNAME.matcher(s).matches() && !isReserved(s);
    }

    /** A usable name for a new word, constant, variable, primitive or alias. */
    public static boolean isDefinableName(String s) {
        return isWordName(s) && !isReserved(s);
    }

    /**
     * The header type name a literal token has (spec 5.10: the effect of a CONSTANT or VARIABLE is the type of its
     * literal; a char literal makes an int).
     */
    public static String literalType(IElementType type, String text) {
        if (type == FLTokenTypes.STRING) return "str";
        if (type == FLTokenTypes.BYTES) return "bytes";
        if (type == FLTokenTypes.BOOL) return "bool";
        if (type == FLTokenTypes.NUMBER) {
            boolean radix = text.startsWith("0x") || text.startsWith("0b") || text.startsWith("-0x") || text.startsWith("-0b");
            return !radix && (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) ? "num" : "int";
        }
        return "int"; // CHAR
    }

    /** Whether a token text is a valid number (spec 2.4, 2.5). */
    static boolean isNumber(String run) {
        if (HEX_INT.matcher(run).matches() || BIN_INT.matcher(run).matches() || DEC_INT.matcher(run).matches()) {
            return true;
        }
        if (FLOAT.matcher(run).matches()) {
            try {
                return !Double.isInfinite(Double.parseDouble(run.replace("_", "")));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Reads the escape whose backslash is at {@code bs} (strings accept {@code \"}, chars accept {@code \'} and
     * {@code \"}). Returns the index after the escape, or -1 when invalid.
     */
    static int readEscape(CharSequence b, int bs, int end, boolean inChar) {
        if (bs + 1 >= end) return -1;
        char e = b.charAt(bs + 1);
        switch (e) {
            case 'n', 'r', 't', '0', '\\', '"':
                return bs + 2;
            case '\'':
                return inChar ? bs + 2 : -1;
            case 'x':
                if (bs + 4 <= end && isHex(b.charAt(bs + 2)) && isHex(b.charAt(bs + 3))) return bs + 4;
                return -1;
            case 'u': {
                if (bs + 2 >= end || b.charAt(bs + 2) != '{') return -1;
                int j = bs + 3;
                while (j < end && j < bs + 10 && isHex(b.charAt(j))) j++;
                int digits = j - (bs + 3);
                if (digits < 1 || digits > 6 || j >= end || b.charAt(j) != '}') return -1;
                long cp = Long.parseLong(b.subSequence(bs + 3, j).toString(), 16);
                if (cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) return -1;
                return j + 1;
            }
            default:
                return -1;
        }
    }

    static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
