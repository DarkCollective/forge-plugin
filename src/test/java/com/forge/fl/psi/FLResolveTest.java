package com.forge.fl.psi;

import com.forge.fl.FLMultiFileTestBase;
import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.index.FLIndexLookup;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Name resolution across files and into the built-ins. Expectations come from docs/LANGUAGE.md section 6
 * (6.2 unqualified lookup, 6.3 qualified lookup), 5.3 (IN:, USING:), 5.7 (private words), 5.9 (META:) and 15.3/15.4
 * (ambiguity and shadowing examples); the core and primitive names from packages/ops built output.
 */
public class FLResolveTest extends FLMultiFileTestBase {
    // ---- 6.2 step by step: parameter > current vocabulary > import > core ----------------------------------

    public void testStep1ParameterBeatsEverything() {
        add("a.fth", "IN: a\n: x ( -- int ) 1 ;\n");
        PsiElement t = resolveAt("m.fth", "USING: a ;\n: x ( -- int ) 2 ;\n: f ( x:int -- int ) <caret>x ;\n");
        assertTrue(t instanceof FLParameter);
        // a parameter named like a core word hides the core word (7: locals shadow all words)
        assertTrue(resolveAt("m2.fth", ": f ( dup:int -- int ) <caret>dup ;\n") instanceof FLParameter);
    }

    public void testStep2CurrentVocabularyInThisFile() {
        add("a.fth", "IN: a\n: foo ( -- int ) 1 ;\n");
        PsiElement t = resolveAt("m.fth", "USING: a ;\n: foo ( -- int ) 2 ;\n: g ( -- int ) <caret>foo ;\n");
        assertEquals("m.fth:foo", where(t)); // current vocabulary shadows imports
    }

    public void testStep2CurrentVocabularyDefinedInAnotherFile() {
        add("v1.fth", "IN: v\n: shared ( -- int ) 1 ;\n: _inner ( -- int ) 2 ;\n");
        assertEquals("v1.fth:shared", where(resolveAt("v2.fth", "IN: v\n: use ( -- int ) <caret>shared ;\n")));
        // private words of the current vocabulary are visible too, also across files
        assertEquals("v1.fth:_inner", where(resolveAt("v3.fth", "IN: v\n: use ( -- int ) <caret>_inner ;\n")));
        // but not from another vocabulary
        assertNull(resolveAt("w.fth", "IN: w\n: use ( -- int ) <caret>shared ;\n"));
    }

    public void testCurrentVocabularyBeatsCore() {
        // 15.4 / 6.2: inside IN: classical, rot means classical:rot; core:rot is the stack word
        add("classical.fth", "IN: classical\n: rot ( text:str -- str ) text ;\n");
        assertEquals("classical.fth:rot", where(resolveAt("m.fth", "IN: classical\n: f ( s:str -- str ) <caret>rot ;\n")));
        PsiElement stack = resolveAt("m2.fth", "IN: classical\n: f ( -- ) <caret>core:rot ;\n");
        assertTrue(FLBuiltins.isBuiltin(stack));
        assertEquals("core.fl:rot", where(stack));
        // outside that vocabulary the stack word is the one
        assertEquals("core.fl:rot", where(resolveAt("m3.fth", ": f ( -- ) <caret>rot ;\n")));
    }

    public void testStep3ImportBeatsCore() {
        add("a.fth", "IN: a\n: dup ( -- int ) 99 ;\n");
        assertEquals("a.fth:dup", where(resolveAt("m.fth", "USING: a ;\n: f ( -- ) <caret>dup ;\n")));
        // without the import it is core's
        assertEquals("core.fl:dup", where(resolveAt("m2.fth", ": f ( -- ) <caret>dup ;\n")));
    }

