// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The files of one run and their unit names. The engine names a source unit by its file name: the bundled
 * vocabularies are the files of {@code <checkout>/packages/ops/vocab}, and every extra file is a user unit.
 */
public final class FLUnitFiles {
    private final Project project;
    private final @Nullable Path vocabDir;
    private final Map<String, Path> extra = new HashMap<>();

    public FLUnitFiles(@NotNull Project project, @Nullable Path checkout, @NotNull List<String> extraFiles) {
        this.project = project;
        this.vocabDir = checkout == null ? null : FLForgeEnvironment.vocabDir(checkout).toAbsolutePath().normalize();
        for (String f : extraFiles) {
            Path p = Path.of(f).toAbsolutePath().normalize();
            extra.put(p.getFileName().toString(), p);
        }
    }

    /**
     * The unit name under which the run loads this file, or null when the run does not load it: a file of the
     * checkout's vocabulary directory or one of the extra files.
     */
    @Nullable
    public String unitOf(@NotNull VirtualFile file) {
        if (!file.isInLocalFileSystem()) return null;
        Path p = file.toNioPath().toAbsolutePath().normalize();
        if (vocabDir != null && vocabDir.equals(p.getParent())) return p.getFileName().toString();
        Path e = extra.get(p.getFileName().toString());
        return p.equals(e) ? p.getFileName().toString() : null;
    }

    /** The file of a unit name: an extra file, then the checkout's vocabulary directory, then by name in the project. */
    @Nullable
    public VirtualFile fileOf(@NotNull String unit) {
        LocalFileSystem fs = LocalFileSystem.getInstance();
        Path p = extra.get(unit);
        if (p != null) {
            VirtualFile f = fs.findFileByNioFile(p);
            if (f != null) return f;
        }
        if (vocabDir != null) {
            VirtualFile f = fs.findFileByNioFile(vocabDir.resolve(unit));
            if (f != null) return f;
        }
        try {
            return ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>) () -> {
                Collection<VirtualFile> found = FilenameIndex.getVirtualFilesByName(unit, GlobalSearchScope.projectScope(project));
                return found.isEmpty() ? null : found.iterator().next();
            });
        } catch (IndexNotReadyException e) {
            return null;
        }
    }
}
