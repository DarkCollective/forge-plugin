// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.hierarchy;

import com.forge.fl.FLTestBase;
import com.forge.fl.psi.FLDefinition;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FLCallHierarchyTest extends FLTestBase {
    private static final String CODE = """
            IN: t
            : _sq ( int -- int ) dup * ;
            : area ( w:int h:int -- int ) w h * _sq ;
            : twice-area ( w:int -- int ) w w area w 1 area + ;
            : fact ( n:int -- int ) n 1 <= [ 1 ] [ n 1 - recurse n * ] if ;

            META: area
              alias surface ;
              see-also t:_sq ;
            ;

            : by-alias ( -- int ) 2 3 surface ;
            5 _sq .
            """;

    private PsiFile file;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        file = myFixture.configureByText("t.fth", CODE);
    }

    private FLDefinition word(String name) {
        return PsiTreeUtil.findChildrenOfType(file, FLDefinition.class).stream()
                .filter(d -> name.equals(d.getName())).findFirst().orElseThrow();
    }

    /** The children of a node as "text [uses]". */
    private static List<String> children(HierarchyTreeStructure s, Object node) {
        List<String> out = new ArrayList<>();
        for (Object c : s.getChildElements(node)) {
            FLCallNode n = (FLCallNode) c;
            out.add(n.text() + " [" + n.usages().size() + "]" + (n.isRecursive() ? " recursive" : ""));
        }
        return out;
    }

    private static FLCallNode child(HierarchyTreeStructure s, Object node, String prefix) {
        return (FLCallNode) Arrays.stream(s.getChildElements(node)).filter(c -> ((FLCallNode) c).text().startsWith(prefix))
                .findFirst().orElseThrow();
    }

    public void testCallersIncludeAliasesAndTopLevelCode() {
        HierarchyTreeStructure callers = new FLCallTrees.Callers(getProject(), word("area"), HierarchyBrowserBaseEx.SCOPE_ALL);
        assertEquals(List.of("t:twice-area ( w:int -- int )  t.fth:4 [2]", "t:by-alias ( -- int )  t.fth:12 [1]"),
                children(callers, callers.getBaseDescriptor()));
        HierarchyTreeStructure sq = new FLCallTrees.Callers(getProject(), word("_sq"), HierarchyBrowserBaseEx.SCOPE_ALL);
        // the see-also in the META: block is not a call
        assertEquals(List.of("t:area ( w:int h:int -- int )  t.fth:3 [1]", "t.fth (top-level code) [1]"),
                children(sq, sq.getBaseDescriptor()));
        // and one level further up
        FLCallNode area = child(sq, sq.getBaseDescriptor(), "t:area");
        assertEquals(2, sq.getChildElements(area).length);
    }

    public void testCalleesSkipLocalsAndStopAtRecursion() {
        HierarchyTreeStructure callees = new FLCallTrees.Callees(getProject(), word("area"));
        List<String> c = children(callees, callees.getBaseDescriptor());
        assertEquals(2, c.size());
        assertTrue(c.get(0), c.get(0).startsWith("core:* ") && c.get(0).endsWith("built-in [1]"));
        assertEquals("t:_sq ( int -- int )  t.fth:2 [1]", c.get(1));
        HierarchyTreeStructure fact = new FLCallTrees.Callees(getProject(), word("fact"));
        List<String> f = children(fact, fact.getBaseDescriptor());
        assertTrue(f.toString(), f.contains("t:fact ( n:int -- int )  t.fth:5 [1] recursive"));
        FLCallNode self = child(fact, fact.getBaseDescriptor(), "t:fact");
        assertEquals("a recursive node has no children", 0, fact.getChildElements(self).length);
        // a built-in leaf has no callees
        FLCallNode mul = child(callees, callees.getBaseDescriptor(), "core:*");
        assertEquals(0, callees.getChildElements(mul).length);
    }

    public void testSourceOrderFollowsCallSites() {
        HierarchyTreeStructure callees = new FLCallTrees.Callees(getProject(), word("fact"));
        List<Object> nodes = new ArrayList<>(Arrays.asList(callees.getChildElements(callees.getBaseDescriptor())));
        java.util.Collections.reverse(nodes);
        nodes.sort((x, y) -> FLCallHierarchyProvider.byFirstUse((FLCallNode) x, (FLCallNode) y));
        List<String> names = nodes.stream().map(n -> ((FLCallNode) n).text().split(" ")[0]).toList();
        // n 1 <= [ 1 ] [ n 1 - recurse n * ] if
        assertEquals(List.of("core:<=", "core:-", "t:fact", "core:*", "core:if"), names);
    }

    public void testTargetAndRegistration() {
        String text = file.getText();
        PsiElement onUse = FLCallHierarchyProvider.targetAt(file, text.indexOf("area w 1") + 1);
        assertEquals("area", ((FLDefinition) onUse).getName());
        PsiElement onLocal = FLCallHierarchyProvider.targetAt(file, text.indexOf("n 1 <=") );
        assertEquals("fact", ((FLDefinition) onLocal).getName());
        PsiElement viaAlias = FLCallHierarchyProvider.targetAt(file, text.lastIndexOf("surface") + 1);
        assertEquals("area", ((FLDefinition) viaAlias).getName());
        assertTrue(new LanguageExtension<>("com.intellij.callHierarchyProvider").allForLanguage(com.forge.fl.FLLanguage.INSTANCE)
                .stream().anyMatch(p -> p instanceof FLCallHierarchyProvider));
    }
}
