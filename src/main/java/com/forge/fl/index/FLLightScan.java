package com.forge.fl.index;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.lexer.FLLexer;
import com.forge.fl.lexer.FLSyntax;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.forge.fl.FLTokenTypes.*;

/**
 * Computes the content of {@link FLIndex} for one file from its tokens alone: no PSI, no resolution. It follows
 * the structure rules of {@link com.forge.fl.parser.FLParser} (the same recovery decisions), so that for a file
 * the declarations equal those of the PSI and the uses are exactly the {@code FLWordRef}s. A test compares the
 * two on the bundled corpus.
 */
public final class FLLightScan {
    public static final String DEFAULT_VOCAB = "user";

    private static final TokenSet FORM_STARTS = TokenSet.create(COLON, IN_KW, USING_KW, META_KW, VOCAB_KW, PRIMITIVE_KW);

    private final CharSequence text;
    private final int[] starts;
    private final int[] ends;
    private final IElementType[] types;
    private final int n;
    private int i;
    private String vocab = DEFAULT_VOCAB;
    private final Map<String, List<FLIndexEntry>> out = new LinkedHashMap<>();

    private FLLightScan(CharSequence text) {
        this.text = text;
        List<int[]> spans = new ArrayList<>();
        List<IElementType> kinds = new ArrayList<>();
        FLLexer lexer = new FLLexer();
        lexer.start(text, 0, text.length(), 0);
        for (IElementType t = lexer.getTokenType(); t != null; lexer.advance(), t = lexer.getTokenType()) {
            if (t == TokenType.WHITE_SPACE || COMMENTS.contains(t)) continue;
            spans.add(new int[]{lexer.getTokenStart(), lexer.getTokenEnd()});
            kinds.add(t);
        }
        n = spans.size();
        starts = new int[n];
        ends = new int[n];
        types = kinds.toArray(new IElementType[0]);
        for (int k = 0; k < n; k++) {
            starts[k] = spans.get(k)[0];
            ends[k] = spans.get(k)[1];
        }
    }

