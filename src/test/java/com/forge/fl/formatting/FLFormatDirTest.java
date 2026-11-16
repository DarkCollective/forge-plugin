// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.formatting;

import com.forge.fl.FLTestBase;
import com.forge.fl.intentions.FLMetaBlockMover;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLMetaBlock;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Formats a directory of FL files without an IDE: the plugin's formatter plus the META-placement fix, i.e. Reformat
 * File with Code cleanup.
 *
 * <ul>
 *   <li>{@code -Dfl.format=DIR} rewrites every {@code .fth}/{@code .fl} file of DIR (recursively) in place and prints
 *       {@code FL-FORMAT files=N changed=M}.</li>
 *   <li>{@code -Dfl.formatCheck=DIR} writes nothing, prints the files that would change and fails when any would.</li>
 * </ul>
 *
 * A file with syntax errors is skipped, and a file whose token stream changes (other than by whole META blocks
 * moving) is refused; neither is ever written. Without a property the directory tests do nothing.
 */
public class FLFormatDirTest extends FLTestBase {
    /** A formatted text, or the reason the file was not formatted. */
    private record Result(String text, String skipped, String refused) {
    }

    private static List<Path> filesIn(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".fth") || p.toString().endsWith(".fl")).sorted().toList();
        }
    }

    /** Applies the META-placement fix (until nothing is misplaced), then reformats the file. */
    private String formatText(String name, String text) {
        PsiFile file = myFixture.configureByText(name, text);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            for (int guard = 0; guard < 10000; guard++) {
                PsiFile current = file.getViewProvider().getPsi(file.getLanguage());
                FLMetaBlock misplaced = null;
                FLDefinition target = null;
                for (FLMetaBlock block : PsiTreeUtil.findChildrenOfType(current, FLMetaBlock.class)) {
                    FLDefinition def = FLMetaBlockMover.targetDefinition(block);
                    if (def != null && !FLMetaBlockMover.isImmediatelyAfter(block, def)) {
                        misplaced = block;
                        target = def;
                        break;
                    }
                }
                if (misplaced == null) break;
                FLMetaBlockMover.move(getProject(), current, misplaced, target);
            }
            CodeStyleManager.getInstance(getProject()).reformat(file);
        });
        return file.getText();
    }

    /** Tokens outside META blocks in order, then the sorted token strings of the META blocks. */
    private List<String> tokens(String name, String text) {
        PsiFile file = myFixture.configureByText(name, text);
        List<String> code = new ArrayList<>();
        List<String> metas = new ArrayList<>();
        StringBuilder metaTokens = new StringBuilder();
        for (FLMetaBlock block : PsiTreeUtil.findChildrenOfType(file, FLMetaBlock.class)) {
            metaTokens.setLength(0);
            collect(block, null, metaTokens);
            metas.add(metaTokens.toString());
        }
        StringBuilder out = new StringBuilder();
        collect(file, FLMetaBlock.class, out);
        for (String t : out.toString().split("\u0000")) code.add(t);
        Collections.sort(metas);
        code.add("\u0001META");
        code.addAll(metas);
        return code;
    }

    private static void collect(PsiElement root, Class<? extends PsiElement> skip, StringBuilder out) {
        for (PsiElement leaf = PsiTreeUtil.firstChild(root); leaf != null && PsiTreeUtil.isAncestor(root, leaf, false);
             leaf = PsiTreeUtil.nextLeaf(leaf)) {
            if (skip != null && PsiTreeUtil.getParentOfType(leaf, skip, false) != null) continue;
            if (leaf.getNode().getElementType() == TokenType.WHITE_SPACE) continue;
            if (com.forge.fl.FLTokenTypes.COMMENTS.contains(leaf.getNode().getElementType())) continue;
            out.append(leaf.getText()).append('\u0000');
        }
    }

    private Result process(String name, String text) {
        PsiFile before = myFixture.configureByText(name, text);
        List<String> problems = problems(before);
        if (!problems.isEmpty()) return new Result(null, problems.size() + " syntax problem(s), first: " + problems.get(0), null);
        List<String> tokensBefore = tokens(name, text);
        String formatted = formatText(name, text);
        PsiFile after = myFixture.configureByText(name, formatted);
        if (!problems(after).isEmpty()) return new Result(null, null, "the formatted text has syntax problems");
        if (!tokensBefore.equals(tokens(name, formatted))) return new Result(null, null, "the token stream changed");
        return new Result(formatted, null, null);
    }

    private void run(String dir, boolean write) throws IOException {
        List<Path> files = filesIn(Path.of(dir));
        int changed = 0;
        List<String> skipped = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        for (Path p : files) {
            String text = Files.readString(p);
            Result r = process(p.getFileName().toString(), text);
            if (r.skipped() != null) {
                skipped.add(p + ": " + r.skipped());
            } else if (r.refused() != null) {
                refused.add(p + ": " + r.refused());
            } else if (!r.text().equals(text)) {
                changed++;
                System.out.println((write ? "FL-FORMAT CHANGED " : "FL-FORMAT WOULD CHANGE ") + p);
                if (write) Files.writeString(p, r.text());
                else System.out.println(firstDifference(text, r.text()));
            }
        }
        System.out.println("FL-FORMAT files=" + files.size() + " changed=" + changed);
        for (String s : skipped) System.out.println("FL-FORMAT SKIPPED " + s);
        for (String s : refused) System.out.println("FL-FORMAT REFUSED " + s);
        assertEmpty("refused files", refused);
        if (!write) {
            assertEmpty("skipped files", skipped);
            assertEquals("files that would change", 0, changed);
        }
    }

    /** Lines around the first difference, as a few -/+ lines. */
    private static String firstDifference(String a, String b) {
        String[] x = a.split("\n", -1);
        String[] y = b.split("\n", -1);
        int i = 0;
        while (i < x.length && i < y.length && x[i].equals(y[i])) i++;
        int ex = x.length;
        int ey = y.length;
        while (ex > i && ey > i && x[ex - 1].equals(y[ey - 1])) {
            ex--;
            ey--;
        }
        StringBuilder sb = new StringBuilder("  @@ line " + (i + 1) + "\n");
        for (int k = i; k < Math.min(ex, i + 8); k++) sb.append("  - ").append(x[k]).append('\n');
        for (int k = i; k < Math.min(ey, i + 8); k++) sb.append("  + ").append(y[k]).append('\n');
        return sb.toString().stripTrailing();
    }

    /** Runs only with -Dfl.format=DIR (rewrites the files in place). */
    public void testFormatDirectory() throws IOException {
        String dir = System.getProperty("fl.format");
        if (dir == null || dir.isBlank()) return;
        run(dir, true);
    }

    /** Runs only with -Dfl.formatCheck=DIR (writes nothing, fails when a file would change). */
    public void testCheckDirectory() throws IOException {
        String dir = System.getProperty("fl.formatCheck");
        if (dir == null || dir.isBlank()) return;
        run(dir, false);
    }

    /** Formatting is idempotent: a second pass over the bundled corpus changes nothing. */
    public void testFormattingIsIdempotent() throws IOException {
        List<Path> files = filesIn(Path.of("src/test/testData/corpus"));
        assertTrue("corpus files missing", files.size() >= 4);
        for (Path p : files) {
            String once = formatText(p.getFileName().toString(), Files.readString(p));
            String twice = formatText(p.getFileName().toString(), once);
            assertEquals(p + " changes on a second formatting pass", once, twice);
        }
    }

    /** The guard that formatting never changes code: the bundled corpus keeps its tokens. */
    public void testFormattingKeepsTokens() throws IOException {
        for (Path p : filesIn(Path.of("src/test/testData/corpus"))) {
            Result r = process(p.getFileName().toString(), Files.readString(p));
            assertNull(p + " skipped", r.skipped());
            assertNull(p + " refused", r.refused());
        }
    }
}