    public void testStep3ImportOnlyFromUsingFormsBeforeTheUse() {
        add("a.fth", "IN: a\n: foo ( -- int ) 1 ;\n");
        assertNull(resolveAt("m.fth", ": f ( -- ) <caret>foo ;\nUSING: a ;\n"));
        assertEquals("a.fth:foo", where(resolveAt("m2.fth", "USING: a ;\n: f ( -- ) <caret>foo ;\n")));
        // several forms add up; a later one does not hide an earlier one; core in the list is ignored
        add("b.fth", "IN: b\n: bar ( -- int ) 1 ;\n");
        assertEquals("a.fth:foo", where(resolveAt("m3.fth", "USING: a ;\nUSING: core b ;\n: f ( -- ) <caret>foo ;\n")));
        assertEquals("b.fth:bar", where(resolveAt("m4.fth", "USING: a ;\nUSING: core b ;\n: f ( -- ) <caret>bar ;\n")));
    }

    public void testStep3AmbiguityBetweenTwoImportsIsPolyVariant() {
        add("alpha.fth", "IN: alpha\n: greet ( -- str ) \"alpha\" ;\n");
        add("beta.fth", "IN: beta\n: greet ( -- str ) \"beta\" ;\n");
        // 15.3: USING: alpha beta ; greet -> E_AMBIGUOUS with candidates in U order
        PsiReference ref = refAt("m.fth", "USING: alpha beta ;\n: f ( -- str ) <caret>greet ;\n");
        assertTrue(ref instanceof PsiPolyVariantReference);
        assertNull(ref.resolve());
        assertTrue(ref.isSoft());
        assertEquals(List.of("alpha.fth:greet", "beta.fth:greet"),
                candidatesAt("m2.fth", "USING: alpha beta ;\n: f ( -- str ) <caret>greet ;\n").stream().map(FLResolveTest::where).collect(Collectors.toList()));
        // U order, not alphabetical; duplicates in U are ignored
        assertEquals(List.of("beta.fth:greet", "alpha.fth:greet"),
                candidatesAt("m3.fth", "USING: beta alpha beta ;\n: f ( -- str ) <caret>greet ;\n").stream().map(FLResolveTest::where).collect(Collectors.toList()));
        assertEquals(List.of("alpha.fth:greet", "beta.fth:greet"),
                candidatesAt("m4.fth", "USING: alpha ;\nUSING: beta alpha ;\n: f ( -- str ) <caret>greet ;\n").stream().map(FLResolveTest::where).collect(Collectors.toList()));
        // a qualified name never is ambiguous
        assertEquals("alpha.fth:greet", where(resolveAt("m5.fth", "USING: alpha beta ;\n: f ( -- str ) <caret>alpha:greet ;\n")));
        // the current vocabulary and core never cause ambiguity
        assertEquals("m6.fth:greet", where(resolveAt("m6.fth", "USING: alpha beta ;\n: greet ( -- str ) \"m\" ;\n: f ( -- str ) <caret>greet ;\n")));
    }

    public void testAmbiguityNeedsTwoVocabulariesNotTwoFiles() {
        add("a1.fth", "IN: a\n: foo ( -- int ) 1 ;\n");
        add("a2.fth", "IN: a\n: bar ( -- int ) 1 ;\n");
        add("b.fth", "IN: b\n: bar ( -- int ) 1 ;\n");
        assertEquals("a1.fth:foo", where(resolveAt("m.fth", "USING: a b ;\n: f ( -- ) <caret>foo ;\n")));
        assertEquals(2, candidatesAt("m2.fth", "USING: a b ;\n: f ( -- ) <caret>bar ;\n").size());
    }

    public void testStep4CoreWords() {
        PsiElement dup = resolveAt("m.fth", ": f ( -- ) <caret>dup ;\n");
        assertNotNull(dup);
        assertTrue(FLBuiltins.isBuiltin(dup));
        assertEquals("core.fl:dup", where(dup));
        assertEquals("core.fl:swap", where(resolveAt("m2.fth", ": f ( -- ) <caret>swap ;\n")));
        assertEquals("core.fl:+", where(resolveAt("m3.fth", ": f ( -- ) <caret>+ ;\n")));
        assertEquals("core.fl:str>int", where(resolveAt("m4.fth", ": f ( -- ) <caret>str>int ;\n")));
        // navigation target: a real declaration with a readable header and the documentation above it
        FLDefinition d = (FLDefinition) dup;
        assertEquals("( a -- a a )", d.getEffectText());
        assertTrue(((Navigatable) dup).canNavigate());
        assertTrue(dup.getContainingFile().getText().contains("\\ duplicate the top\n\\ [TypeScript intrinsic]\n: dup ( a -- a a ) ;"));
        assertFalse(dup.getContainingFile().getVirtualFile().isWritable());
    }

