// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Links {@code unit.fth:line:col} in the runner's output to the file. The runner names a file by its unit name
 * (the file name); it is looked up among the extra files, then in the checkout's vocabulary directory, then by
 * name in the project.
 */
final class FLRunConsoleFilter implements Filter {
    static final Pattern LOCATION = Pattern.compile("([\\w.\\-]+\\.(?:fth|fl|forge)):(\\d+)(?::(\\d+))?");

    private final Project project;
    private final @Nullable Path vocabDir;
    private final Map<String, Path> units = new HashMap<>();

    FLRunConsoleFilter(@NotNull Project project, @Nullable Path checkout, @NotNull List<String> extraFiles) {
        this.project = project;
        this.vocabDir = checkout == null ? null : FLForgeEnvironment.vocabDir(checkout);
        for (String f : extraFiles) {
            Path p = Path.of(f);
            units.put(p.getFileName().toString(), p);
        }
    }

    @Nullable
    @Override
    public Result applyFilter(@NotNull String line, int entireLength) {
        int lineStart = entireLength - line.length();
        List<ResultItem> items = new ArrayList<>();
        Matcher m = LOCATION.matcher(line);
        while (m.find()) {
            VirtualFile file = find(m.group(1));
            if (file == null) continue;
            int lineNo = Integer.parseInt(m.group(2)) - 1;
            int col = m.group(3) == null ? 0 : Integer.parseInt(m.group(3)) - 1;
            items.add(new ResultItem(lineStart + m.start(), lineStart + m.end(),
                    new OpenFileHyperlinkInfo(project, file, lineNo, col)));
        }
        return items.isEmpty() ? null : new Result(items);
    }

    @Nullable
    private VirtualFile find(String name) {
        LocalFileSystem fs = LocalFileSystem.getInstance();
        Path unit = units.get(name);
        if (unit != null) {
            VirtualFile f = fs.findFileByNioFile(unit);
            if (f != null) return f;
        }
        if (vocabDir != null) {
            VirtualFile f = fs.findFileByNioFile(vocabDir.resolve(name));
            if (f != null) return f;
        }
        try {
            return ApplicationManager.getApplication().runReadAction((Computable<VirtualFile>) () -> {
                Collection<VirtualFile> found = FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project));
                return found.isEmpty() ? null : found.iterator().next();
            });
        } catch (IndexNotReadyException e) {
            return null;
        }
    }
}
