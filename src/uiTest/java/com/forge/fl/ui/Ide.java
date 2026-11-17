// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.ui;

import com.intellij.remoterobot.RemoteRobot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Drives the IDE of {@code runIdeForUiTests} through its robot server, with JavaScript (Rhino) run inside the IDE. No
 * operating-system input or screen capture is used: actions are invoked, dialogs are filled and their buttons clicked
 * through their Swing components, and screenshots are the windows painted into images. So it needs no accessibility or
 * screen-recording permission and works while nobody is at the machine.
 */
final class Ide {
    final RemoteRobot robot = new RemoteRobot("http://127.0.0.1:8082");
    private final Path screenshots = Path.of(System.getProperty("ui.screenshots", "build/ui-screenshots"));
    private int shot;

    /** Common imports and helpers, prepended to every script. */
    private static final String PRELUDE = """
            importPackage(java.awt); importPackage(javax.swing);
            importClass(com.intellij.openapi.project.ProjectManager);
            importClass(com.intellij.openapi.fileEditor.FileEditorManager);
            importClass(com.intellij.openapi.application.ApplicationManager);
            function project() { var ps = ProjectManager.getInstance().getOpenProjects(); return ps.length > 0 ? ps[0] : null; }
            function editor() { return FileEditorManager.getInstance(project()).getSelectedTextEditor(); }
            function dialogs() {
              var out = []; var ws = java.awt.Window.getWindows();
              for (var i = 0; i < ws.length; i++) if (ws[i].isShowing() && ws[i] instanceof java.awt.Dialog) out.push(ws[i]);
              return out;
            }
            function find(c, pred, out) {
              if (pred(c)) out.push(c);
              if (c instanceof java.awt.Container) { var cs = c.getComponents(); for (var i = 0; i < cs.length; i++) find(cs[i], pred, out); }
              return out;
            }
            function later(f) { ApplicationManager.getApplication().invokeLater(new java.lang.Runnable({ run: f })); }
            """;

    /** Opens a project file in the editor and focuses it. */
    void open(String name) throws InterruptedException {
        run("""
                var vf = project().getBaseDir().findChild('%s');
                FileEditorManager.getInstance(project()).openFile(vf, true);
                """.formatted(name));
        waitFor("the editor", "editor() != null && editor().getVirtualFile().getName() == '" + name + "'", 30);
    }

    /** The text of the editor's document. */
    String text() {
        return call("String(editor().getDocument().getText())");
    }

    /** Selects the first occurrence of {@code code} after {@code after} (or from the start). */
    void select(String code, String after) {
        run("""
                var t = String(editor().getDocument().getText());
                var from = %s; var s = t.indexOf(%s, from);
                if (s < 0) throw 'not found';
                editor().getSelectionModel().setSelection(s, s + %s.length);
                editor().getCaretModel().moveToOffset(s + %s.length);
                """.formatted(after == null ? "0" : "t.indexOf(" + js(after) + ")", js(code), js(code), js(code)));
    }

    /** Puts the caret {@code delta} characters into the first occurrence of {@code code} after {@code after}. */
    void caret(String code, String after, int delta) {
        run("""
                var t = String(editor().getDocument().getText());
                var from = %s; var s = t.indexOf(%s, from);
                if (s < 0) throw 'not found';
                editor().getSelectionModel().removeSelection();
                editor().getCaretModel().moveToOffset(s + %d);
                """.formatted(after == null ? "0" : "t.indexOf(" + js(after) + ")", js(code), delta));
    }

    /** Invokes an action as the editor's shortcut would, later on the event thread (a modal dialog may open). */
    void action(String id) {
        run("""
                importClass(com.intellij.openapi.actionSystem.ActionManager);
                importClass(com.intellij.ide.DataManager);
                importClass(com.intellij.openapi.actionSystem.ex.ActionUtil);
                var ed = editor();
                later(function () {
                  var a = ActionManager.getInstance().getAction('%s');
                  ActionUtil.invokeAction(a, DataManager.getInstance().getDataContext(ed.getContentComponent()), 'EditorPopup', null, null);
                });
                """.formatted(id));
    }

    /** Titles of the showing dialogs, joined by " | ". */
    String dialogs() {
        return call("var d = dialogs(); var t = []; for (var i = 0; i < d.length; i++) t.push(d[i].getTitle()); t.join(' | ');");
    }

