// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.vectors;

import com.forge.fl.run.FLForgeEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The op test vector files of a Forge checkout: {@code packages/ops/vocab/<vocab>.test.json}, each a JSON array of
 * entries (vectors and check pairs, RECIPES.md section 9). Entries are found by a small scanner, so that nothing here
 * needs the JSON plugin.
 */
public final class FLVectorFiles {
    public static final String SUFFIX = ".test.json";

    private FLVectorFiles() {
    }

    /** Whether {@code file} is a vectors file: {@code <checkout>/packages/ops/vocab/<vocab>.test.json}. */
    public static boolean isVectorsFile(@Nullable VirtualFile file) {
        return file != null && !file.isDirectory() && file.getName().endsWith(SUFFIX) && checkoutOf(file) != null;
    }

    /** Whether {@code dir} is the vocabulary directory of a checkout. */
    public static boolean isVocabDir(@Nullable VirtualFile dir) {
        Path p = nioPath(dir);
        if (p == null || !dir.isDirectory()) return false;
        Path checkout = p.getParent() == null || p.getParent().getParent() == null ? null : p.getParent().getParent().getParent();
        return checkout != null && FLForgeEnvironment.vocabDir(checkout).equals(p) && FLForgeEnvironment.isCheckout(checkout);
    }

    /** The checkout of a file in its vocabulary directory, or null. */
    @Nullable
    public static Path checkoutOf(@NotNull VirtualFile file) {
        VirtualFile dir = file.getParent();
        if (!isVocabDir(dir)) return null;
        return nioPath(dir).getParent().getParent().getParent();
    }

    /** The path of a file on disk, or null (not a local file, or one that has no path, like the test file system). */
    @Nullable
    static Path nioPath(@Nullable VirtualFile file) {
        if (file == null || !file.isInLocalFileSystem()) return null;
        try {
            return file.getFileSystem().getNioPath(file);
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    /** {@code base32.test.json} -> {@code base32}. */
    @NotNull
    public static String vocabOf(@NotNull String fileName) {
        return fileName.endsWith(SUFFIX) ? fileName.substring(0, fileName.length() - SUFFIX.length()) : fileName;
    }

    /** {@link #entryOffsets(CharSequence)} of a file, cached until it changes. */
    @NotNull
    public static List<Integer> entryOffsets(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () ->
                CachedValueProvider.Result.create(entryOffsets(file.getViewProvider().getContents()), file));
    }

    /** The index of the entry that contains {@code offset} (the last that starts at or before it), or -1. */
    public static int entryAt(@NotNull List<Integer> offsets, int offset) {
        int found = -1;
        for (int i = 0; i < offsets.size() && offsets.get(i) <= offset; i++) found = i;
        return found;
    }

    /** The start offsets of the elements of the top-level JSON array, in order; empty when the text is not an array. */
    @NotNull
    public static List<Integer> entryOffsets(@NotNull CharSequence text) {
        List<Integer> out = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        boolean expectValue = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
                continue;
            }
            if (Character.isWhitespace(c)) continue;
            if (depth == 0) {
                if (c != '[') return out;
                depth = 1;
                expectValue = true;
                continue;
            }
            if (depth == 1) {
                if (c == ',') {
                    expectValue = true;
                    continue;
                }
                if (c == ']') return out;
                if (expectValue) {
                    out.add(i);
                    expectValue = false;
                }
            }
            if (c == '"') inString = true;
            else if (c == '[' || c == '{') depth++;
            else if (c == ']' || c == '}') depth--;
        }
        return out;
    }

    /** The offset of the opening {@code [} of the top-level array, or -1. */
    public static int arrayStart(@NotNull CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') return i;
            if (!Character.isWhitespace(c)) return -1;
        }
        return -1;
    }
}
