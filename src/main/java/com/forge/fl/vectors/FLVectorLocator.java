// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.vectors;

import com.forge.fl.index.FLIndexEntry;
import com.forge.fl.index.FLIndexLookup;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.run.FLForgeEnvironment;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The locations the vector runner reports: {@code flvectors://<file>} (a vectors file), {@code flvector://<file>#<i>}
 * (entry i of it) and {@code flop://<vocab:op>} (the op's FL or built-in declaration).
 */
final class FLVectorLocator implements SMTestLocator {
    static final String FILE = "flvectors";
    static final String ENTRY = "flvector";
    static final String OP = "flop";

    private final @Nullable Path checkout;

    FLVectorLocator(@Nullable Path checkout) {
        this.checkout = checkout;
    }

    @NotNull
    @Override
    public List<Location> getLocation(@NotNull String protocol, @NotNull String path, @NotNull Project project,
                                      @NotNull GlobalSearchScope scope) {
        PsiElement target = switch (protocol) {
            case FILE -> file(project, path);
            case ENTRY -> entry(project, path);
            case OP -> op(project, path);
            default -> null;
        };
        return target == null ? List.of() : List.of(PsiLocation.fromPsiElement(target));
    }

    @Nullable
    private PsiFile file(Project project, String name) {
        if (checkout == null) return null;
        VirtualFile vf = LocalFileSystem.getInstance().findFileByNioFile(FLForgeEnvironment.vocabDir(checkout).resolve(name));
        return vf == null ? null : PsiManager.getInstance(project).findFile(vf);
    }

    /** {@code base32.test.json#3}: the first token of entry 3. */
    @Nullable
    private PsiElement entry(Project project, String path) {
        int hash = path.lastIndexOf('#');
        if (hash < 0) return file(project, path);
        PsiFile file = file(project, path.substring(0, hash));
        if (file == null) return null;
        int index;
        try {
            index = Integer.parseInt(path.substring(hash + 1));
        } catch (NumberFormatException e) {
            return file;
        }
        Document doc = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
        if (doc == null) return file;
        List<Integer> offsets = FLVectorFiles.entryOffsets(doc.getCharsSequence());
        if (index < 0 || index >= offsets.size()) return file;
        PsiElement at = file.findElementAt(offsets.get(index));
        return at == null ? file : at;
    }

    /** The declarations of {@code vocab:name} in the index: FL words of the vocabularies, or a built-in word. */
    @Nullable
    private static PsiElement op(Project project, String qname) {
        int colon = qname.indexOf(':');
        if (colon <= 0) return null;
        List<PsiElement> found = new ArrayList<>();
        for (FLIndexLookup.Hit hit : FLIndexLookup.declarations(project, qname.substring(0, colon), qname.substring(colon + 1))) {
            for (FLIndexEntry e : hit.entries()) {
                if (!e.isDeclaration()) continue;
                FLNamedElement el = FLIndexLookup.element(project, hit.file(), e);
                if (el != null) found.add(el);
            }
        }
        return found.isEmpty() ? null : found.get(0);
    }
}