    void waitForDialog(String title) throws InterruptedException {
        waitFor("dialog " + title, "(function(){ var d = dialogs(); for (var i = 0; i < d.length; i++) if (String(d[i].getTitle()).indexOf("
                + js(title) + ") >= 0) return true; return false; })()", 20);
    }

    void waitForNoDialog() throws InterruptedException {
        waitFor("dialogs to close", "dialogs().length == 0", 20);
    }

    /** Sets the first text field of the dialog titled {@code title}. */
    void typeInDialog(String title, String value) {
        run("""
                var d = %s;
                var fields = find(d, function (c) { return c instanceof javax.swing.text.JTextComponent && c.isEditable() && c.isShowing(); }, []);
                fields[0].setText(%s);
                """.formatted(dialogExpr(title), js(value)));
    }

    /** Clicks the button of the dialog titled {@code title} whose text contains {@code label}. */
    void click(String title, String label) {
        run("""
                var d = %s;
                var bs = find(d, function (c) { return c instanceof javax.swing.AbstractButton && c.isShowing() && String(c.getText()).indexOf(%s) >= 0; }, []);
                if (bs.length == 0) throw 'no button ' + %s;
                var b = bs[0]; later(function () { b.doClick(); });
                """.formatted(dialogExpr(title), js(label), js(label)));
    }

    /** The error text shown in a dialog (a validation message), if any. */
    String dialogText(String title) {
        return call("""
                var d = %s; var ls = find(d, function (c) { return (c instanceof javax.swing.JLabel || c instanceof javax.swing.text.JTextComponent) && c.isShowing(); }, []);
                var out = []; for (var i = 0; i < ls.length; i++) { var t = ls[i].getText(); if (t != null && String(t).length > 0) out.push(String(t)); }
                out.join(' / ');
                """.formatted(dialogExpr(title)));
    }

    static String dialogExpr(String title) {
        return "(function(){ var d = dialogs(); for (var i = 0; i < d.length; i++) if (String(d[i].getTitle()).indexOf(" + js(title)
                + ") >= 0) return d[i]; throw 'no dialog " + title.replace("'", "") + "'; })()";
    }

    /** A JavaScript string literal. */
    static String js(String s) {
        StringBuilder sb = new StringBuilder("'");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c);
            }
        }
        return sb.append("'").toString();
    }

    String call(String script) {
        Object r = robot.callJs(PRELUDE + script, true);
        return r == null ? null : r.toString();
    }

    void run(String script) {
        robot.runJs(PRELUDE + script, true);
    }

    /** Polls {@code condition} (a JS expression) until it is true. */
    void waitFor(String what, String condition, int seconds) throws InterruptedException {
        for (int i = 0; i < seconds * 4; i++) {
            if ("true".equals(call("(" + condition + ") ? 'true' : 'false'"))) return;
            Thread.sleep(250);
        }
        throw new AssertionError("timed out waiting for " + what);
    }

    /** Paints every showing window (the IDE frame and any dialog) into PNG files; returns their names. */
    String screenshot(String name) throws IOException {
        String data = call("""
                importClass(java.awt.image.BufferedImage); importClass(javax.imageio.ImageIO);
                var parts = []; var ws = java.awt.Window.getWindows();
                for (var i = 0; i < ws.length; i++) {
                  var w = ws[i];
                  if (!w.isShowing() || w.getWidth() < 50 || w.getHeight() < 30) continue;
                  var img = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
                  var g = img.createGraphics(); w.printAll(g); g.dispose();
                  var bytes = new java.io.ByteArrayOutputStream(); ImageIO.write(img, "png", bytes);
                  var title = (w instanceof java.awt.Frame || w instanceof java.awt.Dialog) ? w.getTitle() : w.getClass().getSimpleName();
                  parts.push(String(title).replace(/[^A-Za-z0-9 ._-]/g, "_") + "::" + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()));
                }
                parts.join("||");
                """);
        Files.createDirectories(screenshots);
        StringBuilder names = new StringBuilder();
        int k = 0;
        for (String part : data.split("\\|\\|")) {
            if (part.isEmpty()) continue;
            int sep = part.indexOf("::");
            String file = String.format("%02d-%s-%d.png", ++shot, name, k++);
            Files.write(screenshots.resolve(file), Base64.getDecoder().decode(part.substring(sep + 2)));
            names.append(file).append(" (").append(part, 0, sep).append(") ");
        }
        System.out.println("UI-SHOT " + names.toString().strip());
        return names.toString();
    }
}
