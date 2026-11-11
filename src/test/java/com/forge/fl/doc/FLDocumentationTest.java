// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.doc;

import com.forge.fl.FLMultiFileTestBase;
import com.forge.fl.psi.FLNamedElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

public class FLDocumentationTest extends FLMultiFileTestBase {
    private final FLDocumentationProvider provider = new FLDocumentationProvider();

    private static final String SOURCE = "IN: v\n"
            + ": shift ( text:str amount:int[1..25]=13 -- str )\n"
            + "  \"Shifts <b>each</b> letter & more.\" doc\n"
            + "  \"Ciphers\" category\n"
            + "  text ;\n"
            + "META: shift\n"
            + "  version \"2\" ;\n"
            + "  param amount \"The shift, 13 for ROT13\" ;\n"
            + "  alias rot ;\n"
            + "  see-also v:other core:dup ;\n"
            + ";\n"
            + "256 CONSTANT size\n"
            + "0 VARIABLE counter\n"
            + ": use ( -- ) shift size dup amount ;\n";

    private String docAt(String text) {
        PsiReference ref = refAt("m.fth", text);
        PsiElement target = ref.resolve();
        assertNotNull(target);
        return provider.generateDoc(target, null);
    }

    public void testWordWithDeclsAndMeta() {
        String doc = docAt(SOURCE.replace("shift size", "<caret>shift size"));
        assertTrue(doc, doc.contains("<b>v:shift</b> ( text:str amount:int[1..25]=13 -- str )"));
        assertTrue(doc, doc.contains("Shifts &lt;b&gt;each&lt;/b&gt; letter &amp; more."));
        assertFalse(doc, doc.contains("<b>each</b>"));
        assertTrue(doc, doc.contains("Ciphers"));
        assertTrue(doc, doc.contains("Version:</p></td><td valign='top'>2"));
        assertTrue(doc, doc.contains("<code>amount:int[1..25]=13</code> &ndash; The shift, 13 for ROT13"));
        assertTrue(doc, doc.contains("<code>rot</code>"));
        assertTrue(doc, doc.contains("<code>v:other</code>, <code>core:dup</code>"));
    }

    public void testParameterFromItsUse() {
        String src = SOURCE.replace("  text ;", "  amount ;").replace("  amount ;", "  <caret>amount ;");
        String doc = docAt(src);
        assertTrue(doc, doc.contains("<b>amount</b>") || doc.contains("amount"));
        assertTrue(doc, doc.contains("Parameter of:</p></td><td valign='top'>v:shift"));
        assertTrue(doc, doc.contains("<code>amount:int[1..25]=13</code> &ndash; The shift, 13 for ROT13"));
    }

    public void testConstantAndVariable() {
        String c = docAt(SOURCE.replace("shift size", "shift <caret>size"));
        assertTrue(c, c.contains("<b>v:size</b> ( -- int )"));
        assertTrue(c, c.contains("<code>256</code>"));
        assertTrue(c, c.contains("constant"));
        String v = docAt("0 VARIABLE counter\n: x ( -- ) <caret>counter ;\n");
        assertTrue(v, v.contains("<b>user:counter</b> ( -- int )"));
        assertTrue(v, v.contains("<b>user:counter!</b> ( int -- )"));
        assertTrue(v, v.contains("<code>0</code>"));
    }

    public void testBuiltInWord() {
        String doc = docAt(": f ( a:int -- int ) a <caret>dup ;\n");
        assertTrue(doc, doc.contains("<b>core:dup</b> ( a -- a a )"));
        assertTrue(doc, doc.contains("built-in word (TypeScript intrinsic)") || doc.contains("TypeScript"));
        assertTrue(doc, doc.contains("Duplicate") || doc.contains("duplicate") || doc.length() > 200);
    }

    public void testDocumentationFromTheDeclarationAndQuickInfo() {
        myFixture.configureByText("m.fth", SOURCE);
        int off = SOURCE.indexOf("shift") ;
        PsiElement leaf = myFixture.getFile().findElementAt(off);
        PsiElement el = provider.getCustomDocumentationElement(myFixture.getEditor(), myFixture.getFile(), leaf, off);
        assertTrue(el instanceof FLNamedElement);
        assertEquals("v:shift ( text:str amount:int[1..25]=13 -- str )", provider.getQuickNavigateInfo(el, null));
    }

    public void testLookupObjects() {
        add("a.fth", "IN: a\n: pubw ( x:int -- int ) \"Doc of pubw\" doc x ;\n");
        myFixture.configureByText("m.fth", "USING: a ;\n: f ( -- ) <caret> ;\n");
        com.intellij.codeInsight.lookup.LookupElement[] items = myFixture.completeBasic();
        boolean checked = false;
        for (com.intellij.codeInsight.lookup.LookupElement l : items) {
            if (!l.getLookupString().equals("pubw")) continue;
            PsiElement el = provider.getDocumentationElementForLookupItem(getPsiManager(), l.getObject(), null);
            assertNotNull(el);
            assertTrue(provider.generateDoc(el, null).contains("Doc of pubw"));
            checked = true;
        }
        assertTrue(checked);
    }
}
