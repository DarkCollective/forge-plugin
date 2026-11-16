// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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
    private final FLUnitFiles files;

    FLRunConsoleFilter(@NotNull Project project, @NotNull FLUnitFiles files) {
        this.project = project;
        this.files = files;
    }

    @Nullable
    @Override
    public Result applyFilter(@NotNull String line, int entireLength) {
        int lineStart = entireLength - line.length();
        List<ResultItem> items = new ArrayList<>();
        Matcher m = LOCATION.matcher(line);
        while (m.find()) {
            VirtualFile file = files.fileOf(m.group(1));
            if (file == null) continue;
            int lineNo = Integer.parseInt(m.group(2)) - 1;
            int col = m.group(3) == null ? 0 : Integer.parseInt(m.group(3)) - 1;
            items.add(new ResultItem(lineStart + m.start(), lineStart + m.end(),
                    new OpenFileHyperlinkInfo(project, file, lineNo, col)));
        }
        return items.isEmpty() ? null : new Result(items);
    }
}
