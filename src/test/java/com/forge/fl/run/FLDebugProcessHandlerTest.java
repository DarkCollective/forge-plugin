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
        List<String> evaluated = new ArrayList<>();
        int step = 0;
        for (String cmd : List.of("stepInto", "stepOver", "stepOver", "stepOver", "stepInto", "resume")) {
            String paused = next(events);
            seen.add(summary(paused));
            if (step++ == 0) {
                // at the breakpoint in outer (a=5): a local, a word of the vocabulary, output, an error, a definition
                for (String expr : List.of("a 10 *", "a mine:inner", "\\\"hi\\\" . a", "nope", ": x ( -- ) ;")) {
                    h.send("{\"cmd\":\"evaluate\",\"id\":" + step + ",\"expr\":\"" + expr + "\",\"frame\":0,\"vocab\":\"mine\"}");
                    evaluated.add(evaluation(next(events)));
                }
            }
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
        assertEquals(List.of(
                "ok 50",
                "ok 11",
                "ok 5 printed hi",
                "E_UNDEFINED: undefined word 'nope'",
                "E_SYNTAX: unexpected ':'; expected a word (not a defining keyword)"), evaluated);
        synchronized (console) {
            assertTrue(console.toString(), console.toString().contains("  14\n"));
        }
        assertEquals(0, h.getExitCode().intValue());
        Files.delete(mine);
    }

    /**
     * Set Value against a built checkout whose @forge/lang has the value setters (skipped otherwise):
     * ./gradlew test -Dfl.forge=/path/to/forge. A local of the caller, a refused value, a stack entry.
     */
    public void testRunnerSetValue() throws Exception {
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
        FLDebugProcessHandler h = new FLDebugProcessHandler(new GeneralCommandLine(FLForgeEnvironment.findNode(),
                FLForgeEnvironment.runnerScript().toString()));
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
        h.send("{\"checkout\":" + json(forge) + ",\"program\":\"5 mine:outer\",\"units\":[{\"name\":" + json(unit)
                + ",\"path\":" + json(mine.toString()) + "}],\"limits\":{},\"debug\":true}");
        assertEquals("{\"event\":\"ready\"}", next(events));
        h.send("{\"cmd\":\"start\",\"breakpoints\":[{\"unit\":" + json(unit) + ",\"line\":6}]}");
        var paused = com.google.gson.JsonParser.parseString(next(events)).getAsJsonObject();
        if (!paused.has("canSetValues") || !paused.get("canSetValues").getAsBoolean()) {
            h.send("{\"cmd\":\"stop\"}");
            h.waitFor(30_000);
            Files.delete(mine);
            return; // this Forge build cannot change values
        }
        assertEquals("breakpoint mine:outer:6 a=5", summary(paused.toString()));
        // the caller's local, from an expression that uses it
        h.send(setValue(1, "local", "a", -1, "a 2 *"));
        assertEquals("ok mine:outer:6 a=10 stack:", valueSet(next(events)));
        h.send(setValue(2, "local", "a", -1, "1 2"));
        assertEquals("the new value must be exactly one value; the expression left 2 and took 0 from the stack",
                valueSet(next(events)));
        stepAndExpect(h, events, "stepInto", "step mine:inner:3 n=10");
        // a header input keeps its type
        h.send(setValue(3, "local", "n", -1, "\\\"s\\\""));
        assertTrue(valueSet(next(events)).startsWith("E_"));
        stepAndExpect(h, events, "stepOver", "step mine:inner:4 n=10");
        // the stack entry 20 (index 0, deepest first) becomes 100: inner returns 101, outer 104
        h.send(setValue(4, "stack", null, 0, "100"));
        assertEquals("ok mine:inner:4 n=10 stack:100", valueSet(next(events)));
        h.send("{\"cmd\":\"resume\"}");
        assertEquals("{\"event\":\"resumed\"}", next(events));
        assertTrue(h.waitFor(30_000));
        synchronized (console) {
            assertTrue(console.toString(), console.toString().contains("  104\n"));
        }
        Files.delete(mine);
    }

    private static void stepAndExpect(FLDebugProcessHandler h, BlockingQueue<String> events, String cmd, String expected)
            throws InterruptedException {
        h.send("{\"cmd\":\"" + cmd + "\"}");
        assertEquals("{\"event\":\"resumed\"}", next(events));
        assertEquals(expected, summary(next(events)));
    }

    private static String setValue(int id, String target, String name, int index, String expr) {
        return "{\"cmd\":\"setValue\",\"id\":" + id + ",\"frame\":0,\"vocab\":\"mine\",\"target\":\"" + target + "\""
                + (name != null ? ",\"name\":\"" + name + "\"" : ",\"index\":" + index) + ",\"expr\":\"" + expr + "\"}";
    }

    /** "ok <innermost frame> stack:<values deepest first>" or the error of a valueSet event. */
    private static String valueSet(String event) {
        var o = com.google.gson.JsonParser.parseString(event).getAsJsonObject();
        assertEquals("valueSet", o.get("event").getAsString());
        if (!o.get("ok").getAsBoolean()) return o.get("error").getAsString();
        var f = o.getAsJsonArray("frames").get(0).getAsJsonObject();
        List<String> locals = new ArrayList<>();
        for (var l : f.getAsJsonArray("locals")) {
            locals.add(l.getAsJsonObject().get("name").getAsString() + "=" + l.getAsJsonObject().get("value").getAsString());
        }
        List<String> stack = new ArrayList<>();
        for (var v : o.getAsJsonArray("stack")) stack.add(v.getAsJsonObject().get("value").getAsString());
        return "ok " + f.get("word").getAsString() + ":" + f.get("line").getAsInt() + " " + String.join(",", locals)
                + " stack:" + String.join(" ", stack);
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

    /** "ok <new values, deepest first> [printed <output>]" or the error of an evaluated event. */
    private static String evaluation(String event) {
        var o = com.google.gson.JsonParser.parseString(event).getAsJsonObject();
        assertEquals("evaluated", o.get("event").getAsString());
        if (!o.get("ok").getAsBoolean()) return o.get("error").getAsString();
        List<String> values = new ArrayList<>();
        var stack = o.getAsJsonArray("stack");
        for (int i = o.get("kept").getAsInt(); i < stack.size(); i++) {
            values.add(stack.get(i).getAsJsonObject().get("value").getAsString());
        }
        String out = o.get("output").getAsString().strip();
        return "ok " + String.join(" ", values) + (out.isEmpty() ? "" : " printed " + out);
    }

    private static String json(String s) {
        return FLRunConfiguration.json(s);
    }
}