    @NotNull
    public static Map<String, List<FLIndexEntry>> scan(@NotNull CharSequence text) {
        FLLightScan s = new FLLightScan(text);
        s.run();
        return s.out;
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    @Nullable
    private IElementType type(int k) {
        return k < n ? types[k] : null;
    }

    private String tokenText(int k) {
        return text.subSequence(starts[k], ends[k]).toString();
    }

    private boolean isFormStart(@Nullable IElementType t) {
        return t != null && FORM_STARTS.contains(t);
    }

    private void add(String key, byte kind, int offset, @Nullable String detail) {
        out.computeIfAbsent(key, k -> new ArrayList<>()).add(new FLIndexEntry(kind, offset, detail));
    }

    private void markVocab() {
        List<FLIndexEntry> existing = out.get(FLIndex.vocabKey(vocab));
        if (existing == null) add(FLIndex.vocabKey(vocab), FLIndexEntry.Kind.VOCAB, 0, null);
    }

    private void declare(String name, byte kind, int offset, @Nullable String detail) {
        markVocab();
        add(FLIndex.declKey(vocab, name), kind, offset, detail);
    }

    /** A use of the word at token k ({@code WORD_NAME} or {@code QUALIFIED_NAME}). */
    private void use(int k) {
        String t = tokenText(k);
        add(FLIndex.usageKey(t.substring(t.indexOf(':') + 1)), FLIndexEntry.Kind.USAGE, starts[k], null);
    }

    private static boolean isLiteral(@Nullable IElementType t) {
        return t != null && LITERALS.contains(t);
    }

    // ---- structure ----------------------------------------------------------------------------------------

    private void run() {
        while (i < n) {
            IElementType t = types[i];
            if (t == COLON) {
                definition();
            } else if (t == IN_KW || t == VOCAB_KW) {
                i++;
                if (type(i) == WORD_NAME) {
                    vocab = tokenText(i);
                    markVocab();
                    i++;
                }
            } else if (t == USING_KW) {
                i++;
                while (i < n && types[i] != SEMI && !isFormStart(types[i])) i++;
                if (type(i) == SEMI) i++;
            } else if (t == META_KW) {
                meta();
            } else if (t == PRIMITIVE_KW) {
                i++;
                if (type(i) == WORD_NAME) {
                    declare(tokenText(i), FLIndexEntry.Kind.PRIMITIVE, starts[i], null);
                    i++;
                }
            } else if (isLiteral(t) && (type(i + 1) == CONSTANT_KW || type(i + 1) == VARIABLE_KW)) {
                constOrVar();
            } else {
                if (t == WORD_NAME || t == QUALIFIED_NAME) use(i);
                i++;
            }
        }
    }

    private void constOrVar() {
        String effectType = FLSyntax.literalType(types[i], tokenText(i));
        boolean constant = type(i + 1) == CONSTANT_KW;
        i += 2;
        if (type(i) != WORD_NAME) return;
        String name = tokenText(i);
        int offset = starts[i];
        i++;
        if (constant) {
            declare(name, FLIndexEntry.Kind.CONSTANT, offset, "( -- " + effectType + " )");
        } else {
            declare(name, FLIndexEntry.Kind.VARIABLE, offset, "( -- " + effectType + " )");
            declare(name + "!", FLIndexEntry.Kind.VARIABLE_SETTER, offset, "( " + effectType + " -- )");
        }
    }

    private void definition() {
        i++; // ':'
        int nameTok = -1;
        IElementType t = type(i);
        if (t == WORD_NAME) {
            nameTok = i;
            i++;
        } else if (t == QUALIFIED_NAME || (t != null && FLTokenTypes.KEYWORDS.contains(t) && t != COLON && t != SEMI)) {
            i++;
        }
        String detail = null;
        if (type(i) == HDR_LPAREN) {
            int first = i;
            i++;
            while (i < n && types[i] != HDR_RPAREN) {
                IElementType h = types[i];
                if (h == HDR_PARAM_NAME) {
                    i++;
                    if (type(i) == HDR_PARAM_REST) i++;
                } else if (h == HDR_PARAM_REST || h == HDR_TYPE || h == HDR_ARROW || h == HDR_DOTS
                        || h == HDR_OUTPUT || h == HDR_OTHER) {
                    i++;
                } else {
                    break;
                }
            }
            int last = i - 1;
            if (type(i) == HDR_RPAREN) {
                last = i;
                i++;
            }
            detail = text.subSequence(starts[first], ends[last]).toString().replaceAll("\\s+", " ");
        }
        while (type(i) == STRING && type(i + 1) == WORD_NAME
                && (tokenText(i + 1).equals("doc") || tokenText(i + 1).equals("category"))) {
            i += 2;
        }
        while (i < n && types[i] != SEMI && !isFormStart(types[i])) {
            if (types[i] == WORD_NAME || types[i] == QUALIFIED_NAME) use(i);
            i++;
        }
        if (type(i) == SEMI) i++;
        if (nameTok >= 0) declare(tokenText(nameTok), FLIndexEntry.Kind.WORD, starts[nameTok], detail);
    }

    private void meta() {
        i++; // META:
        if (type(i) == WORD_NAME) {
            use(i);
            i++;
        }
        while (i < n) {
            IElementType t = types[i];
            if (t == SEMI) {
                i++;
                return;
            }
            if (isFormStart(t)) return;
            if (t == WORD_NAME && FLSyntax.META_CLAUSES.contains(tokenText(i))) {
                clause();
            } else if (t == WORD_NAME) {
                i++;
                while (i < n && types[i] != SEMI && !isFormStart(types[i])) i++;
                if (type(i) == SEMI) i++;
            } else {
                i++;
            }
        }
    }

    private void clause() {
        String key = tokenText(i);
        i++;
        boolean paramName = key.equals("param");
        while (i < n && types[i] != SEMI && !isFormStart(types[i])) {
            IElementType t = types[i];
            switch (key) {
                case "param" -> {
                    if (paramName && t == WORD_NAME) paramName = false;
                }
                case "alias" -> {
                    if (t == WORD_NAME) declare(tokenText(i), FLIndexEntry.Kind.ALIAS, starts[i], "alias");
                }
                case "see-also" -> {
                    if (t == WORD_NAME || t == QUALIFIED_NAME) use(i);
                }
                default -> { // doc, category, version
                }
            }
            i++;
        }
        if (type(i) == SEMI) i++;
    }
}
