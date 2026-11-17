// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.effects;

import com.forge.fl.FLTestBase;
import com.forge.fl.psi.FLDefinition;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.List;
import java.util.Map;

public class FLStackEffectsTest extends FLTestBase {
    private PsiFile file;

    private FLDefinition word(String name) {
        return PsiTreeUtil.findChildrenOfType(file, FLDefinition.class).stream()
                .filter(d -> name.equals(d.getName())).findFirst().orElseThrow();
    }

    /** The problems of word {@code name} in {@code text}, as "text-at: message". */
    private List<String> problems(String text, String name) {
        file = myFixture.configureByText("t.fth", text);
        return FLStackAnalysis.of(word(name)).problems().stream()
                .map(p -> p.at().getText() + ": " + p.message()).toList();
    }

    private void assertNoProblems(String text, String name) {
        assertEquals(List.of(), problems(text, name));
    }

    public void testHeaderEffects() {
        file = myFixture.configureByText("t.fth", """
                IN: t
                : a ( s:str bytes n:int=1 -- str int ) s ;
                : b ( .. n:int -- .. ) ;
                : c ( str pattern:regex flags:str[0..3,/[ims]*/]="" -- list ) ;
                """);
        FLEffect a = FLEffect.of(word("a").getHeader());
        assertEquals(3, a.arity());
        assertEquals(1, a.anonymousInputs());
        assertEquals(java.util.Arrays.asList("s", null, "n"), a.inputs().stream().map(FLEffect.Input::name).toList());
        assertEquals(List.of("str", "int"), a.outputs());
        assertEquals("( s:str bytes n:int=1 -- str int )", a.text());
        FLEffect b = FLEffect.of(word("b").getHeader());
        assertTrue(b.variadicIn());
        assertTrue(b.uncheckedOut());
        FLEffect c = FLEffect.of(word("c").getHeader());
        assertFalse("a .. inside a constraint is not the variadic marker", c.variadicIn());
        assertEquals(3, c.arity());
    }

    public void testCorrectBodies() {
        assertNoProblems("IN: t\n: f ( a:int -- int ) a 1 + ;\n", "f");
        assertNoProblems("IN: t\n: f ( str -- int ) len ;\n", "f");                       // an anonymous input stays
        assertNoProblems("IN: t\n: f ( a:int b:int -- int int ) a b swap ;\n", "f");
        assertNoProblems("IN: t\n: f ( -- int ) 1 2 :> x x + ;\n", "f");
        assertNoProblems("IN: t\n3 CONSTANT k\n0 VARIABLE v\n: f ( -- int ) k v! v ;\n", "f");
        assertNoProblems("IN: t\n: f ( n:int -- int ) n 0 = [ 1 ] [ n 1 - recurse n * ] if ;\n", "f");
        assertNoProblems("IN: t\n: f ( -- .. ) 1 2 3 ;\n", "f");                           // unchecked output
        assertNoProblems("IN: t\n: f ( x:int -- int ) [ x 1 + ] call ;\n", "f");
        assertNoProblems("IN: t\n: f ( -- int ) [ 1 ] [ drop 2 ] try ;\n", "f");           // the handler gets the record
        assertNoProblems("IN: t\n: f ( -- int ) 1 2 3 clear 4 ;\n", "f");
        assertNoProblems("IN: t\n: f ( l:list -- list ) l [ 1 + ] map ;\n", "f");
        assertNoProblems("IN: t\n: f ( l:list -- int ) l 0 [ + ] reduce ;\n", "f");
        assertNoProblems("IN: t\n: f ( l:list -- ) l [ drop ] each ;\n", "f");
        assertNoProblems("IN: t\n: f ( -- ) [ 1 0 > ] [ ] while ;\n", "f");
        assertNoProblems("IN: t\n: f ( -- list ) [ 1 emit 2 emit ] collect ;\n", "f");
    }

