package com.forge.fl.lexer;

import java.util.List;

final class LexerTestSupport {
    private LexerTestSupport() {
    }

    static FLLexerTest.Tok last(List<FLLexerTest.Tok> toks) {
        return toks.get(toks.size() - 1);
    }
}