    public void testTypeScriptPrimitivesOfOtherVocabularies() {
        // util:decode-table is an FL word (util.fth), not a primitive; raise:range and hex:encode are TypeScript
        PsiElement range = resolveAt("m.fth", ": f ( -- ) <caret>raise:range ;\n");
        assertEquals("raise.fl:range", where(range));
        assertEquals("( reason:str -- )", ((FLDefinition) range).getEffectText());
        assertEquals("hex.fl:encode", where(resolveAt("m2.fth", "USING: hex ;\n: f ( -- ) <caret>encode ;\n")));
        // private TypeScript primitive: visible in its own vocabulary only
        assertEquals("adler32.fl:_adler", where(resolveAt("m3.fth", "IN: adler32\n: f ( -- ) <caret>_adler ;\n")));
        assertNull(resolveAt("m4.fth", "USING: adler32 ;\n: f ( -- ) <caret>_adler ;\n"));
        assertNull(resolveAt("m5.fth", ": f ( -- ) <caret>adler32:_adler ;\n"));
        assertEquals("adler32.fl:_adler", where(resolveAt("m6.fth", "IN: adler32\n: f ( -- ) <caret>adler32:_adler ;\n")));
    }

    public void testStep5UnresolvedStaysSoft() {
        PsiReference ref = refAt("m.fth", ": f ( -- ) <caret>nosuchword ;\n");
        assertTrue(ref.isSoft());
        assertNull(ref.resolve());
        assertEquals(0, ((PsiPolyVariantReference) ref).multiResolve(false).length);
        // phase 3: the reference stays soft, but the annotator now reports the name
        assertEquals(java.util.List.of("Undefined word 'nosuchword' (E_UNDEFINED)"), errorDescriptions());
    }

    public void testForwardReferenceDoesNotHideCoreOrImports() {
        // 6.6: no forward references, so an earlier use of a name that is defined later still means core's
        assertEquals("core.fl:dup", where(resolveAt("m.fth", ": a ( -- ) <caret>dup ;\n: dup ( -- ) ;\n")));
        // and with nothing else visible the later declaration is the best guess (phase 1 behaviour)
        assertEquals("m2.fth:b", where(resolveAt("m2.fth", ": a ( -- ) <caret>b ;\n: b ( -- ) ;\n")));
    }

    public void testOwnNameInBodyMeansEarlierMeaning() {
        // 5.2 / 6.6: inside its own body a name refers to what it meant before the definition
        assertEquals("core.fl:dup", where(resolveAt("m.fth", ": dup ( a -- a a ) <caret>dup ;\n")));
        add("lib.fth", "IN: user\n: twice ( x:int -- int ) x x + ;\n");
        assertEquals("lib.fth:twice", where(resolveAt("m2.fth", ": twice ( x:int -- int ) x <caret>twice ;\n")));
    }

    // ---- 5.7 privacy ----------------------------------------------------------------------------------------

    public void testPrivateWordsAreNotFoundThroughUsing() {
        add("a.fth", "IN: a\n: _hidden ( -- int ) 1 ;\n: shown ( -- int ) _hidden ;\n");
        assertNull(resolveAt("m.fth", "USING: a ;\n: f ( -- int ) <caret>_hidden ;\n"));
        assertEquals("a.fth:shown", where(resolveAt("m2.fth", "USING: a ;\n: f ( -- int ) <caret>shown ;\n")));
        // inside the declaring file the use of a private word in its vocabulary resolves
        assertEquals("a.fth:_hidden", where(resolveAt("a2.fth", "IN: a\n: g ( -- int ) <caret>_hidden ;\n")));
    }