    public void testEndOfBodyMismatch() {
        assertEquals(List.of(";: E_EFFECT: at the end of the body the stack holds 2 values, but the header "
                        + "( a:int -- int ) declares 1 value"),
                problems("IN: t\n: f ( a:int -- int ) a 1 ;\n", "f"));
        assertEquals(List.of(";: E_EFFECT: at the end of the body the stack holds 0 values, but the header "
                        + "( -- str ) declares 1 value"),
                problems("IN: t\n: f ( -- str ) ;\n", "f"));
    }

    public void testUnderflow() {
        assertEquals(List.of("+: E_STACK_UNDERFLOW: '+' takes 2 values, but the stack holds only 1 here"),
                problems("IN: t\n: f ( a:int -- int ) a + ;\n", "f"));
        assertEquals(List.of(":> x: E_STACK_UNDERFLOW: ':>' binds the top of the stack, but the stack is empty here"),
                problems("IN: t\n: f ( -- int ) :> x x ;\n", "f"));
        // a named input is a local, not a value on the stack
        assertEquals(List.of("len: E_STACK_UNDERFLOW: 'len' takes 1 value, but the stack holds none here"),
                problems("IN: t\n: f ( s:str -- int ) len ;\n", "f"));
    }

    public void testBranches() {
        assertNoProblems("IN: t\n: f ( b:bool -- int ) b [ 1 ] [ 2 ] if ;\n", "f");
        assertEquals(List.of("if: E_EFFECT: the two ways through 'if' leave 1 and 2 values on the stack"),
                problems("IN: t\n: f ( b:bool -- int ) b [ 1 ] [ 1 2 ] if ;\n", "f"));
        assertEquals(List.of("when: E_EFFECT: the two ways through 'when' leave 2 and 1 values on the stack"),
                problems("IN: t\n: f ( b:bool -- int ) 1 b [ 2 ] when ;\n", "f"));
        // a word whose output is unchecked may leave different depths
        assertNoProblems("IN: t\n: f ( b:bool -- .. ) b [ 1 ] [ 1 2 ] if ;\n", "f");
        // problems inside a branch run inline are found too
        assertEquals(List.of("+: E_STACK_UNDERFLOW: '+' takes 2 values, but the stack holds none here"),
                problems("IN: t\n: f ( b:bool -- int ) b [ + ] [ 2 ] if ;\n", "f"));
    }

    public void testFailureAndExitEndPaths() {
        assertNoProblems("IN: t\n: f ( b:bool -- int ) b [ 1 ] [ \"no\" fail-coerce ] if ;\n", "f");
        assertNoProblems("IN: t\n: _no ( s:str -- ) \"no: \" s cat fail-range ;\n: f ( b:bool -- int ) b [ 1 ] [ \"x\" _no ] if ;\n", "f");
        assertNoProblems("IN: t\n: f ( b:bool -- int ) b [ 1 exit ] when 2 ;\n", "f");
        assertEquals(List.of("exit: E_EFFECT: at this exit the stack holds 0 values, but the header ( b:bool -- int ) "
                        + "declares 1 value"),
                problems("IN: t\n: f ( b:bool -- int ) b [ exit ] when 2 ;\n", "f"));
    }

    public void testLoopQuotationRules() {
        assertEquals(List.of("[ 1 + 2 ]: E_EFFECT: a quotation run by 'map' must change the stack by 0 (it receives the "
                        + "element), but this one changes it by +1"),
                problems("IN: t\n: f ( l:list -- list ) l [ 1 + 2 ] map ;\n", "f"));
        assertEquals(List.of("[ ]: E_EFFECT: a quotation run by 'each' must change the stack by -1 (it receives the "
                        + "element), but this one changes it by 0"),
                problems("IN: t\n: f ( l:list -- ) l [ ] each ;\n", "f"));
        assertEquals(List.of("[ 1 ]: E_EFFECT: a quotation run by 'while' must change the stack by 0, but this one "
                        + "changes it by +1"),
                problems("IN: t\n: f ( -- ) [ true ] [ 1 ] while ;\n", "f"));
    }

