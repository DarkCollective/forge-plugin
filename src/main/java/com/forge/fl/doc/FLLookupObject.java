// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.doc;

import com.forge.fl.index.FLIndexEntry;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * The lookup object of a completion item for a word found through the index: where its declaration is, without
 * loading any PSI. The documentation provider turns it into the declaration lazily, when the popup asks for it.
 */
public record FLLookupObject(String vocab, String name, VirtualFile file, FLIndexEntry entry) {
}
