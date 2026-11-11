// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.editor;

import com.forge.fl.FLFile;
import com.forge.fl.FLTokenTypes;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Typed-character pairing. The real FL sources write brackets as standalone tokens with spaces inside
 * ({@code [ key iv _ofb ]}, {@code { 1 2 }}), and {@code [} / {@code {} must be standalone tokens for the lexer,
 * so typing one at a standalone position inserts {@code [  ]} with the caret in the middle, never {@code []}.
 * Typing the closer next to an existing closer moves over it. A quote at a standalone position inserts {@code ""}
 * and typing the closing quote moves over it. Nothing is paired inside comments or strings, or when the
 * character would not be a standalone token.
 */
public final class FLTypedHandler extends TypedHandlerDelegate {
    @NotNull
    @Override
    public Result beforeCharTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file,
                                  @NotNull com.intellij.openapi.fileTypes.FileType fileType) {
        if (!(file instanceof FLFile)) return Result.CONTINUE;
        Document doc = editor.getDocument();
        CharSequence text = doc.getCharsSequence();
        int offset = editor.getCaretModel().getOffset();
        if (editor.getSelectionModel().hasSelection()) return Result.CONTINUE;
        IElementType inside = tokenAt(editor, offset);
        boolean standalone = (offset == 0 || Character.isWhitespace(text.charAt(offset - 1)))
                && (offset >= text.length() || Character.isWhitespace(text.charAt(offset)));
        if (c == '"') {
            if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE) return Result.CONTINUE;
            if (offset < text.length() && text.charAt(offset) == '"' && inside == FLTokenTypes.STRING
                    && !(offset > 0 && text.charAt(offset - 1) == '\\')) {
                editor.getCaretModel().moveToOffset(offset + 1);
                return Result.STOP;
            }
            if (standalone && inside != FLTokenTypes.STRING && !isComment(inside)) {
                doc.insertString(offset, "\"\"");
                editor.getCaretModel().moveToOffset(offset + 1);
                return Result.STOP;
            }
            return Result.CONTINUE;
        }
        if (isComment(inside) || inside == FLTokenTypes.STRING) return Result.CONTINUE;
        if (c == '[' || c == '{') {
            if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET || !standalone) return Result.CONTINUE;
            char close = c == '[' ? ']' : '}';
            doc.insertString(offset, c + "  " + close);
            editor.getCaretModel().moveToOffset(offset + 2);
            return Result.STOP;
        }
        if (c == ']' || c == '}') {
            if (offset < text.length() && text.charAt(offset) == c) {
                editor.getCaretModel().moveToOffset(offset + 1);
                return Result.STOP;
            }
            if (offset + 1 < text.length() && text.charAt(offset) == ' ' && text.charAt(offset + 1) == c) {
                editor.getCaretModel().moveToOffset(offset + 2);
                return Result.STOP;
            }
        }
        return Result.CONTINUE;
    }

    private static boolean isComment(IElementType t) {
        return t != null && FLTokenTypes.COMMENTS.contains(t);
    }

    /** The token that strictly contains the caret (the caret is inside it, not at its start), else null. */
    private static IElementType tokenAt(Editor editor, int offset) {
        HighlighterIterator it = editor.getHighlighter().createIterator(Math.max(0, offset - 1));
        if (it.atEnd()) return null;
        return it.getEnd() > offset || it.getTokenType() == FLTokenTypes.LINE_COMMENT ? it.getTokenType() : null;
    }
}
