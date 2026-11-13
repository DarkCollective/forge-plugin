// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl;

import com.forge.fl.diagnostics.FLShadowInspection;
import com.forge.fl.diagnostics.FLTopLevelCodeInspection;
import com.forge.fl.index.FLIndexEntry;
import com.forge.fl.index.FLLightScan;
import com.forge.fl.lexer.FLLexer;
import com.forge.fl.psi.FLChoiceType;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLLocal;
import com.forge.fl.psi.FLParameter;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The FL v0.2 additions (docs/LANGUAGE.md Appendix A): tokens, forms, resolution and diagnostics. */
public class FLLanguageV02Test extends FLMultiFileTestBase {
    private static final Pattern CODE = Pattern.compile("\\((E_[A-Z_]+|W_[A-Z_]+)\\)\\s*$");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(FLTopLevelCodeInspection.class, FLShadowInspection.class);
    }

    private List<String> tokens(String text) {
        FLLexer lexer = new FLLexer();
        lexer.start(text, 0, text.length(), 0);
        List<String> out = new ArrayList<>();
        for (IElementType t = lexer.getTokenType(); t != null; lexer.advance(), t = lexer.getTokenType()) {
            if (t == TokenType.WHITE_SPACE) continue;
            out.add(t + ":" + text.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
        }
        return out;
    }

    private List<String> diag(String text) {
        myFixture.configureByText("m.fth", text);
        List<String> out = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() == null) continue;
            if (info.getSeverity() != HighlightSeverity.ERROR && info.getSeverity() != HighlightSeverity.WEAK_WARNING) continue;
            Matcher m = CODE.matcher(info.getDescription());
            if (m.find()) out.add(m.group(1) + ":" + info.getText());
        }
        return out;
    }

    // ---- tokens (A.2) -----------------------------------------------------------------------------------

    public void testNewKeywordsAreStandaloneOnly() {
        assertEquals(List.of("MEMO_KW:MEMO:", "CHOICE_KW:CHOICE:", "BIND_KW::>",
                "QUALIFIED_NAME:MEMO:foo", "QUALIFIED_NAME:CHOICE:foo", "BAD_CHARACTER::>x"),
                tokens("MEMO: CHOICE: :> MEMO:foo CHOICE:foo :>x"));
    }

    public void testHeaderAfterMemoName() {
        assertEquals(List.of("MEMO_KW:MEMO:", "WORD_NAME:t", "HDR_LPAREN:(", "HDR_ARROW:--", "HDR_TYPE:record",
                "HDR_RPAREN:)", "SEMI:;"), tokens("MEMO: t ( -- record ) ;"));
        // CHOICE: and :> never take a header: ( is a comment there
        assertTrue(tokens("CHOICE: p ( c ) ;").contains("BLOCK_COMMENT:( c )"));
    }

    public void testChoiceReferenceInHeader() {
        assertEquals(List.of("COLON::", "WORD_NAME:e", "HDR_LPAREN:(", "HDR_TYPE:bytes", "HDR_PARAM_NAME:p",
                "HDR_PARAM_REST::choice<", "HDR_CHOICE_REF:aes:padding", "HDR_PARAM_REST:>=\"pkcs7\"", "HDR_ARROW:--",
                "HDR_TYPE:bytes", "HDR_RPAREN:)", "SEMI:;"),
                tokens(": e ( bytes p:choice<aes:padding>=\"pkcs7\" -- bytes ) ;"));
    }

    public void testLexerRestartsInsideAChoiceReference() {
        String text = ": e ( bytes p:choice<padding>=none -- bytes ) ;";
        FLLexer full = new FLLexer();
        full.start(text, 0, text.length(), 0);
        while (full.getTokenType() != FLTokenTypes.HDR_CHOICE_REF) full.advance();
        int start = full.getTokenStart();
        int state = full.getState();
        FLLexer resumed = new FLLexer();
        resumed.start(text, start, text.length(), state);
        for (; full.getTokenType() != null; full.advance(), resumed.advance()) {
            assertEquals(full.getTokenType(), resumed.getTokenType());
            assertEquals(full.getTokenEnd(), resumed.getTokenEnd());
        }
    }

    // ---- forms (A.3) ------------------------------------------------------------------------------------

    public void testAllV02FormsParseClean() {
        assertClean("""
                IN: aes
                CHOICE: padding
                  option pkcs7 "PKCS#7 padding." ;
                  option none ;
                  option 128 ;
                  option "two words" "Help." ;
                ;
                : encrypt ( data:bytes key:key{hex|utf8} p:choice<padding>=pkcs7 -- bytes )
                  data key p drop drop ;
                MEMO: _table ( -- record )
                  "a=1" "\\n" split [ "=" split ] map >record ;
                : days ( days:int -- int )
                  days 719468 + :> z
                  z [ :> q q 1 + ] call ;
                META: encrypt
                  use-when "You need to encrypt bytes with a symmetric key." ;
                  link "https://example.com/aes" "AES" ;
                  link "https://example.com/aes?x=1#y" ;
                  inverse encrypt ;
                  helper ;
                ;
                """);
    }

    public void testMemoIsADefinition() {
        PsiFile f = parse("MEMO: t ( -- int ) 1 ;\n");
        FLDefinition d = PsiTreeUtil.findChildOfType(f, FLDefinition.class);
        assertNotNull(d);
        assertTrue(d.isMemo());
        assertEquals("t", d.getName());
        assertEquals("( -- int )", d.getEffectText());
    }

    public void testChoiceTypeOptions() {
        PsiFile f = parse("CHOICE: mode option cbc ; option \"g c m\" \"help\" ; option 128 ; ;\n");
        FLChoiceType t = PsiTreeUtil.findChildOfType(f, FLChoiceType.class);
        assertNotNull(t);
        assertEquals("mode", t.getName());
        assertEquals(List.of("cbc", "g c m", "128"), t.getOptions());
    }

    public void testChecksIsReserved() {
        assertEquals(List.of("E_META:checks \"x\" ;"), diag(": f ( a:int -- ) ;\nMETA: f checks \"x\" ; ;\n"));
    }

    public void testHelperTakesNoArguments() {
        assertEmpty(diag(": f ( a:int -- ) ;\nMETA: f helper ; ;\n"));
        assertEquals(List.of("E_META:x"), diag(": f ( a:int -- ) ;\nMETA: f helper x ; ;\n"));
    }

    public void testClauseNamesAreOrdinaryWordsOutsideMeta() {
        assertEmpty(diag(": option ( -- ) ;\n: inverse ( -- ) ;\n: link ( -- ) option inverse ;\n"));
    }

    // ---- index (light scan must agree with the PSI) ------------------------------------------------------

    public void testLightScanKnowsTheNewForms() {
        Map<String, List<FLIndexEntry>> idx = FLLightScan.scan("""
                IN: v
                CHOICE: padding option none ; ;
                MEMO: t ( -- int ) 1 :> z z ;
                : f ( s:str p:choice<padding> -- ) ;
                META: f inverse g ;
                """);
        assertTrue(idx.containsKey("Tv:padding"));
        assertTrue(idx.containsKey("Dv:t"));
        assertTrue(idx.containsKey("Dv:f"));
        assertFalse("the bound name is no use", idx.get("Uz").size() != 1); // only the read of z
        assertTrue("choice<padding> is a use", idx.containsKey("Upadding"));
        assertTrue("inverse is a use", idx.containsKey("Ug"));
        assertFalse("options are no uses", idx.containsKey("Unone"));
    }

    // ---- resolution -------------------------------------------------------------------------------------

    public void testLocalResolvesToItsBinding() {
        PsiElement e = resolveAt("a.fth", ": f ( n:int -- int ) n 1 + :> m <caret>m ;\n");
        assertTrue(e instanceof FLLocal);
        assertEquals("m", ((FLLocal) e).getName());
    }

    public void testLocalShadowsWordAndParameterOrder() {
        PsiElement e = resolveAt("a.fth", ": dup2 ( -- ) ;\n: f ( n:int -- int ) n :> dup2 dup<caret>2 ;\n");
        assertTrue(e instanceof FLLocal);
        e = resolveAt("a.fth", ": f ( n:int -- int ) <caret>n :> k k ;\n");
        assertTrue(e instanceof FLParameter);
    }

    public void testLocalIsScopedToItsQuotation() {
        PsiElement inside = resolveAt("a.fth", ": f ( -- ) [ 1 :> q <caret>q ] call ;\n");
        assertTrue(inside instanceof FLLocal);
        myFixture.configureByText("b.fth", ": f ( -- ) [ 1 :> q ] call <caret>q ;\n");
        assertNull(myFixture.getFile().findReferenceAt(myFixture.getCaretOffset()).resolve());
    }

    public void testLocalNotVisibleBeforeItsBinding() {
        myFixture.configureByText("a.fth", ": f ( -- ) <caret>z 1 :> z ;\n");
        assertNull(myFixture.getFile().findReferenceAt(myFixture.getCaretOffset()).resolve());
    }

    public void testChoiceRefResolvesInVocabularyAndImports() {
        add("aes.fth", "IN: aes\nCHOICE: padding option none ; ;\nCHOICE: _hidden option a ; ;\n");
        PsiElement own = resolveAt("a.fth", "CHOICE: mode option a ; ;\n: f ( s:str m:choice<mo<caret>de> -- ) ;\n");
        assertTrue(own instanceof FLChoiceType);
        PsiElement imported = resolveAt("b.fth", "USING: aes ;\n: f ( s:str p:choice<pad<caret>ding> -- ) ;\n");
        assertEquals("aes.fth:padding", where(imported));
        PsiElement qualified = resolveAt("c.fth", ": f ( s:str p:choice<aes:pad<caret>ding> -- ) ;\n");
        assertEquals("aes.fth:padding", where(qualified));
        myFixture.configureByText("d.fth", "USING: aes ;\n: f ( s:str p:choice<_hid<caret>den> -- ) ;\n");
        assertNull(myFixture.getFile().findReferenceAt(myFixture.getCaretOffset()).resolve());
    }

    public void testTypesAndWordsHaveSeparateNamespaces() {
        assertEmpty(diag("CHOICE: padding option none ; ;\n: padding ( -- ) ;\n: f ( s:str p:choice<padding> -- ) padding ;\n"));
    }

    public void testRenameChoiceType() {
        myFixture.configureByText("a.fth", "CHOICE: pad<caret>ding\n  option none ;\n;\n: f ( s:str p:choice<padding>=none -- ) ;\n");
        myFixture.renameElementAtCaret("pad");
        assertEquals("CHOICE: pad\n  option none ;\n;\n: f ( s:str p:choice<pad>=none -- ) ;\n", myFixture.getFile().getText());
    }

    public void testRenameLocal() {
        myFixture.configureByText("a.fth", ": f ( -- int ) 1 :> a<caret>a aa aa + ;\n");
        myFixture.renameElementAtCaret("b+");
        assertEquals(": f ( -- int ) 1 :> b+ b+ b+ + ;\n", myFixture.getFile().getText());
    }

    public void testNewCoreWordsResolve() {
        assertEmpty(diag(": f ( l:list -- int ) l sum 2.0 ln drop [ 1 emit ] collect drop \"12\" str>num ;\n"));
    }

    // ---- diagnostics ------------------------------------------------------------------------------------

    public void testBindPlacement() {
        assertEquals(List.of("E_SYNTAX::>"), diag(": f ( -- ) ;\n1 :> x\n").stream().filter(s -> !s.startsWith("E_TOPLEVEL")).toList());
        assertEquals(List.of("E_SYNTAX::>"), diag(": f ( -- list ) { 1 :> x } ;\n"));
        assertEquals(List.of("E_REDEFINED:n"), diag(": f ( n:int -- ) 1 :> n ;\n"));
        assertEquals(List.of("E_REDEFINED:a"), diag(": f ( -- ) 1 :> a 2 :> a ;\n"));
        // sibling scopes may reuse a name
        assertEmpty(diag(": f ( b:bool -- ) b [ 1 :> a a drop ] [ 2 :> a a drop ] if ;\n"));
        assertEquals(List.of("W_SHADOW:dup"), diag(": f ( -- ) 1 :> dup ;\n"));
    }

    public void testExitPlacement() {
        assertEmpty(diag(": f ( b:bool -- ) b [ exit ] when exit ;\n"));
        assertEmpty(diag(": f ( b:bool -- ) b [ [ exit ] [ ] if ] when ;\n"));
        assertEquals(List.of("E_SYNTAX:exit"), diag(": f ( l:list -- ) l [ exit ] each ;\n"));
        assertEquals(List.of("E_SYNTAX:exit"), diag(": f ( -- ) [ true ] [ 1 :> x exit ] while ;\n"));
        assertEquals(List.of("E_SYNTAX:exit"), diag(": f ( -- list ) { exit } ;\n"));
        // a vocabulary's own exit is not core:exit
        assertEmpty(diag(": exit ( -- ) ;\n: f ( l:list -- ) l [ exit ] each ;\n"));
    }

    public void testMemoHeaderAndPurity() {
        assertEmpty(diag("MEMO: t ( -- record ) { } >record ;\n"));
        assertEquals(List.of("E_HEADER:( n:int -- int )"), diag("MEMO: t ( n:int -- int ) n ;\n"));
        assertEquals(List.of("E_HEADER:( -- quot )"), diag("MEMO: t ( -- quot ) [ ] ;\n"));
        assertEquals(List.of("E_HEADER:( -- int int )"), diag("MEMO: t ( -- int int ) 1 2 ;\n"));
        assertEquals(List.of("E_FORBIDDEN:."), diag("MEMO: t ( -- int ) 1 dup . ;\n"));
        assertEquals(List.of("E_FORBIDDEN:c"), diag("0 VARIABLE c\nMEMO: t ( -- int ) c ;\n"));
        assertEquals(List.of("E_SYNTAX:recurse"), diag("MEMO: t ( -- int ) recurse ;\n"));
    }

    public void testChoiceDiagnostics() {
        assertEquals(List.of("E_UNDEFINED:nope"), diag(": f ( s:str p:choice<nope> -- ) ;\n"));
        assertEquals(List.of("E_HEADER:p:choice<mode>=ecb"),
                diag("CHOICE: mode option cbc ; option ctr ; ;\n: f ( s:str p:choice<mode>=ecb -- ) ;\n"));
        assertEmpty(diag("CHOICE: mode option cbc ; option \"c t r\" ; ;\n: f ( s:str p:choice<mode>=\"c t r\" -- ) ;\n"));
        assertEquals(List.of("E_META:cbc"), diag("CHOICE: mode option cbc ; option cbc ; ;\n"));
        assertEquals(List.of("E_REDEFINED:mode"), diag("CHOICE: mode option a ; ;\nCHOICE: mode option b ; ;\n"));
        assertEquals(List.of("E_META:;"), diag("CHOICE: mode ;\n"));
    }

    public void testMetaV02Clauses() {
        String def = "IN: v\n: f ( a:int -- ) ;\n: g ( a:int -- ) ;\n";
        assertEquals(List.of("E_META:\"\""), diag(def + "META: f use-when \"\" ; ;\n"));
        assertEquals(List.of("E_META:\" x\""), diag(def + "META: f use-when \" x\" ; ;\n"));
        assertEquals(List.of("E_META:\"http://example.com\""), diag(def + "META: f link \"http://example.com\" ; ;\n"));
        assertEquals(List.of("E_META:\"https://localhost/x\""), diag(def + "META: f link \"https://localhost/x\" ; ;\n"));
        assertEquals(List.of("E_META:\"https://a.example.com\""),
                diag(def + "META: f link \"https://a.example.com\" ; link \"https://a.example.com\" ; ;\n"));
        assertEmpty(diag(def + "META: f link \"https://a.example.com:8443/p?q=1#f\" \"Label\" ; inverse g ; ;\n"));
        // the inverse may be defined later in the unit
        assertEmpty(diag("IN: v\n: f ( a:int -- ) ;\nMETA: f inverse h ; ;\n: h ( a:int -- ) ;\n"));
        assertEquals(List.of("E_META:nope"), diag(def + "META: f inverse nope ; ;\n"));
        assertEquals(List.of("E_META:w:g"), diag("IN: w\n: g ( a:int -- ) ;\n" + def + "META: f inverse w:g ; ;\n"));
    }

    public void testLinkIsAWebReference() {
        myFixture.configureByText("a.fth", ": f ( a:int -- ) ;\nMETA: f link \"https://exa<caret>mple.com\" ; ;\n");
        assertTrue(myFixture.getFile().findReferenceAt(myFixture.getCaretOffset())
                instanceof com.intellij.openapi.paths.WebReference);
    }
}
