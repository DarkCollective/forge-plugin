// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.editor;

import com.forge.fl.FLFile;
import com.forge.fl.FLTokenTypes;
import com.forge.fl.lexer.FLLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.search.IndexPatternBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** TODO indexing in {@code \} line comments and {@code ( )} block comments. */
public final class FLIndexPatternBuilder implements IndexPatternBuilder {
    @Nullable
    @Override
    public Lexer getIndexingLexer(@NotNull PsiFile file) {
        return file instanceof FLFile ? new FLLexer() : null;
    }

    @Nullable
    @Override
    public TokenSet getCommentTokenSet(@NotNull PsiFile file) {
        return file instanceof FLFile ? FLTokenTypes.COMMENTS : null;
    }

    @Override
    public int getCommentStartDelta(IElementType tokenType) {
        return 1; // the backslash or the opening parenthesis
    }

    @Override
    public int getCommentEndDelta(IElementType tokenType) {
        return tokenType == FLTokenTypes.BLOCK_COMMENT ? 1 : 0;
    }
}
