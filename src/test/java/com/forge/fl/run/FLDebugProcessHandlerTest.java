// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class FLDebugProcessHandlerTest extends UsefulTestCase {
    /** Event lines go to the listener, everything else to the console, in order; commands reach stdin. */
    public void testEventLinesAreSeparatedFromOutput() throws Exception {
        // cat echoes stdin; the shell prints output around it
        String script = "printf 'one\\n\\001FL-DEBUG {\"event\":\"ready\"}\\ntwo'; printf ' and more\\n'; cat; printf 'end\\n'";
        FLDebugProcessHandler h = new FLDebugProcessHandler(new GeneralCommandLine("/bin/sh", "-c", script));
        List<String> events = new CopyOnWriteArrayList<>();
        StringBuilder console = new StringBuilder();
        h.setEventListener(events::add);
        h.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                if (outputType == ProcessOutputTypes.STDOUT) {
                    synchronized (console) {
                        console.append(event.getText());
                    }
                }
            }
        });
        h.startNotify();
        assertTrue(h.send("\u0001FL-DEBUG {\"event\":\"paused\"}"));
        assertTrue(h.send("echoed"));
        h.getProcess().getOutputStream().close();
        assertTrue(h.waitFor(10_000));
        assertEquals(List.of("{\"event\":\"ready\"}", "{\"event\":\"paused\"}"), events);
        synchronized (console) {
            assertEquals("one\ntwo and more\nechoed\nend\n", console.toString());
        }
    }

    /**
     * Drives the bundled runner in debug mode against a built checkout: ./gradlew test -Dfl.forge=/path/to/forge.
     * Breakpoint, step into, step over back to the caller, a quotation run by map, resume to the end.
     */
    public void testRunnerDebugProtocol() throws Exception {
        String forge = System.getProperty("fl.forge");
        if (forge == null || forge.isBlank()) return;
        Path mine = Files.createTempFile("mine", ".fth");
        Files.writeString(mine, """
                IN: mine
                : inner ( n:int -- int )
                  n 2 *
                  1 + ;
                : outer ( a:int -- int )
                  a inner
                  :> b
                  { b } [ 3 + ] map 0 nth ;
                """);
        String unit = mine.getFileName().toString();
        String node = FLForgeEnvironment.findNode();
        assertNotNull("node not found", node);
        FLDebugProcessHandler h = new FLDebugProcessHandler(new GeneralCommandLine(node, FLForgeEnvironment.runnerScript().toString()));
        BlockingQueue<String> events = new LinkedBlockingQueue<>();
        StringBuilder console = new StringBuilder();
        h.setEventListener(events::add);
        h.addProcessListener(new ProcessListener() {
            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                synchronized (console) {
                    console.append(event.getText());
                }
            }
        });
        h.startNotify();
        String request = "{\"checkout\":" + json(forge) + ",\"program\":\"5 mine:outer\",\"units\":[{\"name\":" + json(unit)
                + ",\"path\":" + json(mine.toString()) + "}],\"limits\":{},\"debug\":true}";
        assertTrue(h.send(request));
        assertEquals("{\"event\":\"ready\"}", next(events));
        h.send("{\"cmd\":\"start\",\"breakpoints\":[{\"unit\":" + json(unit) + ",\"line\":6}]}");
        List<String> seen = new ArrayList<>();
        for (String cmd : List.of("stepInto", "stepOver", "stepOver", "stepOver", "stepInto", "resume")) {
            String paused = next(events);
            seen.add(summary(paused));
            h.send("{\"cmd\":\"" + cmd + "\"}");
            assertEquals("{\"event\":\"resumed\"}", next(events));
        }
        assertTrue(h.waitFor(30_000));
        assertEquals(List.of(
                "breakpoint mine:outer:6 a=5",
                "step mine:inner:3 n=5",
                "step mine:inner:4 n=5",
                "step mine:outer:7 a=5",
                "step mine:outer:8 a=5,b=11",
                "step mine:outer[q]:8 a=5,b=11"), seen);
        synchronized (console) {
            assertTrue(console.toString(), console.toString().contains("  14\n"));
        }
        assertEquals(0, h.getExitCode().intValue());
        Files.delete(mine);
    }

    private static String next(BlockingQueue<String> events) throws InterruptedException {
        String e = events.poll(30, TimeUnit.SECONDS);
        assertNotNull("no event from the runner", e);
        return e;
    }

    /** "reason word:line locals" of the innermost frame of a paused event. */
    private static String summary(String paused) {
        var o = com.google.gson.JsonParser.parseString(paused).getAsJsonObject();
        var f = o.getAsJsonArray("frames").get(0).getAsJsonObject();
        List<String> locals = new ArrayList<>();
        for (var l : f.getAsJsonArray("locals")) {
            locals.add(l.getAsJsonObject().get("name").getAsString() + "=" + l.getAsJsonObject().get("value").getAsString());
        }
        return o.get("reason").getAsString() + " " + f.get("word").getAsString() + ("quot".equals(f.get("kind").getAsString()) ? "[q]" : "")
                + ":" + f.get("line").getAsInt() + " " + String.join(",", locals);
    }

    private static String json(String s) {
        return FLRunConfiguration.json(s);
    }
}
