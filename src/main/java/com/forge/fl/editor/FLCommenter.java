// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.editor;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

/** Line comments are a standalone backslash. Block comments are not offered: {@code ( ... )} has no nesting. */
public class FLCommenter implements Commenter {
    @Override
    public String getLineCommentPrefix() {
        return "\\ ";
    }

    @Nullable
    @Override
    public String getBlockCommentPrefix() {
        return null;
    }

    @Nullable
    @Override
    public String getBlockCommentSuffix() {
        return null;
    }

    @Nullable
    @Override
    public String getCommentedBlockCommentPrefix() {
        return null;
    }

    @Nullable
    @Override
    public String getCommentedBlockCommentSuffix() {
        return null;
    }
}
