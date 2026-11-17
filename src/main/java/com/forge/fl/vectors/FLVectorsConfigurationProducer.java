// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.vectors;

import com.forge.fl.FLFile;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.run.FLForgeEnvironment;
import com.forge.fl.run.FLRunInputs;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Creates FL Vectors configurations from a context: an entry of a vectors file (that entry), elsewhere in a vectors
 * file (the file), a word of a bundled vocabulary (the vectors of that op), elsewhere in its {@code .fth} file (the
 * vocabulary's file), the vocabulary directory (all files).
 */
public final class FLVectorsConfigurationProducer extends LazyRunConfigurationProducer<FLVectorsRunConfiguration> {
    /** What a context runs. */
    record Selection(Path checkout, List<String> files, String op, Map<String, List<Integer>> entries) {
    }

    @NotNull
    @Override
    public ConfigurationFactory getConfigurationFactory() {
        return FLVectorsConfigurationType.getInstance().getFactory();
    }

    @Override
    protected boolean setupConfigurationFromContext(@NotNull FLVectorsRunConfiguration configuration,
                                                    @NotNull ConfigurationContext context,
                                                    @NotNull Ref<PsiElement> sourceElement) {
        Selection s = selectionOf(context);
        if (s == null) return false;
        configuration.setCheckout(s.checkout().toString());
        configuration.setFiles(s.files());
        configuration.setOp(s.op());
        configuration.setEntries(s.entries());
        configuration.setName(configuration.suggestedName());
        return true;
    }

    @Override
    public boolean isConfigurationFromContext(@NotNull FLVectorsRunConfiguration configuration,
                                              @NotNull ConfigurationContext context) {
        Selection s = selectionOf(context);
        return s != null
                && Objects.equals(configuration.effectiveCheckout(), s.checkout())
                && configuration.getFiles().equals(s.files())
                && configuration.getOp().strip().equals(s.op())
                && configuration.getEntries().equals(s.entries());
    }

    /**
     * What runs from a context: with several elements selected (in the Project view), the files of all of them, or
     * every file when the vocabulary directory is among them; else what runs from the one element.
     */
    @Nullable
    static Selection selectionOf(@NotNull ConfigurationContext context) {
        PsiElement[] selected = context.containsMultipleSelection()
                ? com.intellij.openapi.actionSystem.LangDataKeys.PSI_ELEMENT_ARRAY.getData(context.getDataContext()) : null;
        if (selected == null || selected.length < 2) return selectionAt(context.getPsiLocation());
        return merged(selected);
    }

    /** The union of whole files of several selected elements; null when one of them is not about vectors. */
    @Nullable
    static Selection merged(@NotNull PsiElement[] elements) {
        Path checkout = null;
        java.util.TreeSet<String> files = new java.util.TreeSet<>();
        boolean all = false;
        for (PsiElement e : elements) {
            Selection s = selectionAt(e instanceof PsiDirectory ? e : e.getContainingFile());
            if (s == null) return null;
            if (checkout != null && !checkout.equals(s.checkout())) return null;
            checkout = s.checkout();
            if (s.files().isEmpty()) all = true;
            files.addAll(s.files());
        }
        if (checkout == null) return null;
        return new Selection(checkout, all ? List.of() : List.copyOf(files), "", Map.of());
    }

    /** What runs from {@code element}, or null when it is not in a checkout's vocabulary directory. */
    @Nullable
    static Selection selectionAt(@Nullable PsiElement element) {
        if (element == null) return null;
        if (element instanceof PsiDirectory dir) {
            VirtualFile vf = dir.getVirtualFile();
            if (!FLVectorFiles.isVocabDir(vf)) return null;
            return new Selection(FLVectorFiles.nioPath(vf).getParent().getParent().getParent(), List.of(), "", Map.of());
        }
        PsiFile file = element.getContainingFile();
        VirtualFile vf = file == null ? null : file.getOriginalFile().getVirtualFile();
        if (vf == null) return null;
        Path checkout = FLVectorFiles.checkoutOf(vf);
        if (checkout == null) return null;
        String name = vf.getName();
        if (name.endsWith(FLVectorFiles.SUFFIX)) {
            if (element instanceof PsiFile) return new Selection(checkout, List.of(name), "", Map.of());
            List<Integer> offsets = FLVectorFiles.entryOffsets(file);
            int index = FLVectorFiles.entryAt(offsets, element.getTextRange().getStartOffset());
            if (index < 0) return new Selection(checkout, List.of(name), "", Map.of());
            return new Selection(checkout, List.of(name), "", Map.of(name, List.of(index)));
        }
        if (!(file instanceof FLFile)) return null;
        int dot = name.lastIndexOf('.');
        String vectors = (dot > 0 ? name.substring(0, dot) : name) + FLVectorFiles.SUFFIX;
        VirtualFile vectorsFile = LocalFileSystem.getInstance().findFileByNioFile(FLForgeEnvironment.vocabDir(checkout).resolve(vectors));
        if (vectorsFile == null) return null;
        FLDefinition def = PsiTreeUtil.getParentOfType(element, FLDefinition.class, false);
        if (def != null && def.getName() != null) {
            String op = FLRunInputs.qualifiedName(def);
            if (mentions(vectorsFile, op)) return new Selection(checkout, List.of(vectors), op, Map.of());
        }
        return new Selection(checkout, List.of(vectors), "", Map.of());
    }

    /** Whether the vectors file names the op ({@code "op": "base32:encode"}); a cheap text test. */
    private static boolean mentions(VirtualFile vectorsFile, String op) {
        var doc = FileDocumentManager.getInstance().getDocument(vectorsFile);
        return doc != null && doc.getCharsSequence().toString().contains("\"" + op + "\"");
    }
}
