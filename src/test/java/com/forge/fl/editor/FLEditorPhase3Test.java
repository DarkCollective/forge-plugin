// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.editor;

import com.forge.fl.FLMultiFileTestBase;
import com.forge.fl.highlighting.FLSyntaxHighlighter;
import com.forge.fl.psi.FLNamedElement;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.search.TodoItem;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.indexing.FindSymbolParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class FLEditorPhase3Test extends FLMultiFileTestBase {
    // ---- Go to Symbol -------------------------------------------------------------------------------------

    private List<String> names(GlobalSearchScope scope) {
        List<String> out = new ArrayList<>();
        // what the Go to Symbol popup passes: the scope and an id filter for "include non-project items"
        boolean nonProject = scope.equals(GlobalSearchScope.allScope(getProject()));
        new com.forge.fl.navigation.FLGotoSymbolContributor().processNames(out::add, scope,
                com.intellij.util.indexing.IdFilter.getProjectIdFilter(getProject(), nonProject));
        return out;
    }

    private List<NavigationItem> items(String name, boolean nonProject) {
        List<NavigationItem> out = new ArrayList<>();
        new com.forge.fl.navigation.FLGotoSymbolContributor().processElementsWithName(name, out::add,
                FindSymbolParameters.wrap(name, getProject(), nonProject));
        return out;
    }

    public void testGotoSymbolByBareAndQualifiedName() {
        add("aes.fth", "IN: aes\n: encrypt ( data:bytes -- bytes ) data ;\n");
        List<String> names = names(GlobalSearchScope.projectScope(getProject()));
        assertTrue(names.toString(), names.contains("aes:encrypt"));
        com.intellij.psi.codeStyle.MinusculeMatcher bare = com.intellij.psi.codeStyle.NameUtil.buildMatcher("*encrypt").build();
        com.intellij.psi.codeStyle.MinusculeMatcher qual = com.intellij.psi.codeStyle.NameUtil.buildMatcher("*aes:enc").build();
        assertTrue(bare.matches("aes:encrypt"));
        assertTrue(qual.matches("aes:encrypt"));
        List<NavigationItem> found = items("aes:encrypt", false);
        assertEquals(1, found.size());
        assertEquals("encrypt ( data:bytes -- bytes )", found.get(0).getPresentation().getPresentableText());
        assertEquals("(aes) aes.fth", found.get(0).getPresentation().getLocationString());
    }

    public void testGotoSymbolBuiltinsOnlyWithNonProjectItems() {
        add("aes.fth", "IN: aes\n: encrypt ( data:bytes -- bytes ) data ;\n");
        assertFalse(names(GlobalSearchScope.projectScope(getProject())).contains("core:dup"));
        assertTrue(names(GlobalSearchScope.allScope(getProject())).contains("core:dup"));
        assertEmpty(items("core:dup", false));
        assertEquals(1, items("core:dup", true).size());
    }

    // ---- structure view ---------------------------------------------------------------------------------------

    private static List<String> tree(StructureViewTreeElement e, String indent) {
        List<String> out = new ArrayList<>();
        for (TreeElement c : e.getChildren()) {
            StructureViewTreeElement s = (StructureViewTreeElement) c;
            out.add(indent + s.getPresentation().getPresentableText());
            out.addAll(tree(s, indent + "  "));
        }
        return out;
    }

    public void testStructureViewGroupsByVocabularyWhenThereAreSeveralIns() {
        PsiFile f = parse("IN: a\n: x ( -- ) ;\nIN: b\n: y ( -- ) ;\n: _z ( -- ) ;\nIN: a\n: w ( -- ) ;\n");
        assertEquals(List.of("a", "  x", "  w", "b", "  y", "  _z"), tree(new FLStructureViewElement(f), ""));
        PsiFile one = parse("IN: a\n: x ( -- ) ;\n");
        assertEquals(List.of("x"), tree(new FLStructureViewElement(one), ""));
    }

    public void testStructureViewMarksPrivateWordsAndOffersFilterAndSorter() {
        PsiFile f = parse("IN: a\n: _p ( -- ) ;\n: q ( -- ) ;\n");
        List<TreeElement> kids = new ArrayList<>(List.of(new FLStructureViewElement(f).getChildren()));
        FLStructureViewElement p = (FLStructureViewElement) kids.get(0);
        FLStructureViewElement q = (FLStructureViewElement) kids.get(1);
        assertTrue(p.isPrivate());
        assertFalse(q.isPrivate());
        assertEquals("Private word", p.getPresentation() instanceof com.intellij.ide.projectView.PresentationData d ? d.getTooltip() : null);
        FLStructureViewModel model = new FLStructureViewModel(f, null);
        assertEquals(1, model.getFilters().length);
        assertFalse(model.getFilters()[0].isVisible(p));
        assertTrue(model.getFilters()[0].isVisible(q));
        assertEquals(1, model.getSorters().length);
    }

    // ---- folding ------------------------------------------------------------------------------------------------

    private int counter;

    private String folds(String text) {
        myFixture.configureByText("fold" + (counter++) + ".fth", text);
        return ((CodeInsightTestFixtureImpl) myFixture).getFoldingDescription(true);
    }

    public void testFolding() {
        String src = "\\ one\n\\ two\n\\ three\n\n: f ( a:int -- int )\n  a [\n    1 +\n  ] ;\n\\ x\n\\ y\n"
                + "META: f\n  doc \"d\" ;\n;\n: g ( -- ) ;\n";
        String expected = "<fold text='\\ one ...' expand='true'>\\ one\n\\ two\n\\ three</fold>\n\n"
                + "<fold text=': f ( a:int -- int ) ...' expand='true'>: f ( a:int -- int )\n  a <fold text='[ ... ]' expand='true'>[\n    1 +\n  ]</fold> ;</fold>\n"
                + "\\ x\n\\ y\n"
                + "<fold text='META: f ...' expand='true'>META: f\n  doc \"d\" ;\n;</fold>\n: g ( -- ) ;\n";
        assertEquals(expected, folds(src));
    }

    public void testLicenceHeaderCollapsedByDefaultAccordingToTheSetting() {
        CodeFoldingSettings s = CodeFoldingSettings.getInstance();
        boolean old = s.COLLAPSE_FILE_HEADER;
        try {
            String src = "\\ Copyright 2026 X. Licensed under Apache 2.0.\n\\ more\nIN: a\n";
            s.COLLAPSE_FILE_HEADER = true;
            assertTrue(folds(src), folds(src).contains("expand='false'"));
            s.COLLAPSE_FILE_HEADER = false;
            assertTrue(folds(src), folds(src).contains("expand='true'"));
            s.COLLAPSE_FILE_HEADER = true;
            assertEquals("\\ a\n\\ b\nIN: a\n", folds("\\ a\n\\ b\nIN: a\n")); // two lines, not a licence: no fold
        } finally {
            s.COLLAPSE_FILE_HEADER = old;
        }
    }

    // ---- typing -------------------------------------------------------------------------------------------------

    private String typedWithCaret(String before, String chars) {
        myFixture.configureByText("m.fth", before);
        myFixture.type(chars);
        String t = myFixture.getEditor().getDocument().getText();
        int c = myFixture.getCaretOffset();
        return t.substring(0, c) + "<caret>" + t.substring(c);
    }

    public void testBracketsArePairedWithSpaces() {
        assertEquals(": f ( -- ) [ <caret> ] ;", typedWithCaret(": f ( -- ) <caret> ;", "["));
        assertEquals("x { <caret> } y", typedWithCaret("x <caret> y", "{"));
    }

    public void testClosersAreTypedOver() {
        assertEquals("{ 1 }<caret>", typedWithCaret("<caret>", "{1}"));
        assertEquals("[ a ]<caret>", typedWithCaret("<caret>", "[a]"));
    }

    public void testBracketNotPairedInsideAToken() {
        assertEquals("foo[<caret>", typedWithCaret("foo<caret>", "["));
    }

    public void testQuotesArePairedAndOvertyped() {
        assertEquals("\"\"<caret>", typedWithCaret("<caret>", "\"\""));
        assertEquals("\"hi\"<caret>", typedWithCaret("<caret>", "\"hi\""));
        assertEquals("a \"<caret>\" b", typedWithCaret("a <caret> b", "\""));
        assertEquals("foo\"<caret>", typedWithCaret("foo<caret>", "\""));
    }

    public void testNothingPairedInComments() {
        assertEquals("\\ a [<caret>", typedWithCaret("\\ a <caret>", "["));
    }

    // ---- semantic highlighting ---------------------------------------------------------------------------------------

    private List<String> keys(String text) {
        add("util.fth", "IN: util\n: compare ( a:int -- int ) a ;\n");
        myFixture.configureByText("m.fth", text);
        List<String> out = new ArrayList<>();
        for (HighlightInfo i : myFixture.doHighlighting()) {
            if (i.forcedTextAttributesKey != null) out.add(i.forcedTextAttributesKey.getExternalName() + ":" + i.getText());
        }
        return out;
    }

    public void testSemanticKeys() {
        List<String> k = keys("IN: demo\nUSING: util ;\n: f ( amount:int -- int )\n  \"d\" doc\n  amount dup util:compare ;\n"
                + "META: f\n  param amount \"p\" ;\n  see-also util:compare ;\n;\n");
        assertTrue(k.toString(), k.containsAll(List.of(
                "FL_VOCABULARY:demo", "FL_VOCABULARY:util", "FL_DECLARATION_NAME:f", "FL_META_KEYWORD:doc",
                "FL_PARAMETER_USE:amount", "FL_BUILTIN_WORD:dup", "FL_VOCABULARY:util",
                "FL_META_KEYWORD:param", "FL_META_KEYWORD:see-also")));
        assertEquals(k.toString(), 0, k.stream().filter(s -> s.equals("FL_BUILTIN_WORD:f")).count());
        // a word of a user vocabulary is not coloured as a built-in
        assertFalse(k.toString(), k.contains("FL_BUILTIN_WORD:compare"));
    }

    public void testColourSettingsPageListsEveryKey() {
        FLColorSettingsPage page = new FLColorSettingsPage();
        TreeSet<String> listed = new TreeSet<>();
        for (var d : page.getAttributeDescriptors()) listed.add(d.getKey().getExternalName());
        for (var key : List.of(FLSyntaxHighlighter.META_KEYWORD, FLSyntaxHighlighter.DECLARATION_NAME,
                FLSyntaxHighlighter.PARAMETER_USE, FLSyntaxHighlighter.BUILTIN_WORD, FLSyntaxHighlighter.VOCABULARY)) {
            assertTrue(key.getExternalName(), listed.contains(key.getExternalName()));
            assertTrue(key.getExternalName(), page.getAdditionalHighlightingTagToDescriptorMap().containsValue(key));
        }
        for (String tag : page.getAdditionalHighlightingTagToDescriptorMap().keySet()) {
            assertTrue(tag, page.getDemoText().contains("<" + tag + ">"));
        }
    }

    // ---- TODO ---------------------------------------------------------------------------------------------------------

    public void testTodoInLineAndBlockComments() {
        PsiFile f = parse("\\ TODO fix the line\n: f ( -- ) ( TODO fix the block ) ; \\ FIXME later\n\"TODO not a comment\" CONSTANT-less\n");
        TodoItem[] items = PsiTodoSearchHelper.getInstance(getProject()).findTodoItems(f);
        List<String> texts = new ArrayList<>();
        for (TodoItem i : items) texts.add(f.getText().substring(i.getTextRange().getStartOffset(), i.getTextRange().getEndOffset()));
        assertEquals(texts.toString(), 2 + 0, texts.size() - (texts.stream().anyMatch(t -> t.startsWith("FIXME")) ? 1 : 0));
        assertTrue(texts.toString(), texts.stream().anyMatch(t -> t.startsWith("TODO fix the line")));
        assertTrue(texts.toString(), texts.stream().anyMatch(t -> t.startsWith("TODO fix the block")));
        assertFalse(texts.toString(), texts.stream().anyMatch(t -> t.contains("not a comment")));
    }
}
