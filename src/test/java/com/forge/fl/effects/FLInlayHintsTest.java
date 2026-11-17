// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.effects;

import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase;

import java.util.Map;

/** The two declarative inlay providers, rendered by the platform's test harness. */
public class FLInlayHintsTest extends DeclarativeInlayHintsProviderTestCase {
    @Override
    protected void setUp() throws Exception {
        com.forge.fl.builtins.FLBuiltins.prepare();
        super.setUp();
        com.forge.fl.builtins.FLBuiltins.prepare();
    }

    public void testParameterNames() {
        doTestProvider("t.fth", """
                IN: t
                : enc ( bytes alphabet:choice{standard|url}=standard padding:bool=true -- str ) drop "x" ;
                : g ( -- str ) "data" /*<# alphabet: #>*/"url" /*<# padding: #>*/false enc ;
                """, new FLParameterNameHints(), Map.of(), true);
    }

    public void testStackDepth() {
        doTestProvider("t.fth", """
                IN: t
                : f ( a:int -- int )
                  a 1 + /*<# → 1 #>*/
                  dup 2 * + /*<# → 1 #>*/
                  ;
                """, new FLStackDepthHints(), Map.of(), true);
    }
}
