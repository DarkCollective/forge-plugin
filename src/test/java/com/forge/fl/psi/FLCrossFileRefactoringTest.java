package com.forge.fl.psi;

import com.forge.fl.FLMultiFileTestBase;
import com.forge.fl.builtins.FLBuiltins;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Find usages and rename across files (docs/LANGUAGE.md 6: only uses that resolve to the declaration change). */
public class FLCrossFileRefactoringTest extends FLMultiFileTestBase {
    private void rename(String newName) {
        myFixture.renameElementAtCaret(newName);
    }

    private static Set<String> files(Collection<UsageInfo> usages) {
        return usages.stream().map(u -> u.getFile().getName()).collect(Collectors.toCollection(TreeSet::new));
    }

    // ---- find usages ------------------------------------------------------------------------------------

    public void testFindUsagesAcrossFiles() {
        add("use1.fth", "USING: lib ;\n: f ( -- int ) grab grab ;\n");
        add("use2.fth", ": g ( -- int ) lib:grab ;\n");
        add("other.fth", "IN: other\n: h ( -- int ) grab ;\n"); // does not see lib
        myFixture.configureByText("lib.fth", "IN: lib\n: gr<caret>ab ( -- int ) 1 ;\nMETA: grab\n  doc \"x\" ;\n;\n");
        Collection<UsageInfo> usages = myFixture.findUsages(myFixture.getElementAtCaret());
        assertEquals(4, usages.size()); // two in use1, one in use2, the META: target
        assertEquals(Set.of("lib.fth", "use1.fth", "use2.fth"), files(usages));
    }

    public void testFindUsagesOfSymbolNamedWordsAcrossFiles() {
        add("use1.fth", "USING: lib ;\n: f ( n:int -- str ) n >hex n >hex ;\n: g ( -- ) 1 +! ;\n");
        add("use2.fth", ": h ( n:int -- str ) n lib:>hex ;\n");
        myFixture.configureByText("lib.fth", "IN: lib\n: >he<caret>x ( n:int -- str ) n ;\n: +! ( n:int -- ) ;\n");
        assertEquals(3, myFixture.findUsages(myFixture.getElementAtCaret()).size());
        PsiFile lib = myFixture.getFile();
        PsiElement plus = ((FLScope.of(lib)).find("lib", "+!")).get(0).element();
        Collection<UsageInfo> usages = myFixture.findUsages(plus);
        assertEquals(1, usages.size());
        assertEquals("use1.fth", usages.iterator().next().getFile().getName());
    }

    public void testFindUsagesOfBuiltinWord() {
        add("use1.fth", ": f ( -- ) dup dup ;\n");
        add("use2.fth", "IN: z\n: g ( -- ) dup ;\n: dup ( -- ) ;\n: h ( -- ) dup ;\n"); // the last one is z:dup
        PsiElement dup = resolveAt("use3.fth", ": k ( -- ) <caret>dup ;\n");
        assertTrue(FLBuiltins.isBuiltin(dup));
        Collection<UsageInfo> usages = myFixture.findUsages(dup);
        // use1 (2), use2 first body (1: later definition does not count), use3 (1); not z:dup's own use
        assertEquals(4, usages.size());
    }

    public void testFindUsagesOfVariableCoversBangForm() {
        add("b.fth", "IN: b\n: f ( -- ) 1 a:ctr! a:ctr ;\n");
        add("c.fth", "USING: a ;\n: g ( -- ) ctr 2 ctr! ;\n");
        myFixture.configureByText("a.fth", "IN: a\n0 VARIABLE ct<caret>r\n");
        assertEquals(4, myFixture.findUsages(myFixture.getElementAtCaret()).size());
    }