    public void testQualifiedPrivateOnlyFromOwnVocabulary() {
        add("a.fth", "IN: a\n: _hidden ( -- int ) 1 ;\n");
        assertNull(resolveAt("m.fth", ": f ( -- int ) <caret>a:_hidden ;\n"));
        assertNull(resolveAt("m2.fth", "IN: b\n: f ( -- int ) <caret>a:_hidden ;\n"));
        assertEquals("a.fth:_hidden", where(resolveAt("m3.fth", "IN: a\n: f ( -- int ) <caret>a:_hidden ;\n")));
        // a private name is never taken from another vocabulary through an import of a same-named public one
        add("b.fth", "IN: b\n: _hidden ( -- int ) 2 ;\n");
        assertEquals("b.fth:_hidden", where(resolveAt("m4.fth", "IN: b\n: f ( -- int ) <caret>_hidden ;\n")));
    }

    // ---- 6.3 qualified lookup ---------------------------------------------------------------------------

    public void testQualifiedLooksOnlyAtItsVocabulary() {
        add("a.fth", "IN: a\n: greet ( -- str ) \"a\" ;\n");
        add("b.fth", "IN: b\n: greet ( -- str ) \"b\" ;\n: only-b ( -- int ) 1 ;\n");
        assertEquals("b.fth:greet", where(resolveAt("m.fth", "USING: a ;\n: f ( -- str ) <caret>b:greet ;\n")));
        // imports are not consulted: a is imported, but the name is qualified with b
        assertNull(resolveAt("m2.fth", "USING: a ;\n: f ( -- int ) <caret>b:nothing ;\n"));
        // the current vocabulary does not help either
        assertNull(resolveAt("m3.fth", "IN: a\n: f ( -- int ) <caret>b:nothing ;\n"));
        // core is only reachable as core:
        assertNull(resolveAt("m4.fth", ": f ( -- ) <caret>a:dup ;\n"));
        assertEquals("core.fl:dup", where(resolveAt("m5.fth", ": f ( -- ) <caret>core:dup ;\n")));
    }

    public void testQualifiedNameInThisFileFollowsPhaseOneOrdering() {
        // the last declaration before the enclosing form wins
        PsiElement t = resolveAt("m.fth", "IN: a\n: x ( -- int ) 1 ;\n: x ( -- int ) 2 ;\nIN: b\n: y ( -- int ) <caret>a:x ;\n");
        assertEquals(t.getContainingFile().getText().lastIndexOf(": x") + 2, t.getTextOffset());
    }

    // ---- 5.9 META: --------------------------------------------------------------------------------------

    public void testMetaTargetMustBeInTheCurrentVocabulary() {
        add("w.fth", "IN: w\n: word ( -- int ) 1 ;\n");
        assertEquals("w.fth:word", where(resolveAt("meta.fth", "IN: w\nMETA: wor<caret>d\n  doc \"x\" ;\n;\n")));
        assertNull(resolveAt("meta2.fth", "IN: z\nMETA: wor<caret>d\n  doc \"x\" ;\n;\n"));
        assertNull(resolveAt("meta3.fth", "USING: w ;\nMETA: wor<caret>d\n  doc \"x\" ;\n;\n"));
        // core words are not META: targets of another vocabulary
        assertNull(resolveAt("meta4.fth", "META: du<caret>p\n  doc \"x\" ;\n;\n"));
    }

    public void testMetaParamResolvesThroughTargetInAnotherFile() {
        add("w.fth", "IN: w\n: shift ( text:str amount:int -- str ) text ;\n");
        PsiElement p = resolveAt("meta.fth", "IN: w\nMETA: shift\n  param am<caret>ount \"x\" ;\n;\n");
        assertTrue(p instanceof FLParameter);
        assertEquals("w.fth", fileName(p));
    }

