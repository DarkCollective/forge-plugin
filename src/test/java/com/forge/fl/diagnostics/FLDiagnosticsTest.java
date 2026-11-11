// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.diagnostics;

import com.forge.fl.FLMultiFileTestBase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Each diagnostic with a positive case and a near-miss that must stay silent. */
public class FLDiagnosticsTest extends FLMultiFileTestBase {
    private static final Pattern CODE = Pattern.compile("\\((E_[A-Z_]+|W_[A-Z_]+)\\)\\s*$");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(FLTopLevelCodeInspection.class, FLShadowInspection.class);
    }

    /** {@code CODE:highlighted text} of every error and weak warning, in position order. */
    private List<String> diag(String text) {
        myFixture.configureByText("m.fth", text);
        return collect();
    }

    private List<String> collect() {
        List<String> out = new ArrayList<>();
        for (HighlightInfo info : myFixture.doHighlighting()) {
            if (info.getDescription() == null) continue;
            if (info.getSeverity() != HighlightSeverity.ERROR && info.getSeverity() != HighlightSeverity.WEAK_WARNING) continue;
            Matcher m = CODE.matcher(info.getDescription());
            if (m.find()) out.add(m.group(1) + ":" + info.getText());
        }
        return out;
    }

    // ---- undefined / vocabularies ------------------------------------------------------------------------

    public void testUndefinedWord() {
        assertEquals(List.of("E_UNDEFINED:nope"), diag(": f ( -- ) nope ;\n"));
        assertEmpty(diag(": g ( -- int ) 1 ;\n: f ( -- int ) g ;\n"));
    }

    public void testUndefinedQualifiedWordAndUnknownVocabulary() {
        add("a.fth", "IN: a\n: foo ( -- ) ;\n");
        assertEquals(List.of("E_UNDEFINED:a:bar"), diag(": f ( -- ) a:bar ;\n"));
        assertEquals(List.of("E_NO_VOCAB:zz"), diag(": f ( -- ) zz:foo ;\n"));
        assertEmpty(diag(": f ( -- ) a:foo core:dup ;\n"));
    }

    public void testUseBeforeDefinition() {
        assertEquals(List.of("E_UNDEFINED:g"), diag(": f ( -- ) g ;\n: g ( -- ) ;\n"));
        assertEmpty(diag(": g ( -- ) ;\n: f ( -- ) g ;\n"));
    }

    public void testSelfReferenceNeedsRecurse() {
        assertEquals(List.of("E_UNDEFINED:f"), diag(": f ( n:int -- ) f ;\n"));
        assertEmpty(diag(": f ( n:int -- ) recurse ;\n"));
    }

    public void testUsingUnknownVocabulary() {
        add("a.fth", "IN: a\n: x ( -- ) ;\n");
        assertEquals(List.of("E_NO_VOCAB:nonexist"), diag("USING: nonexist ;\n"));
        assertEmpty(diag("USING: core a ;\n"));
    }

    public void testInvalidVocabularyName() {
        assertEquals(List.of("E_NAME:a.b"), diag("IN: a.b\n"));
        assertEmpty(diag("IN: a-b\n"));
    }

    // ---- ambiguity, privacy --------------------------------------------------------------------------------

    public void testAmbiguousNameAndQuickFixes() {
        add("a.fth", "IN: a\n: x ( -- ) ;\n");
        add("b.fth", "IN: b\n: x ( -- ) ;\n");
        assertEquals(List.of("E_AMBIGUOUS:x"), diag("USING: a b ;\n: f ( -- ) x ;\n"));
        assertEmpty(diag("USING: a b ;\n: f ( -- ) a:x ;\n"));
        assertEmpty(diag("USING: a ;\n: f ( -- ) x ;\n"));

        myFixture.configureByText("q.fth", "USING: a b ;\n: f ( -- ) <caret>x ;\n");
        myFixture.doHighlighting();
        List<String> fixes = new ArrayList<>();
        for (IntentionAction a : myFixture.getAvailableIntentions()) {
            if (a.getText().startsWith("Qualify as")) fixes.add(a.getText());
        }
        assertEquals(List.of("Qualify as 'a:x'", "Qualify as 'b:x'"), fixes);
        myFixture.launchAction(myFixture.findSingleIntention("Qualify as 'b:x'"));
        myFixture.checkResult("USING: a b ;\n: f ( -- ) b:x ;\n");
    }

    public void testAmbiguityMessageListsCandidates() {
        add("a.fth", "IN: a\n: x ( -- ) ;\n");
        add("b.fth", "IN: b\n: x ( -- ) ;\n");
        myFixture.configureByText("m.fth", "USING: a b ;\n: f ( -- ) x ;\n");
        boolean found = false;
        for (HighlightInfo i : myFixture.doHighlighting()) {
            if (i.getDescription() != null && i.getDescription().contains("a:x, b:x")) found = true;
        }
        assertTrue(found);
    }

    public void testPrivateWordFromAnotherVocabulary() {
        add("a.fth", "IN: a\n: _p ( -- ) ;\n");
        assertEquals(List.of("E_PRIVATE:a:_p"), diag(": f ( -- ) a:_p ;\n"));
        assertEmpty(diag("IN: a\n: f ( -- ) a:_p _p ;\n"));
    }

    // ---- redefinition ----------------------------------------------------------------------------------------

    public void testRedefinedWord() {
        assertEquals(List.of("E_REDEFINED:f"), diag(": f ( -- ) ;\n: f ( -- ) ;\n"));
        assertEmpty(diag("IN: a\n: f ( -- ) ;\nIN: b\n: f ( -- ) ;\n"));
    }

    public void testRedefinedConstantVariableSetterAndAlias() {
        assertEquals(List.of("E_REDEFINED:x"), diag("1 CONSTANT x\n2 CONSTANT x\n"));
        assertEquals(List.of("E_REDEFINED:x!"), diag("1 VARIABLE x\n: x! ( int -- ) ;\n"));
        assertEquals(List.of("E_REDEFINED:g"), diag(": f ( -- ) ;\n: g ( -- ) ;\nMETA: f alias g ; ;\n"));
        assertEmpty(diag(": f ( -- ) ;\nMETA: f alias g ; ;\n"));
    }

    // ---- names and headers -------------------------------------------------------------------------------------

    public void testMissingHeaderIsReportedWithItsCode() {
        myFixture.configureByText("m.fth", ": f 1 ;\n");
        boolean found = false;
        for (HighlightInfo i : myFixture.doHighlighting()) {
            if (i.getDescription() != null && i.getDescription().contains("(E_HEADER)")) found = true;
        }
        assertTrue(found);
    }

    public void testQualifiedDefinitionNameIsENameWithItsCode() {
        myFixture.configureByText("m.fth", ": a:b ( -- ) ;\n");
        boolean found = false;
        for (HighlightInfo i : myFixture.doHighlighting()) {
            if (i.getDescription() != null && i.getDescription().contains("(E_NAME)")) found = true;
        }
        assertTrue(found);
    }

    public void testHeaderWithoutArrow() {
        assertEquals(List.of("E_HEADER:( int )"), diag(": f ( int ) ;\n"));
        assertEmpty(diag(": f ( int -- ) ;\n"));
    }

    public void testDuplicateAndReservedParameterNames() {
        assertEquals(List.of("E_HEADER:a"), diag(": f ( a:int a:int -- ) ;\n"));
        assertEmpty(diag(": f ( a:int b:int -- ) ;\n"));
        assertEquals(List.of("E_HEADER:recurse"), diag(": f ( recurse:int -- ) ;\n"));
        assertEquals(List.of("E_HEADER:Bad"), diag(": f ( Bad:int -- ) ;\n"));
    }

    // ---- META: --------------------------------------------------------------------------------------------------

    public void testMetaTargetUndefinedOrLater() {
        assertEquals(List.of("E_META:g"), diag(": f ( -- ) ;\nMETA: g doc \"x\" ; ;\n"));
        assertEquals(List.of("E_META:f"), diag("META: f doc \"x\" ; ;\n: f ( -- ) ;\n"));
        assertEmpty(diag(": f ( -- ) ;\nMETA: f doc \"x\" ; ;\n"));
    }

    public void testMetaTargetInAnotherFileOfTheVocabularyIsFine() {
        add("a.fth", "IN: a\n: w ( -- ) ;\n");
        assertEmpty(diag("IN: a\nMETA: w doc \"x\" ; ;\n"));
        assertEquals(List.of("E_META:w"), diag("IN: b\nMETA: w doc \"x\" ; ;\n"));
    }

    public void testMetaOnPrivateWord() {
        assertEquals(List.of("E_META:_f"), diag(": _f ( -- ) ;\nMETA: _f doc \"x\" ; ;\n"));
    }

    public void testUnknownMetaClauseCarriesItsCode() {
        myFixture.configureByText("m.fth", ": f ( -- ) ;\nMETA: f bogus \"x\" ; ;\n");
        boolean found = false;
        for (HighlightInfo i : myFixture.doHighlighting()) {
            if (i.getDescription() != null && i.getDescription().contains("(E_META)")) found = true;
        }
        assertTrue(found);
    }

    public void testMetaParamMustNameAParameterOtherThanTheFirstInput() {
        String def = ": f ( data:str amount:int -- str ) ;\n";
        assertEquals(List.of("E_META:nope"), diag(def + "META: f param nope \"x\" ; ;\n"));
        assertEquals(List.of("E_META:data"), diag(def + "META: f param data \"x\" ; ;\n"));
        assertEmpty(diag(def + "META: f param amount \"x\" ; ;\n"));
        assertEquals(List.of("E_META:x"), diag("1 CONSTANT c\nMETA: c param x \"y\" ; ;\n"));
    }

    // ---- top-level code, VOCAB, shadowing (weak) ----------------------------------------------------------------

    public void testTopLevelCodeIsAWeakWarning() {
        assertEquals(List.of("E_TOPLEVEL:1"), diag("1 2 +\n"));
        assertEmpty(diag("256 CONSTANT x\n: f ( -- ) 1 ;\n"));
    }

    public void testVocabInAFileIsAWeakWarning() {
        assertEquals(List.of("E_SYNTAX:VOCAB"), diag("VOCAB demo\n"));
        assertEmpty(diag("IN: demo\n"));
    }

    public void testParameterShadowsAVisibleWord() {
        assertEquals(List.of("W_SHADOW:dup"), diag(": f ( dup:int -- ) ;\n"));
        assertEquals(List.of("W_SHADOW:g"), diag(": g ( -- ) ;\n: f ( g:int -- ) ;\n"));
        assertEmpty(diag(": f ( zzzz:int -- ) ;\n"));
        assertEmpty(diag(": f ( g:int -- ) ;\n: g ( -- ) ;\n")); // g is not yet defined at the header of f
    }

    public void testNothingIsReportedForBuiltInFilesOrWhenValid() {
        assertEmpty(diag("IN: demo\nUSING: core ;\n: f ( a:int b:int -- int ) a b + ;\nMETA: f param b \"second\" ; ;\n"));
    }
}
