package com.forge.fl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.TokenType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class FLTestBase extends BasePlatformTestCase {
    protected PsiFile parse(String text) {
        return myFixture.configureByText("test.fth", text);
    }

    /** Descriptions of every PsiErrorElement and BAD_CHARACTER leaf in the tree. */
    protected static List<String> problems(PsiFile file) {
        List<String> out = new ArrayList<>();
        file.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiErrorElement e) {
                    out.add("error '" + e.getErrorDescription() + "' at " + e.getTextOffset());
                } else if (element.getNode().getElementType() == TokenType.BAD_CHARACTER) {
                    out.add("bad token '" + element.getText() + "' at " + element.getTextOffset());
                }
                super.visitElement(element);
            }
        });
        return out;
    }

    protected void assertClean(String text) {
        PsiFile f = parse(text);
        assertEquals(text, f.getText());
        assertEmpty(problems(f));
    }
}
