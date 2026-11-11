package com.forge.fl.parser;

import com.forge.fl.FLTestBase;
import com.intellij.psi.PsiFile;

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
}
