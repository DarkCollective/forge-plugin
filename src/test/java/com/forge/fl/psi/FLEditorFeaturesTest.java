// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.FLTestBase;
import com.forge.fl.editor.FLStructureViewElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.List;

public class FLEditorFeaturesTest extends FLTestBase {
    private List<String> complete(String text) {
        myFixture.configureByText("t.fth", text);
        LookupElement[] items = myFixture.completeBasic();
        List<String> out = new ArrayList<>();
        if (items != null) for (LookupElement l : items) out.add(l.getLookupString());
        return out;
    }

    public void testCompletionOffersKeywordsDeclarationsAndParameters() {
        List<String> items = complete(": helper ( -- ) ;\n0 VARIABLE counter\n: f ( amount:int -- ) <caret> ;\n");
        assertTrue(items.toString(), items.containsAll(List.of("recurse", "IN:", "helper", "counter", "counter!", "amount", "f")));
        // core words are real now (phase 2); words that are not in core are still not invented (emit is core in v0.2)
        assertTrue(items.toString(), items.containsAll(List.of("dup", "swap")));
        for (String bad : new String[]{"else", "then", "begin"}) assertFalse(bad, items.contains(bad));
    }

    public void testParametersOfOtherDefinitionsAreNotOffered() {
        List<String> items = complete(": g ( hidden:int -- ) ;\n: f ( amount:int -- ) <caret> ;\n");
        assertFalse(items.toString(), items.contains("hidden"));
        assertTrue(items.contains("amount"));
    }

    public void testStructureView() {
        PsiFile f = parse("IN: v\n256 CONSTANT size\n0 VARIABLE counter\nPRIMITIVE raw\n: f ( a -- b ) ;\n");
        FLStructureViewElement root = new FLStructureViewElement(f);
        List<String> shown = new ArrayList<>();
        for (StructureViewTreeElement child : root.getChildrenBase()) {
            shown.add(child.getPresentation().getPresentableText() + " " + child.getPresentation().getLocationString());
        }
        assertEquals(List.of("size 256", "counter 0", "raw null", "f ( a -- b )"), shown);
    }

    public void testCommenterAndBraces() {
        assertEquals("\\ ", new com.forge.fl.editor.FLCommenter().getLineCommentPrefix());
        assertEquals(2, new com.forge.fl.editor.FLBraceMatcher().getPairs().length);
    }
}