    public void testUnknownStopsQuietly() {
        assertNoProblems("IN: t\n: f ( -- int ) nope 1 2 ;\n", "f");                       // unresolved
        assertNoProblems("IN: t\n: f ( q:quot -- int ) q call 1 2 ;\n", "f");               // not a literal quotation
        assertNoProblems("IN: t\n: f ( k:int -- int ) k { 1 [ 2 ] } case 3 4 ;\n", "f");     // case is not followed
    }

    public void testDepthHintsPerLine() {
        file = myFixture.configureByText("t.fth", """
                IN: t
                : f ( a:int b:bool -- int )
                  a 1 +
                  dup 2 *
                  b [
                    +
                  ] [ drop ] if
                  "x" drop \\ a comment
                  ;
                """);
        Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        Map<Integer, Integer> lines = FLStackDepthHints.lines(word("f"), doc);
        assertEquals(Map.of(2, 1, 3, 2, 5, 1, 6, 1, 7, 1), lines);
    }

    public void testParameterNameHintsAndInfo() {
        file = myFixture.configureByText("t.fth", """
                IN: t
                : enc ( bytes alphabet:choice{standard|url}=standard padding:bool=true -- str ) drop "x" ;
                : g ( -- str ) "data" "url" false enc ;
                """);
        String text = file.getText();
        int url = text.indexOf("\"url\"");
        int flag = text.indexOf("false");
        assertEquals("alphabet", FLParameterNameHints.parameterName(file.findElementAt(url)));
        assertEquals("padding", FLParameterNameHints.parameterName(file.findElementAt(flag)));
        assertNull("an anonymous input has no name", FLParameterNameHints.parameterName(file.findElementAt(text.indexOf("\"data\""))));

        FLParameterInfoHandler.Target onArg = FLParameterInfoHandler.targetAt(file, url + 2);
        assertNotNull(onArg);
        assertEquals("enc", onArg.call().getText());
        assertEquals(1, onArg.input());
        FLParameterInfoHandler.Presentation p = FLParameterInfoHandler.presentation(onArg.callee().effect(), onArg.input());
        assertEquals("bytes alphabet:choice{standard|url}=standard padding:bool=true -- str", p.text());
        assertEquals("alphabet:choice{standard|url}=standard", p.text().substring(p.start(), p.end()));
        int encAt = text.lastIndexOf("enc");
        FLParameterInfoHandler.Target onWord = FLParameterInfoHandler.targetAt(file, encAt + 1);
        assertEquals(-1, onWord.input());
        assertEquals("enc", onWord.callee().definition().getName());
        // just after the word (the caret after typing it)
        assertEquals("enc", FLParameterInfoHandler.targetAt(file, encAt + 3).call().getText());
    }

    public void testInspectionHighlights() {
        myFixture.enableInspections(new FLStackEffectInspection());
        myFixture.configureByText("t.fth", "IN: t\n: f ( a:int -- int ) a 1 ;\n: g ( -- int ) 1 ;\n");
        List<String> found = myFixture.doHighlighting().stream().map(HighlightInfo::getDescription)
                .filter(d -> d != null && d.startsWith("E_")).toList();
        assertEquals(1, found.size());
        assertTrue(found.get(0).contains("declares 1 value"));
    }

    public void testBuiltinsHaveNoProblems() {
        // the generated built-in files have empty bodies: they must not be analysed as wrong
        file = myFixture.configureByText("t.fth", "IN: t\n: f ( -- int ) 1 2 + ;\n");
        PsiElement plus = PsiTreeUtil.findChildrenOfType(file, com.forge.fl.psi.FLWordRef.class).iterator().next();
        FLCallee callee = FLCallee.of((com.forge.fl.psi.FLWordRef) plus);
        assertNotNull(callee);
        assertTrue(callee.isCore("+"));
        FLStackEffectInspection inspection = new FLStackEffectInspection();
        assertNotNull(inspection);
        assertEquals(List.of(), FLStackAnalysis.of(callee.definition()).problems().stream()
                .filter(x -> !com.forge.fl.builtins.FLBuiltins.isBuiltin(callee.definition())).toList());
    }
}
