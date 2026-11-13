// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/** Token types produced by {@link com.forge.fl.lexer.FLLexer}. */
public final class FLTokenTypes {
    public static final class FLTokenType extends IElementType {
        public FLTokenType(String debugName) {
            super(debugName, FLLanguage.INSTANCE);
        }
    }

    public static final IElementType LINE_COMMENT = new FLTokenType("LINE_COMMENT");
    public static final IElementType BLOCK_COMMENT = new FLTokenType("BLOCK_COMMENT");
    public static final IElementType STRING = new FLTokenType("STRING");
    public static final IElementType CHAR = new FLTokenType("CHAR");
    public static final IElementType BYTES = new FLTokenType("BYTES");
    public static final IElementType NUMBER = new FLTokenType("NUMBER");
    public static final IElementType BOOL = new FLTokenType("BOOL");

    public static final IElementType COLON = new FLTokenType("COLON");
    public static final IElementType SEMI = new FLTokenType("SEMI");
    public static final IElementType IN_KW = new FLTokenType("IN_KW");
    public static final IElementType USING_KW = new FLTokenType("USING_KW");
    public static final IElementType META_KW = new FLTokenType("META_KW");
    public static final IElementType VOCAB_KW = new FLTokenType("VOCAB_KW");
    public static final IElementType CONSTANT_KW = new FLTokenType("CONSTANT_KW");
    public static final IElementType VARIABLE_KW = new FLTokenType("VARIABLE_KW");
    public static final IElementType PRIMITIVE_KW = new FLTokenType("PRIMITIVE_KW");
    public static final IElementType RECURSE_KW = new FLTokenType("RECURSE_KW");
    /** {@code MEMO:} (FL v0.2, docs/LANGUAGE.md 5.13): a defining form with the shape of {@code :}. */
    public static final IElementType MEMO_KW = new FLTokenType("MEMO_KW");
    /** {@code CHOICE:} (FL v0.2, 5.12): a shared choice type block. */
    public static final IElementType CHOICE_KW = new FLTokenType("CHOICE_KW");
    /** {@code :>} (FL v0.2, 7.1): binds the top of the stack to a new local. */
    public static final IElementType BIND_KW = new FLTokenType("BIND_KW");

    public static final IElementType LBRACKET = new FLTokenType("LBRACKET");
    public static final IElementType RBRACKET = new FLTokenType("RBRACKET");
    public static final IElementType LBRACE = new FLTokenType("LBRACE");
    public static final IElementType RBRACE = new FLTokenType("RBRACE");

    public static final IElementType QUALIFIED_NAME = new FLTokenType("QUALIFIED_NAME");
    public static final IElementType WORD_NAME = new FLTokenType("WORD_NAME");

    // Stack-effect header sub-tokens (spec 2.8, 5.4).
    public static final IElementType HDR_LPAREN = new FLTokenType("HDR_LPAREN");
    public static final IElementType HDR_RPAREN = new FLTokenType("HDR_RPAREN");
    /** The name of a parameter: the pname before ':' or a bare label, in the input part. */
    public static final IElementType HDR_PARAM_NAME = new FLTokenType("HDR_PARAM_NAME");
    /** The rest of a parameter item, starting at ':' (type, constraint, default). */
    public static final IElementType HDR_PARAM_REST = new FLTokenType("HDR_PARAM_REST");
    public static final IElementType HDR_TYPE = new FLTokenType("HDR_TYPE");
    public static final IElementType HDR_ARROW = new FLTokenType("HDR_ARROW");
    public static final IElementType HDR_DOTS = new FLTokenType("HDR_DOTS");
    public static final IElementType HDR_OUTPUT = new FLTokenType("HDR_OUTPUT");
    /** The type name inside {@code name:choice<...>} (FL v0.2, 5.4), optionally {@code vocab:name}. */
    public static final IElementType HDR_CHOICE_REF = new FLTokenType("HDR_CHOICE_REF");
    /** A header item that fits no other category. */
    public static final IElementType HDR_OTHER = new FLTokenType("HDR_OTHER");

    public static final IElementType BAD = TokenType.BAD_CHARACTER;

    public static final TokenSet COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT);
    public static final TokenSet STRINGS = TokenSet.create(STRING, CHAR);
    public static final TokenSet LITERALS = TokenSet.create(STRING, CHAR, BYTES, NUMBER, BOOL);
    public static final TokenSet KEYWORDS = TokenSet.create(COLON, SEMI, IN_KW, USING_KW, META_KW, VOCAB_KW,
            CONSTANT_KW, VARIABLE_KW, PRIMITIVE_KW, RECURSE_KW, MEMO_KW, CHOICE_KW, BIND_KW);
    /** Keywords after which a name and then a standalone {@code (} make a header (spec 2.3 rule 2, 2.8). */
    public static final TokenSet DEFINERS = TokenSet.create(COLON, MEMO_KW);
    /** Tokens that start a top-level form; they end an unterminated definition, META: or CHOICE: block. */
    public static final TokenSet FORM_STARTS = TokenSet.create(COLON, MEMO_KW, CHOICE_KW, IN_KW, USING_KW, META_KW,
            VOCAB_KW, PRIMITIVE_KW);
    /** Tokens that look like a name to the header lookbehind (a "word" or "qword" in the reader). */
    public static final TokenSet NAME_LIKE = TokenSet.orSet(KEYWORDS, TokenSet.create(WORD_NAME, QUALIFIED_NAME));
    public static final TokenSet IDENTIFIERS = TokenSet.create(WORD_NAME, QUALIFIED_NAME, HDR_PARAM_NAME, HDR_CHOICE_REF);

    private FLTokenTypes() {
    }
}
