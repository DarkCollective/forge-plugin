package com.forge.fl.psi;

import com.forge.fl.FLLanguage;
import com.forge.fl.FLTestBase;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.usageView.UsageInfo;

import java.util.Collection;

public class FLRenameTest extends FLTestBase {
    private void rename(String before, String newName, String after) {
        myFixture.configureByText("t.fth", before);
        myFixture.renameElementAtCaret(newName);
        myFixture.checkResult(after);
    }

    // ---- resolve ----

    private PsiElement resolveAtCaret(String text) {
        myFixture.configureByText("t.fth", text);
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull("no reference at caret", ref);
        return ref.resolve();
    }

    public void testBodyWordResolvesToEarlierDefinition() {
        PsiElement target = resolveAtCaret(": foo ( -- int ) 1 ;\n: bar ( -- int ) fo<caret>o ;\n");
        assertTrue(target instanceof FLDefinition);
        assertEquals(myFixture.getFile().getText().indexOf("foo"), target.getTextOffset());
    }

    public void testInsideItsOwnBodyANameMeansTheEarlierDefinition() {
        PsiElement target = resolveAtCaret(": f ( -- int ) 1 ;\n: f ( -- int ) <caret>f ;\n");
        assertEquals(2, ((FLDefinition) target).getTextOffset());
    }

    public void testForwardReferenceFallsBackToAnyDeclaration() {
        PsiElement target = resolveAtCaret(": a ( -- ) <caret>b ;\n: b ( -- ) ;\n");
        assertEquals("b", ((FLDefinition) target).getName());
    }

    public void testParameterShadowsWords() {
        PsiElement target = resolveAtCaret(": x ( -- int ) 7 ;\n: f ( x:int -- int ) <caret>x ;\n");
        assertTrue(target instanceof FLParameter);
    }

    public void testBareLabelIsAParameter() {
        PsiElement target = resolveAtCaret(": _add ( a b -- c ) a <caret>b + ;\n");
        assertTrue(target instanceof FLParameter);
        assertEquals("b", ((FLParameter) target).getName());
    }

    public void testVocabularyScoping() {
        assertNull(resolveAtCaret("IN: a\n: x ( -- ) ;\nIN: b\n: y ( -- ) <caret>x ;\n"));
        assertNotNull(resolveAtCaret("IN: a\n: x ( -- ) ;\n: y ( -- ) <caret>x ;\n"));
        assertEquals("x", ((FLDefinition) resolveAtCaret("IN: a\n: x ( -- ) ;\nIN: b\n: y ( -- ) <caret>a:x ;\n")).getName());
        // default vocabulary is user
        assertNotNull(resolveAtCaret(": x ( -- ) ;\n: y ( -- ) <caret>x ;\n"));
        // qualified resolves only through the named vocabulary
        assertNull(resolveAtCaret("IN: a\n: x ( -- ) ;\nIN: b\n: x ( -- ) ;\n: y ( -- ) <caret>a:zzz ;\n"));
    }

    public void testUnresolvedIsSoft() {
        myFixture.configureByText("t.fth", ": f ( -- ) <caret>nosuchword other:thing ;\n");
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull(ref);
        assertTrue(ref.isSoft());
        assertNull(ref.resolve());
        myFixture.checkHighlighting(); // no error highlighting
    }

    public void testVariableBangResolvesToVariable() {
        PsiElement target = resolveAtCaret("0 VARIABLE counter\n: bump ( -- ) counter 1 + count<caret>er! ;\n");
        assertTrue(target instanceof FLVariable);
    }

    public void testMetaTargetParamAndSeeAlso() {
        String base = ": shift ( text:str amount:int -- str ) text ;\n: other ( -- ) ;\n";
        assertTrue(resolveAtCaret(base + "META: sh<caret>ift\n  doc \"x\" ;\n;\n") instanceof FLDefinition);
        PsiElement p = resolveAtCaret(base + "META: shift\n  param am<caret>ount \"x\" ;\n;\n");
        assertTrue(p instanceof FLParameter);
        assertEquals("amount", ((FLParameter) p).getName());
        assertTrue(resolveAtCaret(base + "META: shift\n  see-also ot<caret>her ;\n;\n") instanceof FLDefinition);
        assertNull(resolveAtCaret(base + "META: shift\n  param no<caret>pe \"x\" ;\n;\n"));
    }

