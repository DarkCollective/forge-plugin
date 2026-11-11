// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.completion;

import com.forge.fl.FLMultiFileTestBase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;

import java.util.ArrayList;
import java.util.List;

public class FLCompletionTest extends FLMultiFileTestBase {
    private LookupElement[] complete(String name, String text) {
        myFixture.configureByText(name, text);
        return myFixture.completeBasic();
    }

    private static List<String> strings(LookupElement[] items) {
        assertNotNull("completion was applied directly", items);
        List<String> out = new ArrayList<>();
        for (LookupElement l : items) out.add(l.getLookupString());
        return out;
    }

    private static LookupElementPresentation present(LookupElement[] items, String lookup) {
        for (LookupElement l : items) {
            if (l.getLookupString().equals(lookup)) {
                LookupElementPresentation p = new LookupElementPresentation();
                l.renderElement(p);
                return p;
            }
        }
        throw new AssertionError(lookup + " not offered in " + strings(items));
    }

    private void seedVocabularies() {
        add("a.fth", "IN: a\n: pubw ( x:int -- int ) x ;\n: _privw ( -- ) ;\n0 VARIABLE ctr\n");
    }

    public void testVisibleWordsWithEffectAndVocabulary() {
        seedVocabularies();
        LookupElement[] items = complete("m.fth", "USING: a ;\n: mine ( n:int -- ) ;\n: f ( amount:int -- ) <caret> ;\n");
        List<String> names = strings(items);
        assertTrue(names.toString(), names.containsAll(List.of("amount", "mine", "pubw", "ctr", "ctr!", "dup", "swap", "+")));
        assertFalse("private words of an imported vocabulary are not visible", names.contains("_privw"));
        assertFalse("words of vocabularies that are not imported", names.contains("encode"));
        // tail text: the stack effect; type text: the vocabulary (or "parameter")
        assertEquals(" ( a -- a a )", present(items, "dup").getTailText());
        assertEquals("core", present(items, "dup").getTypeText());
        assertEquals(" ( x:int -- int )", present(items, "pubw").getTailText());
        assertEquals("a", present(items, "pubw").getTypeText());
        assertEquals(" ( n:int -- )", present(items, "mine").getTailText());
        assertEquals("user", present(items, "mine").getTypeText());
        assertEquals("parameter", present(items, "amount").getTypeText());
        assertEquals(" ( -- int )", present(items, "ctr").getTailText());
        assertEquals(" ( int -- )", present(items, "ctr!").getTailText());
    }

    public void testEachNameOnceNearestMeaningFirst() {
        add("a.fth", "IN: a\n: dup ( -- int ) 99 ;\n");
        LookupElement[] items = complete("m.fth", "USING: a ;\n: f ( -- ) <caret> ;\n");
        long count = strings(items).stream().filter("dup"::equals).count();
        assertEquals(1, count);
        assertEquals("a", present(items, "dup").getTypeText()); // imports shadow core
    }

    public void testPrivateWordsOfTheCurrentVocabularyAreOffered() {
        seedVocabularies();
        LookupElement[] items = complete("m.fth", "IN: a\n: f ( -- ) _pr<caret> ;\n");
        assertNull(items); // the only match was inserted
        myFixture.checkResult("IN: a\n: f ( -- ) _privw ;\n");
    }

    public void testNothingFromImportsBeforeTheUsingForm() {
        seedVocabularies();
        List<String> names = strings(complete("m.fth", ": f ( -- ) <caret> ;\nUSING: a ;\n"));
        assertFalse(names.contains("pubw"));
    }

    public void testQualifiedCompletionOffersTheWordsOfThatVocabulary() {
        seedVocabularies();
        List<String> names = strings(complete("m.fth", ": f ( -- ) a:<caret> ;\n"));
        assertEquals(List.of("pubw", "ctr", "ctr!").stream().sorted().toList(), names.stream().sorted().toList());
        // from inside the vocabulary the private words are offered too
        List<String> own = strings(complete("m2.fth", "IN: a\n: f ( -- ) a:<caret> ;\n"));
        assertTrue(own.toString(), own.contains("_privw"));
    }

    public void testQualifiedCompletionInsertsAfterTheColon() {
        seedVocabularies();
        assertNull(complete("m.fth", ": f ( -- ) a:pu<caret> ;\n"));
        myFixture.checkResult(": f ( -- ) a:pubw ;\n");
        assertNull(complete("m2.fth", ": f ( -- ) core:sw<caret> ;\n"));
        myFixture.checkResult(": f ( -- ) core:swap ;\n");
    }

    public void testBuiltinVocabulariesAreOfferedQualified() {
        LookupElement[] items = complete("m.fth", ": f ( -- ) raise:<caret> ;\n");
        assertEquals(List.of("coerce", "range"), strings(items).stream().sorted().toList());
        assertEquals(" ( reason:str -- )", present(items, "range").getTailText());
        assertEquals("raise", present(items, "range").getTypeText());
    }

    public void testSymbolNamedWordsComplete() {
        add("a.fth", "IN: a\n: >hex ( n:int -- str ) n ;\n");
        LookupElement[] r = complete("m.fth", "USING: a ;\n: f ( -- ) >he<caret> ;\n");
        assertNull(r == null ? null : strings(r).toString(), r);
        myFixture.checkResult("USING: a ;\n: f ( -- ) >hex ;\n");
    }

    public void testVocabularyNamesAfterUsingAndIn() {
        seedVocabularies();
        for (String form : new String[]{"USING: ", "IN: "}) {
            List<String> names = strings(complete("m" + form.length() + ".fth", form + "<caret>\n"));
            assertTrue(form + names, names.containsAll(List.of("a", "hex", "raise", "user")));
            assertFalse("core cannot be imported or entered", names.contains("core"));
            assertFalse("words are not offered here", names.contains("dup") || names.contains("pubw"));
        }
        assertNull(complete("m3.fth", "USING: rai<caret>\n"));
        myFixture.checkResult("USING: raise\n");
        // vocabularies of the current file count too, and the second name of a USING: form
        add("zeta.fth", "IN: zeta\n");
        assertNull(complete("m4.fth", "USING: a ze<caret> ;\n"));
        myFixture.checkResult("USING: a zeta ;\n");
    }

    public void testDeclarationNamesGetNoSuggestions() {
        seedVocabularies();
        LookupElement[] items = complete("m.fth", ": pu<caret> ( -- ) ;\n");
        List<String> names = items == null ? List.of() : strings(items);
        assertFalse(names.toString(), names.contains("pubw"));
    }
}
