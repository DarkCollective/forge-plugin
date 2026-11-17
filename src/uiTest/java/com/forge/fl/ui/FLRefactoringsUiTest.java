// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.ui;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The FL refactorings, inlay hints, parameter info and live templates in a running IDE (./gradlew runIdeForUiTests,
 * then ./gradlew uiTest). Each step uses the real action and its real dialog, and leaves screenshots in
 * build/ui-screenshots. The steps share the editor and run in name order.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FLRefactoringsUiTest {
    static final Ide ide = new Ide();

    static final String DEMO = """
            IN: demo

            \\ A word to extract from.
            : total ( a:int b:int n:int=2 -- int )
              a b + n *
              1 + ;

            : twice ( n:int -- int ) n 2 * ;
            : use-twice ( -- int ) 5 twice ;

            : pad ( s:str width:int fill:str=" " -- str ) s ;

            META: pad
              doc "Pad." ;
              param width "Width" ;
              param fill "Fill" ;
            ;

            : call-pad ( -- str ) "x" 5 "-" pad ;

            : wrong ( a:int -- int ) a 1 ;

            : body ( b:bool -- int )
              b\s
              ;
            """;

    @BeforeClass
    public static void start() throws Exception {
        ide.waitFor("the project", "project() != null", 180);
        ide.waitFor("indexing", "!com.intellij.openapi.project.DumbService.isDumb(project())", 300);
        // popups left by an earlier run
        ide.run("""
                var ws = java.awt.Window.getWindows();
                for (var i = 0; i < ws.length; i++) {
                  if (!ws[i].isShowing() || ws[i] instanceof java.awt.Frame || ws[i] instanceof java.awt.Dialog) continue;
                  var cs = find(ws[i], function (c) { return c instanceof javax.swing.JComponent; }, []);
                  for (var j = 0; j < cs.length; j++) { var p = com.intellij.openapi.ui.popup.util.PopupUtil.getPopupContainerFor(cs[j]); if (p != null) { p.cancel(); break; } }
                }
                """);
        ide.open("demo.fth");
        // a fresh copy of the demo file for each run
        ide.run("""
                importClass(com.intellij.openapi.command.WriteCommandAction);
                var doc = editor().getDocument();
                WriteCommandAction.runWriteCommandAction(project(), new java.lang.Runnable({ run: function () { doc.setText(%s); } }));
                com.intellij.psi.PsiDocumentManager.getInstance(project()).commitAllDocuments();
                """.formatted(Ide.js(DEMO)));
        Thread.sleep(3000); // highlighting and inlay hints
    }

    @Test
    public void a1_editorShowsHintsAndTheInspection() throws Exception {
        ide.screenshot("editor");
        String highlights = ide.call("""
                importClass(com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl);
                var infos = DaemonCodeAnalyzerImpl.getHighlights(editor().getDocument(), null, project());
                var out = []; for (var i = 0; i < infos.size(); i++) { var d = infos.get(i).getDescription(); if (d != null) out.push(String(d)); }
                out.join(' || ');
                """);
        System.out.println("UI-HIGHLIGHTS " + highlights);
        assertTrue(highlights, highlights.contains("E_EFFECT: at the end of the body the stack holds 2 values"));
        String inlays = ide.call("""
                var out = []; var ins = editor().getInlayModel().getInlineElementsInRange(0, editor().getDocument().getTextLength());
                for (var i = 0; i < ins.size(); i++) out.push(String(ins.get(i).getRenderer()));
                var eol = editor().getInlayModel().getAfterLineEndElementsInRange(0, editor().getDocument().getTextLength());
                out.push('eol=' + eol.size());
                out.join(' | ');
                """);
        System.out.println("UI-INLAYS " + inlays);
    }

    /**
     * Parameter info through the platform's own context and the registered handler. The popup itself needs keyboard
     * focus, which a background IDE does not get on macOS, so the check stops at what the handler finds.
     */
    @Test
    public void a2_parameterInfo() throws Exception {
        ide.caret("5 \"-\" pad", null, 0);
        String found = ide.call("""
                importClass(com.intellij.codeInsight.hint.ShowParameterInfoContext);
                var ed = editor(); var file = com.intellij.psi.PsiDocumentManager.getInstance(project()).getPsiFile(ed.getDocument());
                var h = com.intellij.lang.parameterInfo.LanguageParameterInfo.INSTANCE.allForLanguage(com.intellij.lang.Language.findLanguageByID("FL")).get(0);
                var ctx = new ShowParameterInfoContext(ed, project(), file, ed.getCaretModel().getOffset(), -1);
                var el = h.findElementForParameterInfo(ctx);
                'element=' + (el == null ? null : el.getText()) + ' items=' + (ctx.getItemsToShow() == null ? 0 : ctx.getItemsToShow().length);
                """);
        System.out.println("UI-PARAMINFO " + found);
        assertEquals("element=pad items=1", found);
    }

    @Test
    public void b1_extractWord() throws Exception {
        ide.select("a b + n *", ": total");
        ide.action("ExtractMethod");
        ide.waitForDialog("Extract Word");
        ide.screenshot("extract-dialog");
        System.out.println("UI-DIALOG " + ide.dialogText("Extract Word"));
        ide.typeInDialog("Extract Word", "sum-times");
        Thread.sleep(500);
        ide.screenshot("extract-name");
        ide.click("Extract Word", "OK");
        ide.waitForNoDialog();
        Thread.sleep(500);
        String t = ide.text();
        assertTrue(t, t.contains(": sum-times ( a:int b:int n:int -- any )\n  a b + n * ;\n\n\\ A word to extract from.\n: total"));
        assertTrue(t, t.contains("  a b n sum-times\n  1 + ;"));
        ide.screenshot("extract-done");
    }

    @Test
    public void b2_introduceLocal() throws Exception {
        ide.select("a b n sum-times", null);
        ide.action("IntroduceVariable");
        ide.waitForDialog("Introduce :> Local");
        ide.screenshot("introduce-dialog");
        ide.typeInDialog("Introduce :> Local", "a");
        Thread.sleep(500);
        ide.screenshot("introduce-clash");
        System.out.println("UI-DIALOG " + ide.dialogText("Introduce :> Local"));
        ide.typeInDialog("Introduce :> Local", "sum");
        Thread.sleep(300);
        ide.click("Introduce :> Local", "OK");
        ide.waitForNoDialog();
        Thread.sleep(500);
        String t = ide.text();
        assertTrue(t, t.contains("  a b n sum-times :> sum\n  sum\n  1 + ;"));
    }

    @Test
    public void b3_inlineWord() throws Exception {
        ide.caret("twice ;", ": use-twice", 1);
        ide.action("Inline");
        ide.waitForDialog("Inline Word");
        ide.screenshot("inline-dialog");
        System.out.println("UI-DIALOG " + ide.dialogText("Inline Word"));
        ide.click("Inline Word", "All 1");
        ide.waitForNoDialog();
        Thread.sleep(500);
        String t = ide.text();
        assertTrue(t, t.contains(": use-twice ( -- int ) 5 2 * ;"));
        assertTrue("the word is gone", !t.contains(": twice"));
    }

    @Test
    public void c1_changeSignature() throws Exception {
        ide.caret("pad ;", ": call-pad", 1);
        ide.action("ChangeSignature");
        ide.waitForDialog("Change Signature");
        ide.screenshot("signature-dialog");
        // swap width and fill, rename fill to ch, add right:bool with true at calls
        ide.run("""
                var d = %s;
                var t = find(d, function (c) { return c instanceof javax.swing.JTable; }, [])[0];
                var m = t.getModel();
                m.exchangeRows(1, 2);
                m.setValueAt('ch:str=" "', 1, 0);
                t.getSelectionModel().setSelectionInterval(2, 2);
                """.formatted(Ide.dialogExpr("Change Signature")));
        // the toolbar's Add button
        ide.run("""
                var d = %s;
                var bs = find(d, function (c) { return c.getClass().getName().indexOf('ActionButton') >= 0 && c.isShowing()
                    && String(c.getAction().getTemplatePresentation().getText()).indexOf('Add') >= 0; }, []);
                if (bs.length == 0) throw 'no Add button';
                var b = bs[0]; later(function () { b.click(); });
                """.formatted(Ide.dialogExpr("Change Signature")));
        Thread.sleep(500);
        ide.run("""
                var d = %s;
                var t = find(d, function (c) { return c instanceof javax.swing.JTable; }, [])[0];
                var m = t.getModel();
                m.setValueAt('right:bool=false', m.getRowCount() - 1, 0);
                m.setValueAt('true', m.getRowCount() - 1, 1);
                """.formatted(Ide.dialogExpr("Change Signature")));
        Thread.sleep(500);
        ide.screenshot("signature-edited");
        System.out.println("UI-DIALOG " + ide.dialogText("Change Signature"));
        ide.click("Change Signature", "OK");
        ide.waitForNoDialog();
        Thread.sleep(500);
        String t = ide.text();
        assertTrue(t, t.contains(": pad ( s:str ch:str=\" \" width:int right:bool=false -- str ) s ;"));
        assertTrue(t, t.contains("  param ch \"Fill\" ;\n  param width \"Width\" ;"));
        assertTrue(t, t.contains(": call-pad ( -- str ) \"x\" \"-\" 5 true pad ;"));
        ide.screenshot("signature-done");
    }

    @Test
    public void d1_liveTemplate() throws Exception {
        ide.caret("b \n  ;", ": body", 2);
        ide.run("""
                importClass(com.intellij.openapi.command.WriteCommandAction);
                var ed = editor(); var at = ed.getCaretModel().getOffset();
                WriteCommandAction.runWriteCommandAction(project(), new java.lang.Runnable({ run: function () {
                  ed.getDocument().insertString(at, 'ife'); ed.getCaretModel().moveToOffset(at + 3); } }));
                com.intellij.psi.PsiDocumentManager.getInstance(project()).commitAllDocuments();
                """);
        ide.action("ExpandLiveTemplateByTab");
        Thread.sleep(1000);
        ide.screenshot("template-editing");
        ide.run("""
                importClass(com.intellij.codeInsight.template.impl.TemplateManagerImpl);
                importClass(com.intellij.openapi.command.WriteCommandAction);
                var st = TemplateManagerImpl.getTemplateState(editor());
                if (st != null) WriteCommandAction.runWriteCommandAction(project(), new java.lang.Runnable({ run: function () { st.gotoEnd(false); } }));
                """);
        String t = ide.text();
        assertTrue(t, t.contains("  b [  ] [  ] if\n  ;"));
    }

    @Test
    public void d2_surroundWithQuotation() throws Exception {
        ide.select("1 +", "sum\n");
        ide.action("SurroundWithLiveTemplate");
        Thread.sleep(1500);
        ide.screenshot("surround-popup");
        String items = ide.call("""
                var out = []; var ws = java.awt.Window.getWindows();
                for (var i = 0; i < ws.length; i++) if (ws[i].isShowing()) find(ws[i], function (c) { return c instanceof javax.swing.JList && c.isShowing(); }, out);
                var t = []; for (var j = 0; j < out.length; j++) { var m = out[j].getModel(); for (var k = 0; k < m.getSize(); k++) t.push(String(m.getElementAt(k))); }
                t.join(' | ');
                """);
        System.out.println("UI-SURROUND " + items);
        // choose the quotation template in the popup (as a click on it would)
        ide.run("""
                var out = []; var ws = java.awt.Window.getWindows();
                for (var i = 0; i < ws.length; i++) if (ws[i].isShowing()) find(ws[i], function (c) { return c instanceof javax.swing.JList && c.isShowing(); }, out);
                var list = out[0]; var m = list.getModel(); var pick = -1;
                for (var k = 0; k < m.getSize(); k++) if (String(m.getElementAt(k)).indexOf('quotation') >= 0) pick = k;
                if (pick < 0) throw 'no quotation item';
                var popup = com.intellij.openapi.ui.popup.util.PopupUtil.getPopupContainerFor(list);
                later(function () { list.setSelectedIndex(pick); popup.handleSelect(true); });
                """);
        Thread.sleep(1000);
        String t = ide.text();
        assertTrue(t, t.contains("  sum\n  [ 1 + ] ;"));
        ide.screenshot("surround-done");
    }

    @Test
    public void z_save() throws Exception {
        Files.writeString(Path.of(System.getProperty("ui.screenshots", "build/ui-screenshots"), "demo-after.fth"), ide.text());
        assertEquals("", ide.dialogs());
    }
}
