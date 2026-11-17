// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.refactoring;

import com.forge.fl.FLTestBase;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLWordRef;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.List;

public class FLRefactoringsTest extends FLTestBase {
    private PsiFile file;

    private PsiFile open(String text) {
        file = myFixture.configureByText("t.fth", text);
        return file;
    }

    private int at(String text) {
        int i = file.getText().indexOf(text);
        assertTrue("not found: " + text, i >= 0);
        return i;
    }

    private FLDefinition word(String name) {
        return PsiTreeUtil.findChildrenOfType(file, FLDefinition.class).stream()
                .filter(d -> name.equals(d.getName())).findFirst().orElseThrow();
    }

    private String refused(Runnable r) {
        try {
            r.run();
            return null;
        } catch (RuntimeException e) {
            return e.getCause() instanceof FLSelection.Refused ? e.getCause().getMessage() : e.getMessage();
        }
    }

    // ---- Introduce :> local ----------------------------------------------------------------------------------

    private FLIntroduceLocal.Plan introduce(String selected) throws FLSelection.Refused {
        int s = at(selected);
        return FLIntroduceLocal.plan(file, s, s + selected.length());
    }

    public void testIntroduceLocalInline() throws Exception {
        open("IN: t\n: f ( a:int b:int c:int -- int ) a b + c * a b + - ;\n");
        FLIntroduceLocal.Plan plan = introduce("a b +");
        assertEquals(1, plan.others().size());
        assertNull(FLIntroduceLocal.nameProblem(plan, "sum"));
        assertEquals("'a' is already a local here (E_REDEFINED)", FLIntroduceLocal.nameProblem(plan, "a"));
        FLIntroduceLocal.perform(plan, "sum", true);
        assertEquals("IN: t\n: f ( a:int b:int c:int -- int ) a b + :> sum sum c * sum - ;\n", file.getText());
    }

    public void testIntroduceLocalAtLineStartBindsOnItsOwnLine() throws Exception {
        open("IN: t\n: f ( s:str -- int )\n  s len 2 *\n  1 + ;\n");
        FLIntroduceLocal.Plan plan = introduce("s len 2 *");
        assertEquals("value", plan.suggestedName());
        FLIntroduceLocal.perform(plan, "twice", false);
        assertEquals("IN: t\n: f ( s:str -- int )\n  s len 2 * :> twice\n  twice\n  1 + ;\n", file.getText());
    }

    public void testIntroduceLocalRefusals() {
        open("IN: t\n: f ( a:int b:int -- list ) a b + { a b + } ;\n");
        assertEquals("The selection must push exactly one value and take none; it takes 2 and leaves 1",
                refused(() -> wrap(() -> introduce("+"))));
        int inList = file.getText().lastIndexOf("a b +");
        assertEquals("':>' is not allowed inside a { } list literal (E_SYNTAX)",
                refused(() -> wrap(() -> FLIntroduceLocal.plan(file, inList, inList + 5))));
        assertEquals("The selection cuts '{ a b + }': select whole words, literals and quotations",
                refused(() -> wrap(() -> FLIntroduceLocal.plan(file, at("b + {"), at("b + {") + 6))));
    }

    private interface Body {
        Object run() throws Exception;
    }

