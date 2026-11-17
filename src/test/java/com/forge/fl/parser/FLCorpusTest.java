// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.parser;

import com.forge.fl.FLTestBase;
import com.forge.fl.psi.FLWordRef;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Real FL sources are compiled by the real toolchain, so any problem here is a plugin bug. */
public class FLCorpusTest extends FLTestBase {
    private List<String> parseAll(List<Path> files) throws IOException {
        List<String> failures = new ArrayList<>();
        for (Path p : files) {
            String text = Files.readString(p);
            PsiFile f = myFixture.configureByText(p.getFileName().toString(), text);
            if (!f.getText().equals(text)) failures.add(p + ": tree does not reproduce the text");
            List<String> problems = problems(f);
            if (!problems.isEmpty()) {
                failures.add(p + ": " + problems.size() + " problem(s), first: " + problems.get(0));
            }
        }
        return failures;
    }

    private static List<Path> filesIn(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".fth") || p.toString().endsWith(".fl")).sorted().toList();
        }
    }

    public void testBundledCorpus() throws IOException {
        List<Path> files = filesIn(Path.of("src/test/testData/corpus"));
        assertTrue("corpus files missing", files.size() >= 4);
        assertEmpty(parseAll(files));
    }

    /**
     * Stack effect inference over a real corpus (-Dfl.corpus): working code, so every problem is a false positive or a
     * real bug (in a path no test reaches). Prints FL-EFFECTS words=.. analysed=.. complete=.. problems=.. and each
     * problem; like FL-META-PLACEMENT, the list never fails the run.
     */
    public void testExternalCorpusStackEffects() throws IOException {
        String dir = System.getProperty("fl.corpus");
        if (dir == null || dir.isBlank()) return;
        List<Path> files = filesIn(Path.of(dir));
        List<PsiFile> psi = new java.util.ArrayList<>();
        for (Path p : files) psi.add(myFixture.addFileToProject(p.getFileName().toString(), Files.readString(p)));
        int words = 0;
        int analysed = 0;
        int complete = 0;
        List<String> problems = new java.util.ArrayList<>();
        for (PsiFile f : psi) {
            com.intellij.openapi.editor.Document doc = com.intellij.psi.PsiDocumentManager.getInstance(getProject()).getDocument(f);
            for (com.forge.fl.psi.FLDefinition def : PsiTreeUtil.findChildrenOfType(f, com.forge.fl.psi.FLDefinition.class)) {
                words++;
                com.forge.fl.effects.FLStackAnalysis a = com.forge.fl.effects.FLStackAnalysis.of(def);
                if (a.effect() != null) analysed++;
                java.util.List<PsiElement> body = com.forge.fl.effects.FLStackAnalysis.bodyItems(def);
                if (a.effect() != null && (body.isEmpty() || a.depthAfter(body.get(body.size() - 1)) != null)) complete++;
                for (com.forge.fl.effects.FLStackAnalysis.Problem pr : a.problems()) {
                    int line = doc == null ? 0 : doc.getLineNumber(pr.at().getTextRange().getStartOffset()) + 1;
                    problems.add(f.getName() + ":" + line + " " + def.getName() + ": " + pr.message());
                }
            }
        }
        System.out.println("FL-EFFECTS words=" + words + " analysed=" + analysed + " complete=" + complete + " problems=" + problems.size());
        for (String pr : problems) System.out.println("FL-EFFECTS PROBLEM " + pr);
    }

    /** Runs only with -Dfl.corpus=/path/to/vocab (see build.gradle.kts). */
    public void testExternalCorpus() throws IOException {
        String dir = System.getProperty("fl.corpus");
        if (dir == null || dir.isBlank()) return;
        List<Path> files = filesIn(Path.of(dir));
        List<String> failures = parseAll(files);
        System.out.println("FL-CORPUS parsed " + files.size() + " files, " + failures.size() + " failure(s)");
        for (String f : failures) System.out.println("FL-CORPUS FAIL " + f);
        assertEmpty(failures);
    }

    /**
     * Loads every file of the directory into one project and counts the word references that do not resolve
     * (neither to one declaration nor to several candidates). These sources compile with the real toolchain, so
     * every unresolved or ambiguous reference is a plugin or built-in catalogue bug.
     */
    public void testExternalCorpusResolves() throws IOException {
        String dir = System.getProperty("fl.corpus");
        if (dir == null || dir.isBlank()) return;
        List<Path> files = filesIn(Path.of(dir));
        List<PsiFile> psi = new ArrayList<>();
        for (Path p : files) {
            psi.add(myFixture.addFileToProject(p.getFileName().toString(), Files.readString(p)));
        }
        int refs = 0;
        int unresolved = 0;
        int ambiguous = 0;
        java.util.Map<String, String> firstFileOfUnresolved = new java.util.TreeMap<>();
        java.util.Map<String, String> firstFileOfAmbiguous = new java.util.TreeMap<>();
        for (PsiFile f : psi) {
            for (FLWordRef ref : PsiTreeUtil.findChildrenOfType(f, FLWordRef.class)) {
                refs++;
                ResolveResult[] results = ref.getReference().multiResolve(false);
                if (results.length == 0) {
                    unresolved++;
                    firstFileOfUnresolved.putIfAbsent(ref.getText(), f.getName());
                } else if (results.length > 1) {
                    ambiguous++;
                    firstFileOfAmbiguous.putIfAbsent(ref.getText(), f.getName());
                }
            }
        }
        System.out.println("FL-RESOLVE files=" + files.size() + " refs=" + refs + " unresolved=" + unresolved
                + " ambiguous=" + ambiguous);
        int shown = 0;
        for (java.util.Map.Entry<String, String> e : firstFileOfUnresolved.entrySet()) {
            if (shown++ == 50) break;
            System.out.println("FL-RESOLVE UNRESOLVED " + e.getKey() + " (" + e.getValue() + ")");
        }
        shown = 0;
        for (java.util.Map.Entry<String, String> e : firstFileOfAmbiguous.entrySet()) {
            if (shown++ == 50) break;
            System.out.println("FL-RESOLVE AMBIGUOUS " + e.getKey() + " (" + e.getValue() + ")");
        }
        assertEquals("unresolved references", 0, unresolved);
        assertEquals("ambiguous references", 0, ambiguous);
    }

    /**
     * Runs the diagnostics over every file of the directory, all loaded into one project. These sources compile
     * with the real toolchain, so every error or warning is a plugin bug. Weak warnings (W_SHADOW and the like) are
     * counted and sampled but do not fail the run.
     */
    public void testExternalCorpusDiagnostics() throws IOException {
        String dir = System.getProperty("fl.corpus");
        if (dir == null || dir.isBlank()) return;
        List<Path> files = filesIn(Path.of(dir));
        List<PsiFile> psi = new ArrayList<>();
        for (Path p : files) {
            psi.add(myFixture.addFileToProject(p.getFileName().toString(), Files.readString(p)));
        }
        int errors = 0;
        int warnings = 0;
        int weak = 0;
        List<String> samples = new ArrayList<>();
        List<String> weakSamples = new ArrayList<>();
        java.util.Map<String, Integer> byCode = new java.util.TreeMap<>();
        for (PsiFile f : psi) {
            String text = f.getText();
            for (com.forge.fl.diagnostics.FLDiagnostics.Problem p : com.forge.fl.diagnostics.FLDiagnostics.compute(f)) {
                int line = com.intellij.openapi.util.text.StringUtil.offsetToLineNumber(text, p.range().getStartOffset()) + 1;
                String where = f.getName() + ":" + line + " " + p.severity() + " " + p.message();
                byCode.merge(p.severity() + " " + p.code(), 1, Integer::sum);
                switch (p.severity()) {
                    case ERROR -> {
                        errors++;
                        samples.add(where);
                    }
                    case WARNING -> {
                        warnings++;
                        samples.add(where);
                    }
                    default -> {
                        weak++;
                        weakSamples.add(where);
                    }
                }
            }
        }
        System.out.println("FL-DIAG files=" + files.size() + " errors=" + errors + " warnings=" + warnings
                + " weak=" + weak);
        byCode.forEach((k, v) -> System.out.println("FL-DIAG COUNT " + k + " " + v));
        int shown = 0;
        for (String s : samples) {
            if (shown++ == 50) break;
            System.out.println("FL-DIAG SAMPLE " + s);
        }
        shown = 0;
        for (String s : weakSamples) {
            if (shown++ == 8) break;
            System.out.println("FL-DIAG WEAK SAMPLE " + s);
        }
        assertEquals("errors", 0, errors);
        assertEquals("warnings", 0, warnings);
    }

    /**
     * Lists the META: blocks that are not immediately after their definition (FL-STYLE.md section 7). That is a style
     * rule, not a language rule (a META: block may follow its definition at any distance), so this prints and never fails.
     */
    public void testExternalCorpusMetaPlacement() throws IOException {
        String dir = System.getProperty("fl.corpus");
        if (dir == null || dir.isBlank()) return;
        List<Path> files = filesIn(Path.of(dir));
        List<PsiFile> psi = new ArrayList<>();
        for (Path p : files) psi.add(myFixture.addFileToProject(p.getFileName().toString(), Files.readString(p)));
        List<String> misplaced = new ArrayList<>();
        for (PsiFile f : psi) {
            for (com.forge.fl.psi.FLMetaBlock block : PsiTreeUtil.findChildrenOfType(f, com.forge.fl.psi.FLMetaBlock.class)) {
                com.forge.fl.psi.FLDefinition def = com.forge.fl.intentions.FLMetaBlockMover.targetDefinition(block);
                if (def != null && !com.forge.fl.intentions.FLMetaBlockMover.isImmediatelyAfter(block, def)) {
                    int line = com.intellij.openapi.util.text.StringUtil.offsetToLineNumber(f.getText(), block.getTextOffset()) + 1;
                    misplaced.add(f.getName() + ":" + line + " META: " + def.getName());
                }
            }
        }
        System.out.println("FL-META-PLACEMENT files=" + files.size() + " misplaced=" + misplaced.size());
        for (String m : misplaced) System.out.println("FL-META-PLACEMENT " + m);
    }
}
