// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.lexer;

import java.util.List;

final class LexerTestSupport {
    private LexerTestSupport() {
    }

    static FLLexerTest.Tok last(List<FLLexerTest.Tok> toks) {
        return toks.get(toks.size() - 1);
    }
}
