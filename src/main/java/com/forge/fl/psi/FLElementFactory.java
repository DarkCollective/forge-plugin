package com.forge.fl.psi;

import com.forge.fl.FLFileType;
import com.forge.fl.FLTokenTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;

/** Creates name leaves by parsing a dummy FL file. */
public final class FLElementFactory {
    private FLElementFactory() {
    }

    public static PsiFile createFile(Project project, String text) {
        return PsiFileFactory.getInstance(project).createFileFromText("dummy.fl", FLFileType.INSTANCE, text);
    }

    /** The leaf at {@code prefix.length()} in {@code prefix + name + suffix}, which must be exactly the name. */
    private static PsiElement leaf(Project project, String prefix, String name, String suffix, IElementType... types) {
        PsiFile file = createFile(project, prefix + name + suffix);
        PsiElement leaf = file.findElementAt(prefix.length());
        if (leaf != null && leaf.getText().equals(name)) {
            IElementType actual = leaf.getNode().getElementType();
            for (IElementType t : types) {
                if (t == actual) return leaf;
            }
        }
        throw new IncorrectOperationException("Not a valid FL name: " + name);
    }

    public static PsiElement createDefinitionName(Project project, String name) {
        return leaf(project, ": ", name, " ( -- ) ;", FLTokenTypes.WORD_NAME);
    }

    public static PsiElement createConstantName(Project project, String name) {
        return leaf(project, "0 CONSTANT ", name, "\n", FLTokenTypes.WORD_NAME);
    }

    public static PsiElement createVariableName(Project project, String name) {
        return leaf(project, "0 VARIABLE ", name, "\n", FLTokenTypes.WORD_NAME);
    }

    public static PsiElement createPrimitiveName(Project project, String name) {
        return leaf(project, "PRIMITIVE ", name, "\n", FLTokenTypes.WORD_NAME);
    }

    public static PsiElement createAliasName(Project project, String name) {
        return leaf(project, "META: x alias ", name, " ; ;", FLTokenTypes.WORD_NAME);
    }

    public static PsiElement createParameterName(Project project, String name) {
        return leaf(project, ": x ( ", name, ":int -- ) ;", FLTokenTypes.HDR_PARAM_NAME);
    }

    /** The name leaf of a word reference ({@code name} or {@code vocab:name}). */
    public static PsiElement createWordRefLeaf(Project project, String text) {
        return leaf(project, ": x ( -- ) ", text, " ;", FLTokenTypes.WORD_NAME, FLTokenTypes.QUALIFIED_NAME);
    }
}
