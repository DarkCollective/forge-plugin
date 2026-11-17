// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.refactoring;

import com.forge.fl.FLTestBase;
import com.forge.fl.effects.FLStackAnalysis;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.run.FLForgeEnvironment;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The refactorings keep behaviour, checked by Forge itself: ./gradlew test -Dfl.forge=/path/to/forge (built).
 * In a few vocabularies every private helper that can be is inlined (and removed), then every word body that can be
 * is extracted into a new helper; the results go into a scratch copy of the checkout, whose op test vectors for those
 * vocabularies must all still pass.
 */
public class FLRefactoringSemanticsTest extends FLTestBase {
    private static final List<String> TARGETS = List.of("base32", "colour", "uri", "morse");

    public void testInlineAndExtractKeepTheVectorsPassing() throws Exception {
        String forge = System.getProperty("fl.forge");
        if (forge == null || forge.isBlank()) return;
        Path vocab = Path.of(forge, "packages", "ops", "vocab");
        Map<String, PsiFile> files = new LinkedHashMap<>();
        try (Stream<Path> s = Files.list(vocab)) {
            for (Path p : s.filter(p -> p.toString().endsWith(".fth")).sorted().toList()) {
                files.put(p.getFileName().toString(), myFixture.addFileToProject(p.getFileName().toString(), Files.readString(p)));
            }
        }
        int inlined = 0;
        int extracted = 0;
        for (String t : TARGETS) {
            PsiFile f = files.get(t + ".fth");
            inlined += inlineHelpers(f);
            extracted += extractBodies(f);
            assertEquals("stack effect problems after refactoring " + t, List.of(), effectProblems(f));
        }
        System.out.println("FL-REFACTOR-SEMANTICS inlined=" + inlined + " extracted=" + extracted);
        assertTrue(inlined > 10);
        assertTrue(extracted > 10);

        Path copy = scratchCheckout(Path.of(forge));
        try {
            for (String t : TARGETS) Files.writeString(FLForgeEnvironment.vocabDir(copy).resolve(t + ".fth"), files.get(t + ".fth").getText());
            StringBuilder request = new StringBuilder("{\"checkout\":\"").append(copy).append("\",\"files\":[");
            for (int i = 0; i < TARGETS.size(); i++) request.append(i > 0 ? "," : "").append('"').append(TARGETS.get(i)).append(".test.json\"");
            request.append("]}");
            CapturingProcessHandler h = new CapturingProcessHandler(new GeneralCommandLine(FLForgeEnvironment.findNode(),
                    FLForgeEnvironment.vectorsScript().toString()).withCharset(StandardCharsets.UTF_8));
            try (OutputStream in = h.getProcess().getOutputStream()) {
                in.write(request.toString().getBytes(StandardCharsets.UTF_8));
            }
            ProcessOutput out = h.runProcess(300_000);
            long finished = out.getStdout().lines().filter(l -> l.startsWith("##teamcity[testFinished")).count();
            List<String> failed = out.getStdout().lines().filter(l -> l.startsWith("##teamcity[testFailed")).toList();
            System.out.println("FL-REFACTOR-SEMANTICS vectors=" + finished + " failed=" + failed.size());
            assertEquals(String.join("\n", failed) + out.getStderr(), 0, out.getExitCode());
            assertTrue(finished > 100);
        } finally {
            FileUtil.delete(copy.toFile());
        }
    }

    /** Inlines private helpers, one word at a time (the PSI changes after each), until none is left to inline. */
    private int inlineHelpers(PsiFile f) {
        int n = 0;
        for (int round = 0; round < 500; round++) {
            boolean changed = false;
            for (FLDefinition def : PsiTreeUtil.findChildrenOfType(f, FLDefinition.class)) {
                if (def.getName() == null || !def.getName().startsWith("_")) continue;
                FLInlineWord.Plan plan;
                try {
                    plan = FLInlineWord.plan(def);
                } catch (FLSelection.Refused e) {
                    continue;
                }
                if (plan.inlinable().isEmpty()) continue;
                n += plan.inlinable().size();
                FLInlineWord.perform(plan, plan.inlinable(), true);
                changed = true;
                break;
            }
            if (!changed) break;
        }
        return n;
    }

    /** Extracts each word's whole body into a new private helper (the helpers made here excepted). */
    private int extractBodies(PsiFile f) {
        int n = 0;
        List<String> names = new ArrayList<>();
        for (FLDefinition def : PsiTreeUtil.findChildrenOfType(f, FLDefinition.class)) {
            if (def.getName() != null && !def.getName().startsWith("_x-")) names.add(def.getName());
        }
        for (String name : names) {
            FLDefinition def = PsiTreeUtil.findChildrenOfType(f, FLDefinition.class).stream()
                    .filter(d -> name.equals(d.getName())).findFirst().orElse(null);
            if (def == null) continue;
            List<PsiElement> body = FLStackAnalysis.bodyItems(def);
            if (body.size() < 2) continue;
            try {
                FLExtractWord.Plan plan = FLExtractWord.plan(f, body.get(0).getTextRange().getStartOffset(),
                        body.get(body.size() - 1).getTextRange().getEndOffset());
                String helper = "_x-" + name.replaceAll("[^a-z0-9-]", "");
                if (FLExtractWord.nameProblem(plan, helper) != null) continue;
                FLExtractWord.perform(plan, helper);
                n++;
            } catch (FLSelection.Refused ignored) {
                // a body that cannot be extracted stays
            }
        }
        return n;
    }

    private static List<String> effectProblems(PsiFile f) {
        List<String> out = new ArrayList<>();
        for (FLDefinition def : PsiTreeUtil.findChildrenOfType(f, FLDefinition.class)) {
            for (FLStackAnalysis.Problem p : FLStackAnalysis.of(def).problems()) out.add(def.getName() + ": " + p.message());
        }
        return out;
    }

    /** A copy of the checkout whose ops package loads the vocabularies of the copy (built code copied, the rest linked). */
    private static Path scratchCheckout(Path forge) throws IOException {
        Path copy = Files.createTempDirectory("forge-refactor");
        Path packages = Files.createDirectories(copy.resolve("packages"));
        Files.createSymbolicLink(copy.resolve("node_modules"), forge.resolve("node_modules"));
        for (String p : List.of("lang", "recipe", "worker")) Files.createSymbolicLink(packages.resolve(p), forge.resolve("packages").resolve(p));
        Path ops = Files.createDirectories(packages.resolve("ops"));
        Path realOps = forge.resolve("packages").resolve("ops");
        Files.copy(realOps.resolve("package.json"), ops.resolve("package.json"));
        Files.createSymbolicLink(ops.resolve("node_modules"), realOps.resolve("node_modules"));
        FileUtil.copyDir(realOps.resolve("dist").toFile(), ops.resolve("dist").toFile());
        FileUtil.copyDir(realOps.resolve("vocab").toFile(), ops.resolve("vocab").toFile());
        return copy;
    }
}
