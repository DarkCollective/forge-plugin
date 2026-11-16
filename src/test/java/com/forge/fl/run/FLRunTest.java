// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.forge.fl.FLTestBase;
import com.forge.fl.psi.FLDefinition;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jdom.Element;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class FLRunTest extends FLTestBase {
    private FLDefinition definition(String text, String name) {
        PsiFile f = myFixture.configureByText("t.fth", text);
        for (FLDefinition d : PsiTreeUtil.findChildrenOfType(f, FLDefinition.class)) {
            if (name.equals(d.getName())) return d;
        }
        throw new AssertionError("no definition " + name);
    }

    public void testSlotsFromHeader() {
        FLDefinition def = definition("""
                IN: demo
                : f ( bytes alphabet:choice{standard|"hex x"}=standard padding:bool=true n:int[1..]=5 s:str="a=b" x -- str ) ;
                """, "f");
        List<FLRunInputs.Slot> slots = FLRunInputs.slots(def);
        assertNotNull(slots);
        assertEquals(6, slots.size());
        assertEquals(new FLRunInputs.Slot(null, "bytes", null, null), slots.get(0));
        assertEquals(new FLRunInputs.Slot("alphabet", "choice", "standard", List.of("standard", "hex x")), slots.get(1));
        assertEquals(new FLRunInputs.Slot("padding", "bool", "true", null), slots.get(2));
        assertEquals(new FLRunInputs.Slot("n", "int", "5", null), slots.get(3));
        assertEquals(new FLRunInputs.Slot("s", "str", "\"a=b\"", null), slots.get(4));
        assertEquals(new FLRunInputs.Slot("x", "any", null, null), slots.get(5));
        assertEquals("demo:f", FLRunInputs.qualifiedName(def));
    }

    public void testSharedChoiceOptions() {
        FLDefinition def = definition("""
                IN: demo
                CHOICE: padding
                  option pkcs7 "PKCS#7 padding." ;
                  option "zero bytes" ;
                ;
                : pad ( data:bytes p:choice<padding>=pkcs7 -- bytes ) data ;
                """, "pad");
        List<FLRunInputs.Slot> slots = FLRunInputs.slots(def);
        assertNotNull(slots);
        assertEquals(List.of("pkcs7", "zero bytes"), slots.get(1).options());
        assertEquals("pkcs7", slots.get(1).defaultText());
    }

    public void testNoInputsAndMemo() {
        assertEquals(List.of(), FLRunInputs.slots(definition("IN: demo\nMEMO: table ( -- list ) [ 1 2 ] ;\n", "table")));
        assertNull(FLRunInputs.slots(definition("IN: demo\n: odd ;\n", "odd")));
    }

    public void testLiterals() {
        FLRunInputs.Slot str = new FLRunInputs.Slot("s", "str", null, null);
        assertEquals("\"he said \\\"hi\\\" \\\\o/\"", FLRunInputs.literal(str, "he said \"hi\" \\o/"));
        assertEquals("\"already\"", FLRunInputs.literal(str, "\"already\""));
        assertEquals("\"\"", FLRunInputs.literal(str, ""));
        FLRunInputs.Slot n = new FLRunInputs.Slot("n", "int", "5", null);
        assertEquals("42", FLRunInputs.literal(n, " 42 "));
        assertThrows(IllegalArgumentException.class, () -> FLRunInputs.literal(n, "  "));
        FLRunInputs.Slot bytes = new FLRunInputs.Slot(null, "bytes", null, null);
        assertEquals("#[ 01 02 ]", FLRunInputs.literal(bytes, "#[ 01 02 ]"));
        assertEquals("\"x\" 1 true demo:f", FLRunInputs.program("demo:f", List.of("\"x\"", "1", "true")));
        assertEquals("a\"b", FLRunInputs.unquote("\"a\\\"b\""));
        assertEquals("plain", FLRunInputs.unquote("plain"));
    }

    public void testLineMarkerOnWordNamesOnly() {
        FLDefinition def = definition("IN: demo\n: f ( -- int ) 1 ;\n: g ( -- int ) f ;\n", "g");
        RunLineMarkerContributor c = new FLRunLineMarkerContributor();
        assertNotNull(c.getInfo(def.getNameIdentifier()));
        PsiElement ref = PsiTreeUtil.findChildrenOfType(def, com.forge.fl.psi.FLWordRef.class).iterator().next();
        assertNull(c.getInfo(ref.getFirstChild() == null ? ref : ref.getFirstChild()));
        assertNull(c.getInfo(def.getFirstChild()));
    }

    public void testConfigurationRoundTripAndRequest() {
        FLRunConfiguration c = (FLRunConfiguration) FLRunConfigurationType.getInstance().getFactory()
                .createTemplateConfiguration(getProject());
        c.setCheckout("/forge");
        c.setNodePath("/usr/bin/node");
        c.setProgram("\"a\\b\"\n1 demo:f");
        c.setVocab("demo");
        c.setExtraFiles(List.of("/x/mine.fth"));
        c.setMaxSteps(1000);
        Element e = new Element("configuration");
        c.writeExternal(e);
        FLRunConfiguration d = (FLRunConfiguration) FLRunConfigurationType.getInstance().getFactory()
                .createTemplateConfiguration(getProject());
        d.readExternal(e);
        assertEquals(c.getProgram(), d.getProgram());
        assertEquals(List.of("/x/mine.fth"), d.getExtraFiles());
        assertEquals(1000, d.getMaxSteps());
        assertEquals(0, d.getMaxMillis());
        assertEquals("{\"checkout\":\"/forge\",\"program\":\"\\\"a\\\\b\\\"\\n1 demo:f\",\"vocab\":\"demo\","
                + "\"units\":[{\"name\":\"mine.fth\",\"path\":\"/x/mine.fth\"}],\"limits\":{\"maxSteps\":1000}}", d.requestJson());
    }

    public void testConfigureCreatesTemporaryConfigurationPerWord() {
        Path checkout = Path.of("/forge");
        RunnerAndConfigurationSettings s = FLRunWordAction.configure(getProject(), "demo:f", "1 demo:f", "demo",
                checkout, Path.of("/elsewhere/mine.fth"));
        RunnerAndConfigurationSettings again = FLRunWordAction.configure(getProject(), "demo:f", "2 demo:f", "demo",
                checkout, Path.of("/elsewhere/mine.fth"));
        assertSame(s, again);
        assertTrue(s.isTemporary());
        FLRunConfiguration c = (FLRunConfiguration) s.getConfiguration();
        assertEquals("2 demo:f", c.getProgram());
        assertEquals(List.of("/elsewhere/mine.fth"), c.getExtraFiles());
        RunnerAndConfigurationSettings bundled = FLRunWordAction.configure(getProject(), "base32:encode", "x", "base32",
                checkout, checkout.resolve("packages/ops/vocab/base32.fth"));
        assertEquals(List.of(), ((FLRunConfiguration) bundled.getConfiguration()).getExtraFiles());
        assertSame(bundled, RunManager.getInstance(getProject()).getSelectedConfiguration());
    }

    public void testConsoleLocations() {
        var m = FLRunConsoleFilter.LOCATION.matcher("  at mine:inner (mine.fth:3:32)");
        assertTrue(m.find());
        assertEquals("mine.fth", m.group(1));
        assertEquals("3", m.group(2));
        assertEquals("32", m.group(3));
        assertFalse(FLRunConsoleFilter.LOCATION.matcher("  at <program>:1:1").find());
    }

    /** Runs the bundled runner against a real checkout: ./gradlew test -Dfl.forge=/path/to/forge (built). */
    public void testRunnerAgainstForge() throws Exception {
        String forge = System.getProperty("fl.forge");
        if (forge == null || forge.isBlank()) return;
        String node = FLForgeEnvironment.findNode();
        assertNotNull("node not found", node);
        Path script = FLForgeEnvironment.runnerScript();
        Path mine = Files.createTempFile("mine", ".fth");
        Files.writeString(mine, "IN: mine\n: inner ( n:int -- int ) n \"a\" + ;\n: outer ( -- int ) 1 inner ;\n");
        Map<String, String> expected = Map.of(
                "\"hello\" \"standard\" true base32:encode", "\"NBSWY3DP\"",
                "\"abc\" base32:_alphabet", "\"ABCDEFGHIJKLMNOPQRSTUVWXYZ234567\"",
                "mine:outer", "(" + mine.getFileName() + ":2:");
        for (var entry : expected.entrySet()) {
            FLRunConfiguration c = (FLRunConfiguration) FLRunConfigurationType.getInstance().getFactory()
                    .createTemplateConfiguration(getProject());
            c.setCheckout(forge);
            c.setProgram(entry.getKey());
            c.setVocab(entry.getKey().contains("_alphabet") ? "base32" : "");
            c.setExtraFiles(List.of(mine.toString()));
            CapturingProcessHandler h = new CapturingProcessHandler(new GeneralCommandLine(node, script.toString()));
            try (OutputStream in = h.getProcess().getOutputStream()) {
                in.write(c.requestJson().getBytes(StandardCharsets.UTF_8));
            }
            ProcessOutput out = h.runProcess(60_000);
            String all = out.getStdout() + out.getStderr();
            assertTrue(entry.getKey() + " ->\n" + all, all.contains(entry.getValue()));
            assertEquals(all, entry.getKey().equals("mine:outer") ? 1 : 0, out.getExitCode());
        }
        Files.delete(mine);
    }
}