    // ---- find usages ----

    public void testFindUsages() {
        myFixture.configureByText("t.fth", ": fo<caret>o ( -- int ) 1 ;\n: bar ( -- int ) foo foo ;\nMETA: foo\n  doc \"x\" ;\n;\n");
        PsiElement decl = myFixture.getElementAtCaret();
        assertTrue(decl instanceof FLDefinition);
        Collection<UsageInfo> usages = myFixture.findUsages(decl);
        assertEquals(3, usages.size()); // two in bar, the META: target
    }

    public void testFindUsagesOfSymbolNamedWord() {
        myFixture.configureByText("t.fth", ": >he<caret>x ( n:int -- str ) n ;\n: use ( n:int -- str ) n >hex >hex ;\n");
        Collection<UsageInfo> usages = myFixture.findUsages(myFixture.getElementAtCaret());
        assertEquals(2, usages.size());
    }

    // ---- rename ----

    public void testRenameColonWordFromDeclaration() {
        rename("IN: v\n: fo<caret>o ( -- int ) 1 ;\n: bar ( -- int ) foo foo ;\nMETA: foo\n  doc \"x\" ;\n;\n", "baz",
                "IN: v\n: baz ( -- int ) 1 ;\n: bar ( -- int ) baz baz ;\nMETA: baz\n  doc \"x\" ;\n;\n");
    }

    public void testRenameColonWordFromUsage() {
        rename(": foo ( -- int ) 1 ;\n: bar ( -- int ) foo fo<caret>o ;\n", "baz",
                ": baz ( -- int ) 1 ;\n: bar ( -- int ) baz baz ;\n");
    }

    public void testRenamePrivateWord() {
        rename(": _he<caret>lper ( -- int ) 1 ;\n: pub ( -- int ) _helper ;\n", "_aid",
                ": _aid ( -- int ) 1 ;\n: pub ( -- int ) _aid ;\n");
    }

    public void testRenameSymbolNamedWordFromDeclarationAndUsage() {
        String before = ": >he<caret>x ( n:int -- str ) n ;\n: use ( n:int -- str ) n >hex ;\n";
        rename(before, "to-hex", ": to-hex ( n:int -- str ) n ;\n: use ( n:int -- str ) n to-hex ;\n");
        rename(": >hex ( n:int -- str ) n ;\n: use ( n:int -- str ) n >he<caret>x ;\n", "+x",
                ": +x ( n:int -- str ) n ;\n: use ( n:int -- str ) n +x ;\n");
        rename(": <caret>+ ( a b -- c ) a ;\n: use ( a b -- c ) a b + ;\n", "plus",
                ": plus ( a b -- c ) a ;\n: use ( a b -- c ) a b plus ;\n");
    }

    public void testRenameParameterLeavesSameNamedWordAlone() {
        String before = ": x ( -- int ) 7 ;\n: f ( <caret>x:int -- int ) x x + ;\n: g ( -- int ) x ;\n";
        String after = ": x ( -- int ) 7 ;\n: f ( y:int -- int ) y y + ;\n: g ( -- int ) x ;\n";
        rename(before, "y", after);
        rename(": x ( -- int ) 7 ;\n: f ( x:int -- int ) x <caret>x + ;\n: g ( -- int ) x ;\n", "y", after);
    }

    public void testRenameBareLabelParameter() {
        rename(": _add ( <caret>a b -- c ) a b + ;\n", "first", ": _add ( first b -- c ) first b + ;\n");
    }

    public void testRenameParameterUpdatesMetaParamClause() {
        rename(": shift ( text:str am<caret>ount:int -- str ) text amount ;\nMETA: shift\n  param amount \"Shift\" ;\n;\n",
                "n",
                ": shift ( text:str n:int -- str ) text n ;\nMETA: shift\n  param n \"Shift\" ;\n;\n");
        rename(": shift ( text:str amount:int -- str ) text amount ;\nMETA: shift\n  param am<caret>ount \"Shift\" ;\n;\n",
                "n",
                ": shift ( text:str n:int -- str ) text n ;\nMETA: shift\n  param n \"Shift\" ;\n;\n");
    }

