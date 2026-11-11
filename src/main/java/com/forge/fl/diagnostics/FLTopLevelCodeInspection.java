// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.diagnostics;

/**
 * Executable code (E_TOPLEVEL, docs/LANGUAGE.md section 9) and {@code VOCAB} outside the console. A weak warning:
 * console snippets are legitimately written in files too, and a source unit that is only ever pasted into the
 * console is fine.
 */
public final class FLTopLevelCodeInspection extends FLDiagnosticInspection {
    public FLTopLevelCodeInspection() {
        super(FLDiagnostics.E_TOPLEVEL, FLDiagnostics.E_SYNTAX);
    }
}
