// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.diagnostics;

/** W_SHADOW (docs/LANGUAGE.md section 7): a parameter that hides a word that would otherwise be visible. */
public final class FLShadowInspection extends FLDiagnosticInspection {
    public FLShadowInspection() {
        super(FLDiagnostics.W_SHADOW);
    }
}