    public void testFindUsagesOfPrivateWordStaysInItsVocabulary() {
        add("p2.fth", "IN: p\n: other ( -- ) _help ;\n");
        add("q.fth", "IN: q\n: _help ( -- ) ;\n: u ( -- ) _help ;\n");
        add("q2.fth", "IN: q\n: w ( -- ) p:_help ;\n");
        myFixture.configureByText("p1.fth", "IN: p\n: _he<caret>lp ( -- ) ;\n: pub ( -- ) _help ;\n");
        Collection<UsageInfo> usages = myFixture.findUsages(myFixture.getElementAtCaret());
        assertEquals(Set.of("p1.fth", "p2.fth"), files(usages));
        assertEquals(2, usages.size());
    }

    // ---- rename -----------------------------------------------------------------------------------------

    public void testRenameWordFromDeclarationUpdatesAllFiles() {
        PsiFile use1 = add("use1.fth", "USING: lib ;\n: f ( -- int ) grab grab ;\n");
        PsiFile use2 = add("use2.fth", "IN: z\n: g ( -- int ) lib:grab ;\n");
        PsiFile meta = add("meta.fth", "IN: lib\nMETA: grab\n  doc \"x\" ;\n;\n");
        myFixture.configureByText("lib.fth", "IN: lib\n: gr<caret>ab ( -- int ) 1 ;\n");
        rename("fetch");
        assertEquals("IN: lib\n: fetch ( -- int ) 1 ;\n", myFixture.getFile().getText());
        assertEquals("USING: lib ;\n: f ( -- int ) fetch fetch ;\n", text(use1));
        assertEquals("IN: z\n: g ( -- int ) lib:fetch ;\n", text(use2));
        assertEquals("IN: lib\nMETA: fetch\n  doc \"x\" ;\n;\n", text(meta));
    }

    public void testRenameWordFromUsageInAnotherFile() {
        PsiFile lib = add("lib.fth", "IN: lib\n: grab ( -- int ) 1 ;\n");
        PsiFile use2 = add("use2.fth", "USING: lib ;\n: g ( -- int ) grab ;\n");
        myFixture.configureByText("use1.fth", "USING: lib ;\n: f ( -- int ) gr<caret>ab ;\n");
        rename("fetch");
        assertEquals("IN: lib\n: fetch ( -- int ) 1 ;\n", text(lib));
        assertEquals("USING: lib ;\n: f ( -- int ) fetch ;\n", myFixture.getFile().getText());
        assertEquals("USING: lib ;\n: g ( -- int ) fetch ;\n", text(use2));
    }

    public void testRenameSymbolNamedWordsAcrossFiles() {
        PsiFile use = add("use.fth", "USING: lib ;\n: f ( n:int -- str ) n >hex 1 +! ;\n");
        myFixture.configureByText("lib.fth", "IN: lib\n: >he<caret>x ( n:int -- str ) n ;\n: +! ( n:int -- ) ;\n");
        PsiFile lib = myFixture.getFile();
        rename("to-hex");
        assertEquals("USING: lib ;\n: f ( n:int -- str ) n to-hex 1 +! ;\n", text(use));
        assertEquals("IN: lib\n: to-hex ( n:int -- str ) n ;\n: +! ( n:int -- ) ;\n", myFixture.getFile().getText());
        // and the other way round: a symbol name from a usage, renamed to another symbol name
        PsiFile use2 = add("use2.fth", "USING: lib ;\n: g ( -- ) 2 +<caret>! ;\n");
        myFixture.configureFromExistingVirtualFile(use2.getVirtualFile());
        rename("+=");
        assertEquals("USING: lib ;\n: g ( -- ) 2 += ;\n", text(use2));
        assertEquals("IN: lib\n: to-hex ( n:int -- str ) n ;\n: += ( n:int -- ) ;\n", text(lib));
        assertEquals("USING: lib ;\n: f ( n:int -- str ) n to-hex 1 += ;\n", text(use));
    }

