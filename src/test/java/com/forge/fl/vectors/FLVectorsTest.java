// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.vectors;

import com.forge.fl.FLTestBase;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.run.FLForgeEnvironment;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.sm.runner.GeneralToSMTRunnerEventsConvertor;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jdom.Element;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FLVectorsTest extends FLTestBase {
    private static final String VECTORS = """
            [
              {
                "name": "encode \\"[x]\\" {y}",
                "op": "base32:encode",
                "params": {},
                "input": { "value": "f", "enc": "utf8" },
                "expect": { "value": "MY======", "enc": "utf8" }
              },
              { "op": "base32:decode", "params": { "a": [1, [2]] }, "input": { "value": "", "enc": "utf8" },
                "expect": { "error": "E_RANGE" } },
              {"name": "pair", "op": "base32:decode", "checks": "input", "data": {"value": "MY", "enc": "utf8"}, "expect": "pass"}
            ]
            """;

    private Path checkout;

    @Override
    protected void tearDown() throws Exception {
        try {
            if (checkout != null) FileUtil.delete(checkout.toFile());
        } finally {
            super.tearDown();
        }
    }

    /** A checkout layout on disk: packages/ops/vocab with base32.fth and base32.test.json. */
    private VirtualFile vocabDir() throws Exception {
        checkout = Files.createTempDirectory("forge");
        Path vocab = Files.createDirectories(FLForgeEnvironment.vocabDir(checkout));
        Files.writeString(vocab.resolve("base32.fth"), "IN: base32\n: encode ( s:str -- str ) s ;\n: decode ( s:str -- str ) s ;\n: other ( -- ) ;\n");
        Files.writeString(vocab.resolve("base32.test.json"), VECTORS);
        VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(vocab);
        assertNotNull(dir);
        dir.refresh(false, true);
        return dir;
    }

    public void testEntryOffsetsSkipStringsAndNesting() {
        List<Integer> offsets = FLVectorFiles.entryOffsets(VECTORS);
        assertEquals(3, offsets.size());
        for (int o : offsets) assertEquals('{', VECTORS.charAt(o));
        assertEquals(VECTORS.indexOf('['), FLVectorFiles.arrayStart(VECTORS));
        assertEquals(List.of(), FLVectorFiles.entryOffsets("{\"a\": 1}"));
        assertEquals(List.of(), FLVectorFiles.entryOffsets("[]"));
        assertEquals(List.of(1, 4), FLVectorFiles.entryOffsets("[1, \"]\"]"));
        assertEquals(-1, FLVectorFiles.entryAt(offsets, 0));
        assertEquals(0, FLVectorFiles.entryAt(offsets, offsets.get(0)));
        assertEquals(1, FLVectorFiles.entryAt(offsets, offsets.get(1) + 20));
        assertEquals("base32", FLVectorFiles.vocabOf("base32.test.json"));
    }

    private FLVectorsRunConfiguration configuration() {
        return (FLVectorsRunConfiguration) FLVectorsConfigurationType.getInstance().getFactory()
                .createTemplateConfiguration(getProject());
    }

    public void testConfigurationRequestNamesAndPersistence() {
        FLVectorsRunConfiguration c = configuration();
        c.setCheckout("/forge");
        assertEquals("Forge vectors", c.suggestedName());
        assertEquals("{\"checkout\":\"/forge\",\"files\":[]}", c.requestJson());
        c.setFiles(List.of("base32.test.json"));
        assertEquals("base32 vectors", c.suggestedName());
        c.setOp("base32:encode");
        assertEquals("base32:encode vectors", c.suggestedName());
        c.setOp("");
        c.setEntries(Map.of("base32.test.json", List.of(3, 3, 1)));
        assertEquals("base32 vectors (2 entries)", c.suggestedName());
        JsonObject r = JsonParser.parseString(c.requestJson()).getAsJsonObject();
        assertEquals("[1,3]", r.getAsJsonObject("entries").get("base32.test.json").toString());
        c.setEntries(Map.of("base32.test.json", List.of(7)));
        assertEquals("base32 #7", c.suggestedName());

        c.setOp("base32:encode");
        Element e = new Element("configuration");
        c.writeExternal(e);
        FLVectorsRunConfiguration back = configuration();
        back.readExternal(e);
        assertEquals("/forge", back.getCheckout());
        assertEquals(List.of("base32.test.json"), back.getFiles());
        assertEquals("base32:encode", back.getOp());
        assertEquals(Map.of("base32.test.json", List.of(7)), back.getEntries());
        assertEquals(Map.of("a.test.json", List.of(1, 2), "b.test.json", List.of(0)),
                FLVectorsRunConfiguration.parseEntries("a.test.json:2,1, x;b.test.json:0;bad"));
        assertNotNull("no checkout", configuration().problem());
    }

    public void testRerunFailedNarrowsToFailedEntries() {
        FLVectorsRunConfiguration c = configuration();
        c.setOp("base32:encode");
        FLVectorsRerunFailedAction.apply(c, List.of(
                "flvector://base32.test.json#3", "flvector://base32.test.json#1", "flvector://hex.test.json#0",
                "flvectors://hex.test.json", "flop://base32:encode", "flvector://bad"));
        assertEquals(List.of("hex.test.json", "base32.test.json"), c.getFiles());
        assertEquals(Map.of("base32.test.json", List.of(1, 3)), c.getEntries());
        assertEquals("", c.getOp());
    }

    public void testProducerSelections() throws Exception {
        VirtualFile dir = vocabDir();
        PsiManager psi = PsiManager.getInstance(getProject());
        PsiDirectory psiDir = psi.findDirectory(dir);
        assertNotNull(psiDir);
        var all = FLVectorsConfigurationProducer.selectionAt(psiDir);
        assertNotNull(all);
        assertEquals(checkout, all.checkout());
        assertEquals(List.of(), all.files());

        PsiFile vectors = psi.findFile(dir.findChild("base32.test.json"));
        assertNotNull(vectors);
        var whole = FLVectorsConfigurationProducer.selectionAt(vectors);
        assertEquals(List.of("base32.test.json"), whole.files());
        assertEquals(Map.of(), whole.entries());
        List<Integer> offsets = FLVectorFiles.entryOffsets(vectors);
        PsiElement inSecond = vectors.findElementAt(offsets.get(1) + 3);
        assertNotNull(inSecond);
        if (inSecond.getTextRange().getStartOffset() >= offsets.get(1)) {
            // a JSON PSI (the platform's JSON support): the element inside entry 1 selects it
            assertEquals(Map.of("base32.test.json", List.of(1)), FLVectorsConfigurationProducer.selectionAt(inSecond).entries());
        }

        PsiFile fl = psi.findFile(dir.findChild("base32.fth"));
        assertNotNull(fl);
        FLDefinition encode = PsiTreeUtil.findChildrenOfType(fl, FLDefinition.class).stream()
                .filter(d -> "encode".equals(d.getName())).findFirst().orElseThrow();
        var op = FLVectorsConfigurationProducer.selectionAt(encode.getNameIdentifier());
        assertEquals("base32:encode", op.op());
        assertEquals(List.of("base32.test.json"), op.files());
        FLDefinition other = PsiTreeUtil.findChildrenOfType(fl, FLDefinition.class).stream()
                .filter(d -> "other".equals(d.getName())).findFirst().orElseThrow();
        assertEquals("an op without vectors: the file", "", FLVectorsConfigurationProducer.selectionAt(other).op());

        PsiFile elsewhere = myFixture.addFileToProject("x.fth", "IN: x\n: y ( -- ) ;\n");
        assertNull(FLVectorsConfigurationProducer.selectionAt(elsewhere.findElementAt(0)));
    }

    public void testLocations() throws Exception {
        VirtualFile dir = vocabDir();
        myFixture.addFileToProject("base32.fth", "IN: base32\n: encode ( s:str -- str ) s ;\n");
        FLVectorLocator locator = new FLVectorLocator(checkout);
        GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
        List<Location> file = locator.getLocation("flvectors", "base32.test.json", getProject(), scope);
        assertEquals(1, file.size());
        assertEquals(dir.findChild("base32.test.json"), file.get(0).getVirtualFile());
        List<Location> entry = locator.getLocation("flvector", "base32.test.json#2", getProject(), scope);
        assertEquals(1, entry.size());
        int at = entry.get(0).getPsiElement().getTextRange().getStartOffset();
        assertTrue(at <= FLVectorFiles.entryOffsets(VECTORS).get(2));
        List<Location> op = locator.getLocation("flop", "base32:encode", getProject(), scope);
        assertEquals(1, op.size());
        assertTrue(op.get(0).getPsiElement() instanceof FLDefinition);
        assertEquals(List.of(), locator.getLocation("flop", "base32:nothing", getProject(), scope));
        assertEquals(List.of(), locator.getLocation("other", "x", getProject(), scope));
    }

    public void testGutterIconsOnFileAndEntries() throws Exception {
        VirtualFile dir = vocabDir();
        PsiFile vectors = PsiManager.getInstance(getProject()).findFile(dir.findChild("base32.test.json"));
        assertNotNull(vectors);
        FLVectorsLineMarkerContributor contributor = new FLVectorsLineMarkerContributor();
        List<Integer> offsets = FLVectorFiles.entryOffsets(vectors);
        PsiElement first = vectors.findElementAt(offsets.get(0));
        if (first == null || first.getFirstChild() != null || !"{".equals(first.getText())) {
            return; // no JSON support in this IDE: the contributor is registered for JSON only
        }
        RunLineMarkerContributor.Info entry = contributor.getInfo(first);
        assertNotNull(entry);
        PsiElement open = vectors.findElementAt(FLVectorFiles.arrayStart(VECTORS));
        assertNotNull(contributor.getInfo(open));
        PsiElement nested = vectors.findElementAt(VECTORS.indexOf("\"input\": {") + 9);
        assertEquals("{", nested.getText());
        assertNull("an inner object is not an entry", contributor.getInfo(nested));
    }

    // ---- the runner against a built checkout ------------------------------------------------------------------

    /** A test event: kind, name, and the attributes that matter here. */
    private record Event(String kind, String name, String location, String expected, String actual) {
    }

    private static final Pattern MESSAGE = Pattern.compile("^##teamcity\\[(\\w+) (.*)]$");
    private static final Pattern ATTR = Pattern.compile("(\\w+)='((?:[^|']|\\|.)*)'");

    private static List<Event> events(String stdout) {
        List<Event> out = new ArrayList<>();
        for (String line : stdout.split("\n")) {
            Matcher m = MESSAGE.matcher(line);
            if (!m.matches()) continue;
            java.util.Map<String, String> attrs = new java.util.HashMap<>();
            Matcher a = ATTR.matcher(m.group(2));
            while (a.find()) attrs.put(a.group(1), a.group(2).replaceAll("\\|(.)", "$1").replace("|n", "\n"));
            out.add(new Event(m.group(1), attrs.get("name"), attrs.get("locationHint"), attrs.get("expected"), attrs.get("actual")));
        }
        return out;
    }

    private static ProcessOutput runVectors(String request) throws Exception {
        GeneralCommandLine cmd = new GeneralCommandLine(FLForgeEnvironment.findNode(), FLForgeEnvironment.vectorsScript().toString())
                .withCharset(StandardCharsets.UTF_8);
        CapturingProcessHandler h = new CapturingProcessHandler(cmd);
        try (OutputStream in = h.getProcess().getOutputStream()) {
            in.write(request.getBytes(StandardCharsets.UTF_8));
        }
        return h.runProcess(120_000);
    }

    /** ./gradlew test -Dfl.forge=/path/to/forge (built): base32's vectors pass, an op filter narrows them. */
    public void testRunnerAgainstForge() throws Exception {
        String forge = System.getProperty("fl.forge");
        if (forge == null || forge.isBlank()) return;
        ProcessOutput all = runVectors("{\"checkout\":" + json(forge) + ",\"files\":[\"base32.test.json\"]}");
        assertEquals(all.getStdout() + all.getStderr(), 0, all.getExitCode());
        List<Event> events = events(all.getStdout());
        long started = events.stream().filter(e -> e.kind().equals("testStarted")).count();
        long finished = events.stream().filter(e -> e.kind().equals("testFinished")).count();
        assertTrue(started > 10);
        assertEquals(started, finished);
        assertTrue(events.stream().noneMatch(e -> e.kind().equals("testFailed")));
        assertTrue(events.stream().anyMatch(e -> e.kind().equals("testSuiteStarted") && "flop://base32:encode".equals(e.location())));
        assertTrue(events.stream().anyMatch(e -> e.kind().equals("testStarted") && "coverage".equals(e.name())));
        assertTrue(events.stream().anyMatch(e -> e.kind().equals("testStarted") && "flvector://base32.test.json#0".equals(e.location())));

        ProcessOutput one = runVectors("{\"checkout\":" + json(forge) + ",\"files\":[\"base32.test.json\"],\"op\":\"base32:decode\"}");
        List<Event> some = events(one.getStdout());
        assertTrue(some.stream().filter(e -> e.kind().equals("testSuiteStarted")).allMatch(
                e -> e.name().equals("base32") || e.name().equals("base32:decode")));
        assertTrue("no coverage test for a filtered run", some.stream().noneMatch(e -> "coverage".equals(e.name())));
    }

    /** The failures, against a copy of base32.test.json with wrong expectations (symlinks to the built packages). */
    public void testRunnerFailuresAgainstForge() throws Exception {
        String forge = System.getProperty("fl.forge");
        if (forge == null || forge.isBlank()) return;
        checkout = Files.createTempDirectory("fakeforge");
        Path packages = Files.createDirectories(checkout.resolve("packages"));
        for (String p : List.of("worker", "recipe", "lang")) Files.createSymbolicLink(packages.resolve(p), Path.of(forge, "packages", p));
        Path ops = Files.createDirectories(packages.resolve("ops"));
        Files.createSymbolicLink(ops.resolve("dist"), Path.of(forge, "packages", "ops", "dist"));
        Path vocab = Files.createDirectories(ops.resolve("vocab"));
        Files.writeString(vocab.resolve("base32.test.json"), """
                [
                  { "name": "wrong text", "op": "base32:encode", "params": {}, "input": { "value": "f", "enc": "utf8" },
                    "expect": { "value": "MZ======", "enc": "utf8" } },
                  { "name": "no error", "op": "base32:encode", "params": {}, "input": { "value": "f", "enc": "utf8" },
                    "expect": { "error": "E_RANGE" } },
                  { "op": "base32:encode", "params": {}, "input": { "value": "fo", "enc": "utf8" },
                    "expect": { "value": "MZXQ====", "enc": "utf8" } }
                ]
                """);
        ProcessOutput out = runVectors("{\"checkout\":" + json(checkout.toString()) + ",\"files\":[\"base32.test.json\"]}");
        assertEquals(out.getStdout() + out.getStderr(), 1, out.getExitCode());
        List<Event> failed = events(out.getStdout()).stream().filter(e -> e.kind().equals("testFailed")).toList();
        assertEquals(List.of("#0 wrong text", "#1 no error", "coverage"), failed.stream().map(Event::name).toList());
        assertEquals("MZ======", failed.get(0).expected());
        assertEquals("MY======", failed.get(0).actual());
        assertEquals("error E_RANGE at \"/steps/0\"", failed.get(1).expected());
        assertTrue(failed.get(1).actual(), failed.get(1).actual().startsWith("a result:"));

        // the platform's parser builds the tree the test runner window shows: file, op, entries; diffs; locations
        FLVectorsRunConfiguration c = configuration();
        c.setCheckout(checkout.toString());
        FLVectorsConsoleProperties props = new FLVectorsConsoleProperties(c, DefaultRunExecutor.getRunExecutorInstance());
        SMTestProxy.SMRootTestProxy root = new SMTestProxy.SMRootTestProxy();
        GeneralToSMTRunnerEventsConvertor processor = new GeneralToSMTRunnerEventsConvertor(getProject(), root, "FL Vectors");
        processor.setLocator(props.getTestLocator());
        OutputToGeneralTestEventsConverter converter = new OutputToGeneralTestEventsConverter("FL Vectors", props);
        converter.setProcessor(processor);
        processor.onStartTesting();
        for (String line : out.getStdout().split("\n")) converter.process(line + "\n", ProcessOutputTypes.STDOUT);
        converter.flushBufferOnProcessTermination(1);
        processor.onFinishTesting();
        SMTestProxy file = root.getChildren().get(0);
        assertEquals("base32", file.getName());
        SMTestProxy op = file.getChildren().get(0);
        assertEquals("base32:encode", op.getName());
        assertEquals(List.of("#0 wrong text", "#1 no error", "#2"), op.getChildren().stream().map(SMTestProxy::getName).toList());
        assertEquals("coverage", file.getChildren().get(1).getName());
        SMTestProxy wrong = op.getChildren().get(0);
        assertTrue(wrong.isDefect());
        assertNotNull(wrong.getDiffViewerProvider());
        assertEquals("MZ======", wrong.getDiffViewerProvider().getLeft());
        assertEquals("MY======", wrong.getDiffViewerProvider().getRight());
        assertTrue(op.getChildren().get(2).isPassed());
        assertEquals("flvector://base32.test.json#0", wrong.getLocationUrl());
        Location<?> where = wrong.getLocation(getProject(), GlobalSearchScope.allScope(getProject()));
        assertNotNull(where);
        assertEquals("base32.test.json", where.getVirtualFile().getName());
        List<String> rerun = new ArrayList<>();
        for (SMTestProxy t : root.getAllTests()) if (t.isLeaf() && t.isDefect()) rerun.add(t.getLocationUrl());
        FLVectorsRerunFailedAction.apply(c, rerun);
        assertEquals("coverage failed: the whole file again", List.of("base32.test.json"), c.getFiles());
        assertEquals(Map.of(), c.getEntries());
    }

    private static String json(String s) {
        JsonObject o = new JsonObject();
        o.addProperty("s", s);
        return o.get("s").toString();
    }
}
