// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.debug;

import com.forge.fl.FLTestBase;
import com.forge.fl.run.FLUnitFiles;
import com.google.gson.JsonParser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XSourcePosition;

import java.util.List;

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
    }
}