    public void testRenameVariableUpdatesBangFormsAndQualifiedUsesInOtherFiles() {
        PsiFile b = add("b.fth", "IN: b\n: f ( -- ) 1 a:ctr! a:ctr ;\n");
        PsiFile c = add("c.fth", "USING: a ;\n: g ( -- ) ctr 2 ctr! ;\n");
        myFixture.configureByText("a.fth", "IN: a\n0 VARIABLE c<caret>tr\n");
        rename("n");
        assertEquals("IN: a\n0 VARIABLE n\n", myFixture.getFile().getText());
        assertEquals("IN: b\n: f ( -- ) 1 a:n! a:n ;\n", text(b));
        assertEquals("USING: a ;\n: g ( -- ) n 2 n! ;\n", text(c));
    }

    public void testRenameVariableFromBangUseInAnotherFile() {
        PsiFile a = add("a.fth", "IN: a\n0 VARIABLE ctr\n");
        myFixture.configureByText("b.fth", "IN: b\n: f ( -- ) 1 a:c<caret>tr! ;\n");
        rename("n");
        assertEquals("IN: a\n0 VARIABLE n\n", text(a));
        assertEquals("IN: b\n: f ( -- ) 1 a:n! ;\n", myFixture.getFile().getText());
    }

    public void testRenamePrivateWordInAllFilesOfItsVocabularyOnly() {
        PsiFile p2 = add("p2.fth", "IN: p\n: other ( -- ) _help ;\n");
        PsiFile q = add("q.fth", "IN: q\n: _help ( -- ) ;\n: u ( -- ) _help ;\n");
        PsiFile q2 = add("q2.fth", "IN: q\n: w ( -- ) p:_help ;\n");
        myFixture.configureByText("p1.fth", "IN: p\n: _he<caret>lp ( -- ) ;\n: pub ( -- ) _help p:_help ;\n");
        rename("_aid");
        assertEquals("IN: p\n: _aid ( -- ) ;\n: pub ( -- ) _aid p:_aid ;\n", myFixture.getFile().getText());
        assertEquals("IN: p\n: other ( -- ) _aid ;\n", text(p2));
        assertEquals("IN: q\n: _help ( -- ) ;\n: u ( -- ) _help ;\n", text(q));
        assertEquals("IN: q\n: w ( -- ) p:_help ;\n", text(q2)); // q cannot see p:_help: not a use of it
    }

    public void testSameNamedWordOfAnotherVocabularyIsNotRenamed() {
        PsiFile b = add("b.fth", "IN: b\n: foo ( -- ) ;\n: u ( -- ) foo ;\n: v ( -- ) b:foo ;\n");
        PsiFile m = add("m.fth", "USING: a ;\n: f ( -- ) foo a:foo b:foo ;\n");
        myFixture.configureByText("a.fth", "IN: a\n: fo<caret>o ( -- ) ;\n");
        rename("baz");
        assertEquals("IN: a\n: baz ( -- ) ;\n", myFixture.getFile().getText());
        assertEquals("USING: a ;\n: f ( -- ) baz a:baz b:foo ;\n", text(m));
        assertEquals("IN: b\n: foo ( -- ) ;\n: u ( -- ) foo ;\n: v ( -- ) b:foo ;\n", text(b));
    }

    public void testShadowedUsesAreNotRenamed() {
        // a parameter, a word of the current vocabulary and an earlier non-import all shadow a:foo
        PsiFile param = add("param.fth", "USING: a ;\n: h ( foo:int -- int ) foo ;\n");
        PsiFile own = add("own.fth", "IN: own\nUSING: a ;\n: foo ( -- ) ;\n: k ( -- ) foo ;\n");
        PsiFile imp = add("imp.fth", "USING: a ;\n: k ( -- ) foo ;\n");
        PsiFile before = add("before.fth", ": k ( -- ) foo ;\nUSING: a ;\n"); // USING: after the use: no import yet
        myFixture.configureByText("a.fth", "IN: a\n: fo<caret>o ( -- ) ;\n");
        rename("baz");
        assertEquals("USING: a ;\n: h ( foo:int -- int ) foo ;\n", text(param));
        assertEquals("IN: own\nUSING: a ;\n: foo ( -- ) ;\n: k ( -- ) foo ;\n", text(own));
        assertEquals("USING: a ;\n: k ( -- ) baz ;\n", text(imp));
        assertEquals(": k ( -- ) foo ;\nUSING: a ;\n", text(before));
    }

