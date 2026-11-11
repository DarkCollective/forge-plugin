// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.editor;

import com.forge.fl.FLTokenTypes;
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;

/** Quote handling for string literals (selection wrapping, closing-quote detection); pairing is {@link FLTypedHandler}. */
public final class FLQuoteHandler extends SimpleTokenSetQuoteHandler {
    public FLQuoteHandler() {
        super(FLTokenTypes.STRING);
    }

    @Override
    public boolean hasNonClosedLiteral(Editor editor, HighlighterIterator iterator, int offset) {
        return false; // an unterminated string is a bad token to the lexer; pairing is done by FLTypedHandler
    }
}