    public void testRenameConstant() {
        rename("IN: v\n256 CONSTANT si<caret>ze\n: f ( -- int ) size size + ;\n", "block",
                "IN: v\n256 CONSTANT block\n: f ( -- int ) block block + ;\n");
        rename("256 CONSTANT size\n: f ( -- int ) size si<caret>ze + ;\n", "block",
                "256 CONSTANT block\n: f ( -- int ) block block + ;\n");
    }

    public void testRenameVariableRewritesBangForm() {
        String after = "0 VARIABLE total\n: bump ( -- ) total 1 + total! ;\n";
        rename("0 VARIABLE cou<caret>nter\n: bump ( -- ) counter 1 + counter! ;\n", "total", after);
        rename("0 VARIABLE counter\n: bump ( -- ) counter 1 + count<caret>er! ;\n", "total", after);
        rename("0 VARIABLE counter\n: bump ( -- ) coun<caret>ter 1 + counter! ;\n", "total", after);
    }

    public void testRenameKeepsVocabPrefixOnQualifiedUsage() {
        rename("IN: a\n: fo<caret>o ( -- int ) 1 ;\nIN: b\n: bar ( -- int ) a:foo ;\n: other ( -- int ) foo ;\n", "baz",
                "IN: a\n: baz ( -- int ) 1 ;\nIN: b\n: bar ( -- int ) a:baz ;\n: other ( -- int ) foo ;\n");
        rename("IN: a\n: foo ( -- int ) 1 ;\nIN: b\n: bar ( -- int ) a:fo<caret>o ;\n", "baz",
                "IN: a\n: baz ( -- int ) 1 ;\nIN: b\n: bar ( -- int ) a:baz ;\n");
    }

    public void testRenameVariableThroughQualifiedBang() {
        rename("IN: a\n0 VARIABLE c<caret>tr\nIN: b\n: f ( -- ) 1 a:ctr! a:ctr ;\n", "n",
                "IN: a\n0 VARIABLE n\nIN: b\n: f ( -- ) 1 a:n! a:n ;\n");
    }

    public void testRenameMetaTarget() {
        rename(": shift ( -- ) ;\nMETA: sh<caret>ift\n  doc \"x\" ;\n;\n", "rot",
                ": rot ( -- ) ;\nMETA: rot\n  doc \"x\" ;\n;\n");
    }

    public void testRenameAlias() {
        String after = ": shift ( -- ) ;\nMETA: shift\n  alias caesar ;\n;\n: use ( -- ) caesar ;\n";
        rename(": shift ( -- ) ;\nMETA: shift\n  alias rot<caret>13 ;\n;\n: use ( -- ) rot13 ;\n", "caesar", after);
        rename(": shift ( -- ) ;\nMETA: shift\n  alias rot13 ;\n;\n: use ( -- ) rot<caret>13 ;\n", "caesar", after);
    }

    // ---- names ----

    public void testNameValidation() {
        NamesValidator v = LanguageNamesValidation.INSTANCE.forLanguage(FLLanguage.INSTANCE);
        assertNotNull(v);
        for (String ok : new String[]{"foo", "_foo", ">hex", "+", "see-also", "n!", "Foo", "base64"}) {
            assertTrue(ok, v.isIdentifier(ok, getProject()));
        }
        for (String bad : new String[]{"a:b", "recurse", "1x", "", "a b", "-1", "IN:", ":", ";", "true", "CONSTANT", "a(b", "x;"}) {
            assertFalse(bad, v.isIdentifier(bad, getProject()));
        }
        assertTrue(v.isKeyword("recurse", getProject()));
        assertFalse(v.isKeyword("foo", getProject()));
    }

    public void testParameterNameValidation() {
        for (String ok : new String[]{"foo", "a", "n1", "see-also"}) {
            assertTrue(ok, FLParameterRenameValidator.isValidParameterName(ok));
        }
        for (String bad : new String[]{"Foo", "_x", "1x", "str", "int", "bytes", "recurse", ">hex", "a:b", "true"}) {
            assertFalse(bad, FLParameterRenameValidator.isValidParameterName(bad));
        }
    }
}
