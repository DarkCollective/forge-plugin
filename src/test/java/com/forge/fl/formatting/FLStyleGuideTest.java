// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.formatting;

import com.forge.fl.FLTestBase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps docs/FL-STYLE.md and the formatter in agreement: every {@code ```fl} example of the guide parses without
 * errors and is already formatted (reformatting it changes nothing). Change a formatter rule and the guide together.
 */
public class FLStyleGuideTest extends FLTestBase {
    private static final Path GUIDE = Path.of("docs/FL-STYLE.md");
    private static final Pattern FL_BLOCK = Pattern.compile("(?ms)^```fl\\n(.*?)^```$");

    static List<String> examples() throws IOException {
        List<String> out = new ArrayList<>();
        Matcher m = FL_BLOCK.matcher(Files.readString(GUIDE));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    public void testGuideHasExamples() throws IOException {
        assertTrue("too few examples in " + GUIDE, examples().size() >= 8);
    }

    public void testEveryExampleParsesAndIsFormatted() throws IOException {
        int n = 0;
        for (String example : examples()) {
            n++;
            PsiFile file = myFixture.configureByText("example" + n + ".fth", example);
            assertEmpty("example " + n + " has syntax errors:\n" + example, problems(file));
            WriteCommandAction.runWriteCommandAction(getProject(), () -> {
                CodeStyleManager.getInstance(getProject()).reformat(file);
            });
            assertEquals("example " + n + " of " + GUIDE + " is not in the formatter's style", example, file.getText());
        }
    }
}
