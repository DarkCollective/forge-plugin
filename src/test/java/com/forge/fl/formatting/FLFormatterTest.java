// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.formatting;

import com.forge.fl.FLTestBase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;

public class FLFormatterTest extends FLTestBase {
    private String format(String source) {
        PsiFile file = myFixture.configureByText("format.fth", source);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            CodeStyleManager.getInstance(getProject()).reformat(file);
        });
        return file.getText();
    }

    public void testFormatsDefinitionsAndTopLevelForms() {
        String actual = format("IN:   demo\n:  add   ( a:int   b:int   --   int )\n c   a b +   ;\n256   CONSTANT   size\n");
        assertEquals("IN: demo\n\n: add ( a:int b:int -- int )\n  c a b + ;\n\n256 CONSTANT size\n", actual);
    }

    public void testFormatsMetaClausesAndGroups() {
        String actual = format(": choose   ( x   -- )\n [ x 1 + ]   { a b }   ;\n"
                + "META: choose\n  param x \"value\" ;   alias choice ;\n;\n");
        assertEquals(": choose ( x -- )\n  [ x 1 + ] { a b } ;\n\n"
                + "META: choose\n  param x \"value\" ;\n  alias choice ;\n;\n", actual);
    }

    public void testKeepsLineCommentsOnTheirOwnLines() {
        String actual = format("\\ file comment\nIN:   demo\n: f   ( -- )\n\\ body comment\n1   ;\n");
        assertEquals("\\ file comment\nIN: demo\n\n: f ( -- )\n  \\ body comment\n  1 ;\n", actual);
    }

    public void testFormattingIsIdempotent() {
        String once = format(": f ( x -- int )\n  x  1 + ;\n");
        assertEquals(once, format(once));
    }

    public void testCommentsAboveDefinitionsShareTheirBlankLine() {
        String actual = format(": first ( -- ) ;\n\\ first comment\n\\ second comment\n: second ( -- ) ;\n");
        assertEquals(": first ( -- ) ;\n\n"
                + "\\ first comment\n\\ second comment\n"
                + ": second ( -- ) ;\n", actual);
    }

    public void testRemovesTrailingEmptyLinesAndKeepsOneFinalNewline() {
        assertEquals(": f ( -- ) ;\n", format(": f ( -- ) ;\n\n\n"));
        assertEquals(": f ( -- ) ;\n", format(": f ( -- ) ;\n  \n \t\n"));
    }

    public void testKeepsOneBlankLineBetweenCommentParagraphs() {
        String source = "\\ licence\nIN: demo\n\n\\ ---- section ----\n\n\n\\ doc of f\n: f ( -- ) ;\n";
        assertEquals("\\ licence\nIN: demo\n\n\\ ---- section ----\n\n\\ doc of f\n: f ( -- ) ;\n", format(source));
    }

    public void testFormatsV02Forms() {
        String actual = format("CHOICE:   mode option a ;   option \"b c\" \"help\" ; ;\n"
                + "MEMO:   t   ( -- int )\n 1 :>   x x ;\n"
                + ": f ( s:str m:choice<mode>=a -- ) ;\n");
        assertEquals("CHOICE: mode\n  option a ;\n  option \"b c\" \"help\" ;\n;\n\n"
                + "MEMO: t ( -- int )\n  1 :> x x ;\n\n"
                + ": f ( s:str m:choice<mode>=a -- ) ;\n", actual);
    }

    public void testRunsOfOneLineDeclarationsStayTogether() {
        assertEquals("0 VARIABLE a\n\"\" VARIABLE b\n\n1 CONSTANT c\n: f ( -- ) ;\n\n: g ( -- )\n  1 ;\n",
                format("0 VARIABLE a\n\"\" VARIABLE b\n\n\n1 CONSTANT c\n: f ( -- ) ;\n: g ( -- )\n  1 ;\n"));
        assertEquals("USING: a b\n  c d ;\n", format("USING: a b\nc d ;\n"));
    }

    public void testMultiLineQuotationContentsAreIndentedOneLevel() {
        assertEquals(": f ( b:bool -- int )\n  b [\n    1 2 +\n  ] [ 0 ] if ;\n",
                format(": f ( b:bool -- int )\n  b [\n1 2 +\n] [ 0 ] if ;\n"));
        assertEquals(": f ( -- list )\n  { 1\n    2 } ;\n", format(": f ( -- list )\n  { 1\n2 } ;\n"));
    }

    public void testEndOfLineCommentsStayAligned() {
        String table = ": t ( -- list )\n  { { 1 2 }    \\ one\n    { 3 4 }    \\ two\n  } ;\n";
        assertEquals(table, format(table));
    }

    public void testDefaultIndentIsTwoSpaces() {
        assertEquals(": f ( -- )\n  1 ;\n", format(": f ( -- )\n        1 ;\n"));
    }

    public void testDoesNotAddFinalNewlineWhenThereWasNone() {
        assertEquals(": f ( -- ) ;", format(": f ( -- ) ;"));
    }
}