    public void testSeeAlsoQualifiesUnqualifiedNamesWithTheCurrentVocabulary() {
        add("w.fth", "IN: w\n: one ( -- int ) 1 ;\n: two ( -- int ) 2 ;\n");
        add("o.fth", "IN: o\n: two ( -- int ) 2 ;\n");
        // unqualified names mean current-vocabulary:name, not an import and not core
        assertEquals("w.fth:two", where(resolveAt("meta.fth", "IN: w\nMETA: one\n  see-also t<caret>wo ;\n;\n")));
        assertNull(resolveAt("meta2.fth", "IN: z\nUSING: w ;\nMETA: x\n  see-also t<caret>wo ;\n;\n"));
        assertNull(resolveAt("meta3.fth", "IN: w\nMETA: one\n  see-also d<caret>up ;\n;\n"));
        // qualified names are what they say
        assertEquals("o.fth:two", where(resolveAt("meta4.fth", "IN: w\nMETA: one\n  see-also o:t<caret>wo ;\n;\n")));
    }

    // ---- misc -------------------------------------------------------------------------------------------

    public void testVariableBangAcrossFiles() {
        add("v.fth", "IN: v\n0 VARIABLE counter\n");
        PsiElement t = resolveAt("m.fth", ": f ( -- ) 1 <caret>v:counter! ;\n");
        assertTrue(t instanceof FLVariable);
        assertTrue(resolveAt("m2.fth", "IN: v\n: f ( -- ) <caret>counter ;\n") instanceof FLVariable);
    }

    public void testAliasesResolveAcrossFiles() {
        add("v.fth", "IN: v\n: shift ( -- ) ;\nMETA: shift\n  alias caesar ;\n;\n");
        PsiElement t = resolveAt("m.fth", "USING: v ;\n: f ( -- ) <caret>caesar ;\n");
        assertTrue(t instanceof FLAlias);
    }

    public void testResolveIsCached() {
        add("a.fth", "IN: a\n: foo ( -- int ) 1 ;\n");
        PsiReference ref = refAt("m.fth", "USING: a ;\n: f ( -- int ) <caret>foo ;\n");
        PsiElement first = ref.resolve();
        assertSame(first, ref.resolve());
    }

    /** A file with thousands of references asks the index once per distinct name, also after an edit. */
    public void testManyReferencesDoNotQueryTheIndexPerReference() {
        add("a.fth", "IN: a\n: pubw ( -- int ) 1 ;\n");
        StringBuilder body = new StringBuilder("USING: a ;\n: big ( -- ) ");
        for (int i = 0; i < 2000; i++) body.append("dup pubw swap ");
        body.append(";\n");
        PsiFile file = myFixture.configureByText("big.fth", body.toString());
        List<FLWordRef> refs = new java.util.ArrayList<>(com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, FLWordRef.class));
        assertEquals(6000, refs.size());
        int before = FLIndexLookup.QUERIES.get();
        for (FLWordRef r : refs) assertNotNull(r.getReference().resolve());
        int firstPass = FLIndexLookup.QUERIES.get() - before;
        assertTrue("index queries for 6000 references: " + firstPass, firstPass <= 8); // user:x, a:x, core:x per name
        // a keystroke invalidates the caches, but resolving again still costs one query per name, not per reference
        myFixture.getEditor().getCaretModel().moveToOffset(0);
        myFixture.type(" ");
        PsiFile edited = myFixture.getFile();
        com.intellij.psi.PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        before = FLIndexLookup.QUERIES.get();
        for (FLWordRef r : com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(edited, FLWordRef.class)) assertNotNull(r.getReference().resolve());
        int secondPass = FLIndexLookup.QUERIES.get() - before;
        assertTrue("index queries after an edit: " + secondPass, secondPass <= 8);
    }

    private java.util.List<String> errorDescriptions() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (com.intellij.codeInsight.daemon.impl.HighlightInfo i : myFixture.doHighlighting()) {
            if (i.getSeverity() == com.intellij.lang.annotation.HighlightSeverity.ERROR) out.add(i.getDescription());
        }
        return out;
    }
}
