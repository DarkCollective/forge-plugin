package com.forge.fl.lexer;

import com.forge.fl.FLTokenTypes;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.forge.fl.FLTokenTypes.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Expected results come from docs/LANGUAGE.md section 2 and packages/lang/src/reader.ts. */
public class FLLexerTest {
    record Tok(IElementType type, String text, int start, int end, int state) {
        @Override
        public String toString() {
            return type + "'" + text + "'@" + start + "/" + state;
        }
    }

    static List<Tok> lex(String s) {
        FLLexer lexer = new FLLexer();
        lexer.start(s, 0, s.length(), 0);
        return rest(lexer, s);
    }

    static List<Tok> rest(FLLexer lexer, String s) {
        List<Tok> out = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            out.add(new Tok(lexer.getTokenType(), s.substring(lexer.getTokenStart(), lexer.getTokenEnd()),
                    lexer.getTokenStart(), lexer.getTokenEnd(), lexer.getState()));
            lexer.advance();
        }
        return out;
    }

    /** The significant tokens as "TYPE:text" strings. */
    static List<String> sig(String s) {
        List<String> out = new ArrayList<>();
        for (Tok t : lex(s)) {
            if (t.type() != TokenType.WHITE_SPACE) out.add(t.type() + ":" + t.text());
        }
        return out;
    }

    static void assertSig(String src, String... expected) {
        assertEquals(List.of(expected), sig(src));
    }

    @Test
    public void everyTokenKind() {
        assertSig("\\ comment text", "LINE_COMMENT:\\ comment text");
        assertSig("( a block )", "BLOCK_COMMENT:( a block )");
        assertSig("\"s\\n\\u{1F600}\"", "STRING:\"s\\n\\u{1F600}\"");
        assertSig("'a' '\\n' ' ' '\\x41'", "CHAR:'a'", "CHAR:'\\n'", "CHAR:' '", "CHAR:'\\x41'");
        assertSig("#[ de AD 00 ] #[ ]", "BYTES:#[ de AD 00 ]", "BYTES:#[ ]");
        assertSig("42 -7 0x1F 0b101 1_000 -0 1.5 -2.5e-3 1e3",
                "NUMBER:42", "NUMBER:-7", "NUMBER:0x1F", "NUMBER:0b101", "NUMBER:1_000", "NUMBER:-0", "NUMBER:1.5",
                "NUMBER:-2.5e-3", "NUMBER:1e3");
        assertSig("true false", "BOOL:true", "BOOL:false");
        assertSig(": ; IN: USING: META: VOCAB CONSTANT VARIABLE PRIMITIVE recurse",
                "COLON::", "SEMI:;", "IN_KW:IN:", "USING_KW:USING:", "META_KW:META:", "VOCAB_KW:VOCAB",
                "CONSTANT_KW:CONSTANT", "VARIABLE_KW:VARIABLE", "PRIMITIVE_KW:PRIMITIVE", "RECURSE_KW:recurse");
        assertSig("[ ] { }", "LBRACKET:[", "RBRACKET:]", "LBRACE:{", "RBRACE:}");
        assertSig("base64:decode foo", "QUALIFIED_NAME:base64:decode", "WORD_NAME:foo");
    }

    @Test
    public void symbolNamesAreWords() {
        // spec 2.11: "-" and "-rot" and ">str" and ".s" and "<>" are words
        assertSig("- -rot >str .s <> + n! _private .5",
                "WORD_NAME:-", "WORD_NAME:-rot", "WORD_NAME:>str", "WORD_NAME:.s", "WORD_NAME:<>", "WORD_NAME:+",
                "WORD_NAME:n!", "WORD_NAME:_private", "WORD_NAME:.5");
    }

    @Test
    public void whitespaceIsExactlySixCharacters() {
        List<String> s = sig("a\tb\u000Bc\u000Cd\re\nf");
        assertEquals(6, s.size());
        // U+00A0 (Zs) and U+200B (Cf) inside a token are errors; they are not separators
        assertEquals(List.of("BAD_CHARACTER:a b"), sig("a b"));
        assertEquals(List.of("BAD_CHARACTER:a​b"), sig("a​b"));
    }

    @Test
    public void headerSubTokens() {
        assertSig(": f ( str amount:int[1..25]=13 -- str ) ;",
                "COLON::", "WORD_NAME:f", "HDR_LPAREN:(", "HDR_TYPE:str", "HDR_PARAM_NAME:amount",
                "HDR_PARAM_REST::int[1..25]=13", "HDR_ARROW:--", "HDR_TYPE:str", "HDR_RPAREN:)", "SEMI:;");
        assertSig(": _add ( a b -- c ) ;",
                "COLON::", "WORD_NAME:_add", "HDR_LPAREN:(", "HDR_PARAM_NAME:a", "HDR_PARAM_NAME:b", "HDR_ARROW:--",
                "HDR_OUTPUT:c", "HDR_RPAREN:)", "SEMI:;");
        assertSig(": f ( .. n:int -- .. ) ;",
                "COLON::", "WORD_NAME:f", "HDR_LPAREN:(", "HDR_DOTS:..", "HDR_PARAM_NAME:n", "HDR_PARAM_REST::int",
                "HDR_ARROW:--", "HDR_DOTS:..", "HDR_RPAREN:)", "SEMI:;");
        assertSig(": f ( -- ) ;", "COLON::", "WORD_NAME:f", "HDR_LPAREN:(", "HDR_ARROW:--", "HDR_RPAREN:)", "SEMI:;");
        // output items: named, labelled and bare types
        assertSig(": f ( -- r:str bool x ) ;",
                "COLON::", "WORD_NAME:f", "HDR_LPAREN:(", "HDR_ARROW:--", "HDR_OUTPUT:r:str", "HDR_TYPE:bool",
                "HDR_OUTPUT:x", "HDR_RPAREN:)", "SEMI:;");
    }

    @Test
    public void headerItemsMayContainStringsAndBraces() {
        // spec 2.8: a ')' inside a string does not end the header; 5.4 examples
        assertSig(": f ( s:str=\"a ) b\" -- str ) ;",
                "COLON::", "WORD_NAME:f", "HDR_LPAREN:(", "HDR_PARAM_NAME:s", "HDR_PARAM_REST::str=\"a ) b\"",
                "HDR_ARROW:--", "HDR_TYPE:str", "HDR_RPAREN:)", "SEMI:;");
        assertSig(": f ( style:choice{\"Upper case\"|\"Lower case\"}=\"Upper case\" key:key{hex|utf8}[16|24|32] -- ) ;",
                "COLON::", "WORD_NAME:f", "HDR_LPAREN:(", "HDR_PARAM_NAME:style",
                "HDR_PARAM_REST::choice{\"Upper case\"|\"Lower case\"}=\"Upper case\"", "HDR_PARAM_NAME:key",
                "HDR_PARAM_REST::key{hex|utf8}[16|24|32]", "HDR_ARROW:--", "HDR_RPAREN:)", "SEMI:;");
    }

    @Test
    public void blockCommentVersusHeader() {
        // after ": name" a standalone "(" is a header; anywhere else a block comment
        assertEquals("HDR_LPAREN", sig(": f ( -- ) ;").get(2).split(":")[0]);
        assertSig("( top level ) 1", "BLOCK_COMMENT:( top level )", "NUMBER:1");
        assertSig(": f ( -- ) ( inside ) ;", "COLON::", "WORD_NAME:f", "HDR_LPAREN:(", "HDR_ARROW:--",
                "HDR_RPAREN:)", "BLOCK_COMMENT:( inside )", "SEMI:;");
        // comments between ": name" and "(" do not count as tokens (spec 2.8)
        assertSig(": f \\ c\n ( -- ) ;", "COLON::", "WORD_NAME:f", "LINE_COMMENT:\\ c", "HDR_LPAREN:(", "HDR_ARROW:--",
                "HDR_RPAREN:)", "SEMI:;");
        assertSig(": f ( x ) ( -- ) ;".substring(0, 10), "COLON::", "WORD_NAME:f", "HDR_LPAREN:(", "HDR_PARAM_NAME:x",
                "HDR_RPAREN:)");
        // ": (" has no name in between: block comment
        assertSig(": ( a ) x", "COLON::", "BLOCK_COMMENT:( a )", "WORD_NAME:x");
        // ": x y (" : one token too many
        assertSig(": x y ( c ) ;", "COLON::", "WORD_NAME:x", "WORD_NAME:y", "BLOCK_COMMENT:( c )", "SEMI:;");
        // a qualified name counts as a name (qword)
        assertEquals("HDR_LPAREN", sig(": a:b ( ) ;").get(2).split(":")[0]);
        // "(" glued to text is not standalone
        assertSig("a(b", "BAD_CHARACTER:a(b");
        assertSig(": f (x) ;", "COLON::", "WORD_NAME:f", "BAD_CHARACTER:(x)", "SEMI:;");
    }

    @Test
    public void malformedInput() {
        assertSig("\"abc", "BAD_CHARACTER:\"abc");
        assertSig("\"a\\qb\"", "BAD_CHARACTER:\"a\\qb\"");
        assertSig("\"a\"\"b\"", "BAD_CHARACTER:\"a\"\"b\"");
        assertSig("( never closed", "BAD_CHARACTER:( never closed");
        assertSig("#[ de ad", "BAD_CHARACTER:#[ de ad");
        assertSig("#[ de zz ] 1", "BAD_CHARACTER:#[ de zz ]", "NUMBER:1");
        assertSig("#[ dead ]", "BAD_CHARACTER:#[ dead ]");
        assertSig("foo;", "BAD_CHARACTER:foo;");
        assertSig("a:b:c", "BAD_CHARACTER:a:b:c");
        assertSig(":x", "BAD_CHARACTER::x");
        assertSig("x:", "BAD_CHARACTER:x:");
        assertSig("1+", "BAD_CHARACTER:1+");
        assertSig("5.", "BAD_CHARACTER:5.");
        assertSig("1__0 1_ 0x_1 0B1 1e999", "BAD_CHARACTER:1__0", "BAD_CHARACTER:1_", "BAD_CHARACTER:0x_1",
                "BAD_CHARACTER:0B1", "BAD_CHARACTER:1e999");
        assertSig("[1 2]", "BAD_CHARACTER:[1", "BAD_CHARACTER:2]");
        assertSig("'ab' '' 'a", "BAD_CHARACTER:'ab'", "BAD_CHARACTER:''", "BAD_CHARACTER:'a");
        assertSig("#[de", "BAD_CHARACTER:#[de");
        assertSig("a#b a\"b", "BAD_CHARACTER:a#b", "BAD_CHARACTER:a\"b");
    }

    @Test
    public void unterminatedHeaderEndsAtEndOfBuffer() {
        List<Tok> toks = lex(": f ( a b");
        assertEquals(LexerTestSupport.last(toks).end(), 9);
        assertSig(": f ( a b", "COLON::", "WORD_NAME:f", "HDR_LPAREN:(", "HDR_PARAM_NAME:a", "HDR_PARAM_NAME:b");
        assertSig(": f ( s:str=\"abc", "COLON::", "WORD_NAME:f", "HDR_LPAREN:(", "HDR_PARAM_NAME:s",
                "HDR_PARAM_REST::str=\"abc");
    }

    static final String[] SAMPLES = {
            ": f ( str amount:int[1..25]=13 -- str )\n  \"d)\" doc\n  amount [ 1 + ] { 1 2 } ;\n",
            "IN: a\nUSING: b c ;\n0 VARIABLE x\n#[ de ad ] CONSTANT y\nMETA: f\n  param amount \"x\" ;\n;\n",
            "( c ) : g ( a b -- c ) a b + ; \\ end\n'x' \"unterminated",
            ": h ( s:str=\"a ) b\" -- ) ; : i ( ",
            "\\\n: : ( ( ) ) ; ( ( 1+ foo; a:b:c #[ zz",
    };

    @Test
    public void tokensCoverTheBufferWithoutGaps() {
        for (String s : SAMPLES) checkInvariants(s);
        Random rnd = new Random(42);
        String[] atoms = {"\"", "'", "(", ")", "\\", ":", ";", "#[", "]", "[", "{", "}", " ", "\n", "a", "1", "--", "-",
                "x:int", "..", " ", "\\x41", "doc", "IN:", "=", "|"};
        for (int n = 0; n < 3000; n++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + rnd.nextInt(14);
            for (int i = 0; i < len; i++) sb.append(atoms[rnd.nextInt(atoms.length)]);
            checkInvariants(sb.toString());
        }
    }

    static void checkInvariants(String s) {
        List<Tok> toks = lex(s);
        int pos = 0;
        for (Tok t : toks) {
            assertEquals("gap or overlap in " + s + " at " + t, pos, t.start());
            assertTrue("empty token in " + s, t.end() > t.start());
            assertTrue("past the end in " + s, t.end() <= s.length());
            pos = t.end();
        }
        assertEquals("buffer not covered: " + s, s.length(), pos);
        // restart from every token boundary with the recorded state
        for (int i = 0; i < toks.size(); i++) {
            FLLexer lexer = new FLLexer();
            lexer.start(s, toks.get(i).start(), s.length(), toks.get(i).state());
            List<Tok> again = rest(lexer, s);
            assertEquals("restart at token " + i + " of: " + s, toks.subList(i, toks.size()), again);
        }
    }

    @Test
    public void restartFromEveryBoundaryGivesTheSameTokens() {
        for (String s : SAMPLES) checkInvariants(s);
        // header state matters: restarting inside a header must keep lexing header items
        List<Tok> toks = lex(": f ( a:int b -- c ) ;");
        Tok b = toks.stream().filter(t -> t.text().equals("b")).findFirst().orElseThrow();
        assertEquals(FLLexer.S_HDR_IN, b.state());
        Tok c = toks.stream().filter(t -> t.text().equals("c")).findFirst().orElseThrow();
        assertEquals(FLLexer.S_HDR_OUT, c.state());
        assertFalse(toks.isEmpty());
    }

    @Test
    public void boundedRangeNeverReadsPastEnd() {
        String s = ": f ( a -- b ) ; extra";
        FLLexer lexer = new FLLexer();
        lexer.start(s, 0, 8, 0); // ": f ( a "
        List<Tok> toks = rest(lexer, s);
        assertEquals(8, LexerTestSupport.last(toks).end());
    }

    @Test
    public void tokenKindsAreDistinct() {
        assertFalse(FLTokenTypes.LINE_COMMENT == FLTokenTypes.BLOCK_COMMENT);
        assertEquals(TokenType.BAD_CHARACTER, FLTokenTypes.BAD);
    }
}
