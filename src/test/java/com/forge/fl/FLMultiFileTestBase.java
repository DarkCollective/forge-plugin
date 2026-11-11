// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;

import java.util.ArrayList;
import java.util.List;

/** Helpers for tests with several FL files in one project. Files live in the same source root. */
public abstract class FLMultiFileTestBase extends FLTestBase {
    protected PsiFile add(String name, String text) {
        return myFixture.addFileToProject(name, text);
    }

    /** Opens {@code text} (with one {@code <caret>}) as the file {@code name} and returns the reference at the caret. */
    protected PsiReference refAt(String name, String text) {
        myFixture.configureByText(name, text);
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull("no reference at the caret", ref);
        return ref;
    }

    protected PsiElement resolveAt(String name, String text) {
        return refAt(name, text).resolve();
    }

    /** All candidates of a possibly ambiguous reference, in order. */
    protected List<PsiElement> candidatesAt(String name, String text) {
        PsiReference ref = refAt(name, text);
        assertTrue("not poly-variant", ref instanceof PsiPolyVariantReference);
        List<PsiElement> out = new ArrayList<>();
        for (ResolveResult r : ((PsiPolyVariantReference) ref).multiResolve(false)) out.add(r.getElement());
        return out;
    }

    protected static String fileName(PsiElement e) {
        return e.getContainingFile().getName();
    }

    /** {@code file name + ':' + declared name}, for compact assertions. */
    protected static String where(PsiElement e) {
        return e == null ? "null" : fileName(e) + ":" + ((com.intellij.psi.PsiNamedElement) e).getName();
    }

    protected static String text(PsiFile f) {
        return f.getText();
    }
}