    public void testAmbiguousUseIsLeftAlone() {
        PsiFile m = add("m.fth", "USING: a b ;\n: f ( -- ) greet a:greet b:greet ;\n");
        add("b.fth", "IN: b\n: greet ( -- ) ;\n");
        myFixture.configureByText("a.fth", "IN: a\n: gr<caret>eet ( -- ) ;\n");
        rename("hi");
        // the ambiguous 'greet' does not compile (E_AMBIGUOUS): renaming a:greet must not decide it
        assertEquals("USING: a b ;\n: f ( -- ) greet a:hi b:greet ;\n", text(m));
    }

    public void testRenameWordUpdatesMetaTargetAndSeeAlsoInAnotherFile() {
        PsiFile meta = add("meta.fth", "IN: v\nMETA: shift\n  doc \"x\" ;\n  see-also shift ;\n;\n");
        PsiFile use = add("use.fth", "USING: v ;\n: f ( -- ) caesar shift ;\n");
        myFixture.configureByText("v.fth", "IN: v\n: sh<caret>ift ( -- ) ;\nMETA: shift\n  alias caesar ;\n;\n");
        rename("rot");
        assertEquals("IN: v\nMETA: rot\n  doc \"x\" ;\n  see-also rot ;\n;\n", text(meta));
        assertEquals("USING: v ;\n: f ( -- ) caesar rot ;\n", text(use));
        assertEquals("IN: v\n: rot ( -- ) ;\nMETA: rot\n  alias caesar ;\n;\n", myFixture.getFile().getText());
    }

    public void testRenameAliasFromUseInAnotherFile() {
        PsiFile v = add("v.fth", "IN: v\n: shift ( -- ) ;\nMETA: shift\n  alias caesar ;\n;\n");
        PsiFile other = add("other.fth", "USING: v ;\n: g ( -- ) caesar ;\n");
        myFixture.configureByText("use.fth", "USING: v ;\n: f ( -- ) cae<caret>sar shift ;\n");
        rename("cs");
        assertEquals("USING: v ;\n: f ( -- ) cs shift ;\n", myFixture.getFile().getText());
        assertEquals("USING: v ;\n: g ( -- ) cs ;\n", text(other));
        assertEquals("IN: v\n: shift ( -- ) ;\nMETA: shift\n  alias cs ;\n;\n", text(v));
    }

    // ---- built-ins are not renameable ---------------------------------------------------------------------

    public void testBuiltinCannotBeRenamed() {
        PsiFile use = add("use.fth", ": f ( -- ) dup ;\n");
        PsiElement dup = resolveAt("m.fth", ": g ( -- ) <caret>dup ;\n");
        assertTrue(FLBuiltins.isBuiltin(dup));
        PsiFile core = dup.getContainingFile();
        String coreText = core.getText();
        assertTrue("vetoed for the rename action", PsiElementRenameHandler.isVetoed(dup));
        assertFalse(new FLRefactoringSupportProvider().isMemberInplaceRenameAvailable(dup, null));
        try {
            myFixture.renameElement(dup, "duplicate");
            fail("renaming a built-in word must be refused");
        } catch (IncorrectOperationException expected) {
            // refused by the rename processor
        } catch (RuntimeException expected) {
            // refused earlier (read-only file); either way nothing may change
        }
        assertEquals(coreText, core.getText());
        assertEquals(": f ( -- ) dup ;\n", text(use));
        assertEquals(": g ( -- ) dup ;\n", myFixture.getFile().getText());
        // ordinary words are not vetoed
        PsiElement own = ((FLScope.of(use)).find("user", "f")).get(0).element();
        assertFalse(PsiElementRenameHandler.isVetoed(own));
    }
}
