package com.forge.fl.lexer;

import com.forge.fl.FLLexer;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FLLexerAdapter extends LexerBase {
    private final FLLexer delegate = new FLLexer();

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        delegate.start(buffer, startOffset, endOffset, initialState);
    }

    @Override
    public int getState() {
        return delegate.getState();
    }

    @Nullable
    @Override
    public IElementType getTokenType() {
        return delegate.getTokenType();
    }

    @Override
    public int getTokenStart() {
        return delegate.getTokenStart();
    }

    @Override
    public int getTokenEnd() {
        return delegate.getTokenEnd();
    }

    @Override
    public void advance() {
        delegate.advance();
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
        return delegate.getBufferSequence();
    }

    @Override
    public int getBufferEnd() {
        return delegate.getBufferEnd();
    }
}