    private static void wrap(Body b) {
        try {
            b.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- Extract word ------------------------------------------------------------------------------------------

    private FLExtractWord.Plan extract(String selected) throws FLSelection.Refused {
        int s = at(selected);
        return FLExtractWord.plan(file, s, s + selected.length());
    }

    public void testExtractWordWithLocals() throws Exception {
        open("IN: t\n\\ Docs of f.\n: f ( a:int b:int n:int=2 -- int )\n  a b + n * 1 + ;\n");
        FLExtractWord.Plan plan = extract("a b + n *");
        assertEquals("( a:int b:int n:int -- any )", plan.header());
        assertEquals("_f-part", plan.suggestedName());
        FLExtractWord.perform(plan, "sum-times");
        assertEquals("IN: t\n: sum-times ( a:int b:int n:int -- any )\n  a b + n * ;\n\n\\ Docs of f.\n"
                + ": f ( a:int b:int n:int=2 -- int )\n  a b n sum-times 1 + ;\n", file.getText());
    }

    public void testExtractWordWithStackValues() throws Exception {
        open("IN: t\n: f ( int int -- int )\n  + 2 *\n  1 + ;\n");
        FLExtractWord.Plan plan = extract("+ 2 *\n  1 +");
        assertEquals("( any any -- any )", plan.header());
        assertTrue(FLExtractWord.nameProblem(plan, "combine").contains("private name starting with _"));
        assertNull(FLExtractWord.nameProblem(plan, "_combine"));
        assertEquals("'f' is already defined in this vocabulary (E_REDEFINED)", FLExtractWord.nameProblem(plan, "f"));
        FLExtractWord.perform(plan, "_combine");
        assertEquals("IN: t\n: _combine ( any any -- any )\n  + 2 *\n  1 + ;\n\n: f ( int int -- int )\n  _combine ;\n", file.getText());
    }

    public void testExtractWordRefusals() {
        open("IN: t\n: f ( n:int -- int ) n 0 = [ 1 exit ] when n 1 - recurse 2 :> k k ;\n");
        assertEquals("The selection uses 'exit', which would leave the new word instead",
                refused(() -> wrap(() -> extract("n 0 = [ 1 exit ] when"))));
        assertEquals("The selection uses 'recurse', which would call the new word",
                refused(() -> wrap(() -> extract("n 1 - recurse"))));
        assertEquals("The local 'k' is bound in the selection and used after it",
                refused(() -> wrap(() -> extract("2 :> k"))));
    }

    // ---- Inline word -------------------------------------------------------------------------------------------

    public void testInlineAllAndRemove() throws Exception {
        open("IN: t\n\\ Square.\n: sq ( int -- int ) dup * ;\n\nMETA: sq\n  doc \"Square.\" ;\n;\n\n: f ( -- int ) 5 sq 3 sq + ;\n");
        FLInlineWord.Plan plan = FLInlineWord.plan(word("sq"));
        assertEquals("dup *", plan.code());
        assertEquals(2, plan.inlinable().size());
        assertTrue(FLInlineWord.perform(plan, plan.inlinable(), true));
        assertEquals("IN: t\n: f ( -- int ) 5 dup * 3 dup * + ;\n", file.getText());
    }

    public void testInlineNamedInputsSubstitutedOrScoped() throws Exception {
        open("IN: t\n: twice ( n:int -- int ) n 2 * ;\n: g ( -- int ) 5 twice ;\n: g2 ( -- int ) 2 3 + twice ;\n"
                + ": h ( n:int -- int ) n twice ;\n: k ( m:int -- int ) m twice m + ;\n");
        FLInlineWord.Plan plan = FLInlineWord.plan(word("twice"));
        assertEquals("[ :> n n 2 * ] call", plan.code());
        assertEquals(3, plan.inlinable().size());
        FLWordRef inH = PsiTreeUtil.findChildrenOfType(word("h"), FLWordRef.class).stream()
                .filter(r -> r.getText().equals("twice")).findFirst().orElseThrow();
        assertEquals("the caller has a local 'n', which the inlined code would bind again", plan.calls().get(inH));
        assertFalse("a call stays, so the word stays", FLInlineWord.perform(plan, plan.inlinable(), true));
        // a literal or a local argument is written into the body; a computed one is bound in a quotation
        assertEquals("IN: t\n: twice ( n:int -- int ) n 2 * ;\n: g ( -- int ) 5 2 * ;\n: g2 ( -- int ) 2 3 + [ :> n n 2 * ] call ;\n"
                + ": h ( n:int -- int ) n twice ;\n: k ( m:int -- int ) m 2 * m + ;\n", file.getText());
    }

    public void testInlineRefusals() {
        open("IN: t\nMEMO: m ( -- int ) 1 ;\n: e ( b:bool -- int ) b [ 1 exit ] when 2 ;\n: x ( a:int str -- ) drop ;\n");
        assertEquals("A MEMO: word is computed once; inlining it would change that", refused(() -> wrap(() -> FLInlineWord.plan(word("m")))));
        assertEquals("'e' uses exit, which would leave the caller", refused(() -> wrap(() -> FLInlineWord.plan(word("e")))));
        assertEquals("A named input of 'x' is below an anonymous one", refused(() -> wrap(() -> FLInlineWord.plan(word("x")))));
    }

    // ---- Change signature ----------------------------------------------------------------------------------------

    private static final String PAD = """
            IN: t
            : _p ( a:str b:int c:str -- str ) a ;
            : pad ( s:str width:int fill:str=" " -- str ) s width fill _p ;

            META: pad
              doc "Pad." ;
              param width "Width" ;
              param fill "Fill" ;
            ;

            : g ( -- str ) "x" 5 "-" pad ;
            : h ( -- str ) "x" 2 3 + "-" pad ;
            """;

    public void testChangeSignatureReordersRenamesAndAdds() {
        open(PAD);
        List<FLChangeSignature.Row> rows = List.of(
                new FLChangeSignature.Row(0, "s:str", null),
                new FLChangeSignature.Row(2, "ch:str=\" \"", null),
                new FLChangeSignature.Row(1, "width:int", null),
                new FLChangeSignature.Row(-1, "right:bool=false", "true"));
        FLChangeSignature.Plan plan = FLChangeSignature.plan(word("pad"), rows, "str");
        assertEquals(List.of(), plan.errors());
        assertEquals(1, plan.skippedCalls().size());
        assertTrue(plan.skippedCalls().get(0), plan.skippedCalls().get(0).contains("not simple values right before the call"));
        FLChangeSignature.perform(getProject(), plan);
        assertEquals("""
                IN: t
                : _p ( a:str b:int c:str -- str ) a ;
                : pad ( s:str ch:str=" " width:int right:bool=false -- str ) s width ch _p ;

                META: pad
                  doc "Pad." ;
                  param ch "Fill" ;
                  param width "Width" ;
                ;

                : g ( -- str ) "x" "-" 5 true pad ;
                : h ( -- str ) "x" 2 3 + "-" pad ;
                """, file.getText());
    }

    public void testChangeSignatureRemovesAnUnusedParameterAndItsClause() {
        open("IN: t\n: w ( s:str n:int=1 -- str ) s ;\n\nMETA: w\n  param n \"N\" ;\n;\n\n: g ( -- str ) \"x\" 7 w ;\n");
        FLChangeSignature.Plan plan = FLChangeSignature.plan(word("w"), List.of(new FLChangeSignature.Row(0, "s:str", null)), "str");
        assertEquals(List.of(), plan.errors());
        FLChangeSignature.perform(getProject(), plan);
        assertEquals("IN: t\n: w ( s:str -- str ) s ;\n\nMETA: w\n;\n\n: g ( -- str ) \"x\" w ;\n", file.getText());
    }

    public void testChangeSignatureErrors() {
        open(PAD);
        FLDefinition pad = word("pad");
        assertEquals(List.of("The parameter 'width' is used in the body and cannot be removed"),
                FLChangeSignature.plan(pad, List.of(new FLChangeSignature.Row(0, "s:str", null),
                        new FLChangeSignature.Row(2, "fill:str", null)), "str").errors());
        assertEquals(List.of("Give the new input 'x:int' a value at calls"),
                FLChangeSignature.plan(pad, List.of(new FLChangeSignature.Row(0, "s:str", null), new FLChangeSignature.Row(1, "width:int", null),
                        new FLChangeSignature.Row(2, "fill:str", null), new FLChangeSignature.Row(-1, "x:int", "")), "str").errors());
        open("IN: t\n: _q ( str bytes -- str ) drop ;\n");
        assertEquals(List.of("Anonymous inputs stay on the stack in the body: they cannot be added, removed or reordered"),
                FLChangeSignature.plan(word("_q"), List.of(new FLChangeSignature.Row(1, "bytes", null), new FLChangeSignature.Row(0, "str", null)), "str").errors());
        List<String> duplicate = FLChangeSignature.plan(word("_q"), List.of(new FLChangeSignature.Row(0, "str", null),
                new FLChangeSignature.Row(1, "bytes", null), new FLChangeSignature.Row(-1, "a:int", "1"),
                new FLChangeSignature.Row(-1, "a:str", "\"\"")), "str").errors();
        assertEquals(1, duplicate.size());
        assertTrue(duplicate.get(0), duplicate.get(0).contains("E_HEADER") || duplicate.get(0).toLowerCase().contains("duplicate"));
    }

    public void testChangeSignatureTarget() {
        open(PAD);
        assertEquals("pad", FLChangeSignature.target(file.findElementAt(file.getText().lastIndexOf("pad"))).getName());
        assertEquals("pad", FLChangeSignature.target(file.findElementAt(at("width fill _p"))).getName());
        assertNull("a core word", FLChangeSignature.target(file.findElementAt(file.getText().indexOf("+"))));
    }
}
