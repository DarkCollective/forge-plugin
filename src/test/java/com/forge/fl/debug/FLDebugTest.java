// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.debug;

import com.forge.fl.FLFile;
import com.forge.fl.FLTestBase;
import com.forge.fl.diagnostics.FLDiagnostics;
import com.forge.fl.run.FLUnitFiles;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FLDebugTest extends FLTestBase {
    public void testBreakpointsOnlyOnLinesWithCode() {
        PsiFile f = myFixture.configureByText("b.fth", """
                IN: b
                \\ a comment

                : f ( -- int )
                  1 ( inline comment ) ;
                ( a block comment )
                """);
        VirtualFile vf = f.getVirtualFile();
        FLLineBreakpointType type = new FLLineBreakpointType();
        assertTrue(type.canPutAt(vf, 0, getProject()));
        assertFalse(type.canPutAt(vf, 1, getProject()));
        assertFalse(type.canPutAt(vf, 2, getProject()));
        assertTrue(type.canPutAt(vf, 3, getProject()));
        assertTrue(type.canPutAt(vf, 4, getProject()));
        assertFalse(type.canPutAt(vf, 5, getProject()));
        assertFalse(type.canPutAt(vf, 99, getProject()));
        VirtualFile txt = myFixture.configureByText("x.txt", "1 2 +\n").getVirtualFile();
        assertFalse(type.canPutAt(txt, 0, getProject()));
    }

    public void testPausedEventBecomesFramesAndValues() {
        PsiFile f = myFixture.addFileToProject("mine.fth", "IN: mine\n: inner ( n:int -- int )\n  n 2 *\n  1 + ;\n");
        // the light fixture's files are not local files: map the unit name by project file name
        FLUnitFiles files = new FLUnitFiles(getProject(), null, List.of());
        FLSuspendContext ctx = FLDebugProcess.suspendContext(JsonParser.parseString("""
                {"event":"paused","reason":"step",
                 "frames":[{"word":"mine:inner","kind":"word","unit":"mine.fth","line":3,"col":5,
                            "locals":[{"name":"n","type":"int","value":"5"}]},
                           {"word":"mine:inner","kind":"quot","unit":"mine.fth","line":4,"col":3,"locals":[]},
                           {"word":"<console>","kind":"word","unit":null,"line":1,"col":1,"locals":[]}],
                 "stack":[{"type":"int","value":"1"},{"type":"str","value":"\\"top\\""}]}
                """).getAsJsonObject(), files);
        List<FLStackFrame> frames = ctx.frames();
        assertEquals(3, frames.size());
        XSourcePosition p = frames.get(0).getSourcePosition();
        assertNotNull(p);
        assertEquals(f.getVirtualFile(), p.getFile());
        assertEquals(2, p.getLine());
        assertEquals(List.of("n"), frames.get(0).info().locals().stream().map(FLValue::getName).toList());
        assertEquals("5", frames.get(0).info().locals().get(0).value());
        assertTrue(frames.get(1).info().quotation());
        assertNull(frames.get(2).getSourcePosition());
        assertSame(frames.get(0), ctx.getActiveExecutionStack().getTopFrame());
        assertNull("no backend, no evaluator", frames.get(0).getEvaluator());
    }

    private static final String MINE = """
            IN: mine
            USING: base32 util ;
            : other ( n:int -- int ) n ;
            : inner ( n:int -- int )
              n 2 * :> d
              d 1 + ;
            """;

    /** A backend that records the commands and answers with a canned event. */
    private final class FakeBackend implements FLEvaluator.Backend {
        final List<JsonObject> sent = new ArrayList<>();
        JsonObject answer;

        @Override
        public CompletableFuture<JsonObject> evaluate(JsonObject command) {
            sent.add(command);
            return CompletableFuture.completedFuture(answer);
        }

        @Override
        public Project project() {
            return getProject();
        }
    }

    /** Evaluates synchronously (the fake backend completes at once): the value or "error: ...". */
    private static Object evaluate(FLEvaluator evaluator, String expr) {
        Object[] out = new Object[1];
        evaluator.evaluate(expr, new XDebuggerEvaluator.XEvaluationCallback() {
            @Override
            public void evaluated(@NotNull XValue result) {
                out[0] = result;
            }

            @Override
            public void errorOccurred(@NotNull String errorMessage) {
                out[0] = "error: " + errorMessage;
            }
        }, null);
        return out[0];
    }

    private FLEvaluator evaluatorAt(FakeBackend backend, PsiFile file, int line) {
        FLStackFrame.Info info = new FLStackFrame.Info("mine:inner", false, "mine.fth", line + 1, 3,
                List.of(new FLValue("n", "int", "5"), new FLValue("d", "int", "10")));
        XSourcePosition p = XDebuggerUtil.getInstance().createPosition(file.getVirtualFile(), line, 2);
        return new FLEvaluator(backend, 0, info, p);
    }

    public void testEvaluateSendsScopeOfTheFrame() {
        PsiFile f = myFixture.addFileToProject("mine.fth", MINE);
        FakeBackend backend = new FakeBackend();
        backend.answer = JsonParser.parseString(
                "{\"event\":\"evaluated\",\"ok\":true,\"stack\":[{\"type\":\"int\",\"value\":\"15\"}],\"kept\":0,\"consumed\":0,\"output\":\"\"}")
                .getAsJsonObject();
        FLEvaluator evaluator = evaluatorAt(backend, f, 5);
        Object r = evaluate(evaluator, "  n d +  ");
        assertEquals(1, backend.sent.size());
        JsonObject cmd = backend.sent.get(0);
        assertEquals("evaluate", cmd.get("cmd").getAsString());
        assertEquals("n d +", cmd.get("expr").getAsString());
        assertEquals(0, cmd.get("frame").getAsInt());
        assertEquals("mine", cmd.get("vocab").getAsString());
        assertEquals("[\"base32\",\"util\"]", cmd.get("using").toString());
        FLEvalResult result = (FLEvalResult) r;
        assertEquals(1, result.values().size());
        assertEquals("15", result.values().get(0).value());
    }

    public void testEvaluateLocalWithoutRoundTripAndErrors() {
        PsiFile f = myFixture.addFileToProject("mine.fth", MINE);
        FakeBackend backend = new FakeBackend();
        FLEvaluator evaluator = evaluatorAt(backend, f, 5);
        FLValue d = (FLValue) evaluate(evaluator, "d");
        assertEquals("10", d.value());
        assertTrue(backend.sent.isEmpty());
        assertEquals("error: Nothing to evaluate", evaluate(evaluator, "   "));
        backend.answer = JsonParser.parseString(
                "{\"event\":\"evaluated\",\"ok\":false,\"error\":\"E_UNDEFINED: undefined word 'x'\",\"output\":\"hi\\n\"}")
                .getAsJsonObject();
        assertEquals("error: E_UNDEFINED: undefined word 'x'\nprinted: hi", evaluate(evaluator, "\"hi\" . x"));
        // console code (no position): the runner picks the scope
        FLEvaluator console = new FLEvaluator(backend, 2, new FLStackFrame.Info("<console>", false, null, null, null, List.of()), null);
        JsonObject cmd = console.command("1 2 +");
        assertEquals(2, cmd.get("frame").getAsInt());
        assertFalse(cmd.has("vocab"));
        assertFalse(cmd.has("using"));
    }

    public void testEvaluatedEventBecomesResult() {
        // paused stack [1 2]; the expression dropped 2 and pushed "a" and 3: kept 1, consumed 1
        FLEvalResult r = FLEvalResult.of(JsonParser.parseString("""
                {"event":"evaluated","id":3,"ok":true,"kept":1,"consumed":1,"output":"x\\n",
                 "stack":[{"type":"int","value":"1"},{"type":"str","value":"\\"a\\""},{"type":"int","value":"3"}]}
                """).getAsJsonObject());
        assertNull(r.error());
        assertEquals(List.of("[0]", "[1]"), r.values().stream().map(FLValue::getName).toList());
        assertEquals(List.of("3", "\"a\""), r.values().stream().map(FLValue::value).toList());
        assertEquals("2 values, took 1 from the stack, printed output", r.summary());
        FLEvalResult empty = FLEvalResult.of(JsonParser.parseString(
                "{\"event\":\"evaluated\",\"ok\":true,\"kept\":0,\"consumed\":0,\"output\":\"\",\"stack\":[]}").getAsJsonObject());
        assertEquals("no values", empty.summary());
        FLEvalResult failed = FLEvalResult.of(JsonParser.parseString(
                "{\"event\":\"evaluated\",\"ok\":false,\"error\":\"E_TYPE: expected bool, got int\",\"output\":\"\"}").getAsJsonObject());
        assertEquals("E_TYPE: expected bool, got int", failed.error());
    }

    public void testHoverOnlyOnLocalsOfThePausedWord() {
        PsiFile f = myFixture.configureByText("mine.fth", MINE);
        FLEvaluator evaluator = evaluatorAt(new FakeBackend(), f, 5);
        Document doc = PsiDocumentManager.getInstance(getProject()).getDocument(f);
        assertNotNull(doc);
        String text = doc.getText();
        int body = text.indexOf("n 2 *");
        assertEquals(TextRange.from(body, 1), range(evaluator, doc, body));            // use of the parameter n
        int dDecl = text.indexOf(":> d") + 3;
        assertEquals(TextRange.from(dDecl, 1), range(evaluator, doc, dDecl));         // the :> local itself
        int dUse = text.indexOf("d 1 +");
        assertEquals(TextRange.from(dUse, 1), range(evaluator, doc, dUse));           // use of d
        assertNull(range(evaluator, doc, text.indexOf("2 *")));                       // a literal
        assertNull(range(evaluator, doc, text.indexOf("+ ;")));                       // a word
        assertNull("n of another word", range(evaluator, doc, text.indexOf("n ;")));
    }

    private TextRange range(FLEvaluator evaluator, Document doc, int offset) {
        return evaluator.getExpressionRangeAtOffset(getProject(), doc, offset, false);
    }

    public void testExpressionEditorIsAnFLFragmentWithoutDiagnostics() {
        Document doc = FLDebugProcess.EDITORS.createDocument(getProject(),
                XExpressionImpl.fromText("n undefined-word"), null, EvaluationMode.EXPRESSION);
        PsiFile fragment = PsiDocumentManager.getInstance(getProject()).getPsiFile(doc);
        assertTrue(fragment instanceof FLFile);
        assertTrue(FLFile.isDebuggerFragment(fragment));
        assertEquals(List.of(), FLDiagnostics.compute(fragment));
    }
}
