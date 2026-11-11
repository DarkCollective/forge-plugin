// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.index;

import org.jetbrains.annotations.Nullable;

/**
 * One fact recorded by {@link FLIndex} for a key in a file: what kind of thing it is, where it starts, and (for
 * declarations) a short detail text such as the stack effect.
 *
 * @param kind   one of the constants of {@link FLIndexEntry.Kind}
 * @param offset start offset of the name token (the declared name or the word use)
 * @param detail stack effect text for words, {@code ( -- int )} style effect for constants and variables, else null
 */
public record FLIndexEntry(byte kind, int offset, @Nullable String detail) {
    /** Entry kinds. USAGE and VOCAB are not declarations. */
    public static final class Kind {
        public static final byte USAGE = 0;
        public static final byte WORD = 1;
        public static final byte CONSTANT = 2;
        public static final byte VARIABLE = 3;
        public static final byte VARIABLE_SETTER = 4;
        public static final byte PRIMITIVE = 5;
        public static final byte ALIAS = 6;
        /** The file declares into, or switches to, a vocabulary ({@code IN:}). */
        public static final byte VOCAB = 7;

        private Kind() {
        }

        public static boolean isDeclaration(byte kind) {
            return kind >= WORD && kind <= ALIAS;
        }
    }

    public boolean isDeclaration() {
        return Kind.isDeclaration(kind);
    }
}
