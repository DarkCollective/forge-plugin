// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.FLLanguage;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;

/** Composite element types of the FL syntax tree (docs/LANGUAGE.md section 5.1). */
public final class FLElementTypes {
    public static final class FLElementType extends IElementType {
        public FLElementType(String debugName) {
            super(debugName, FLLanguage.INSTANCE);
        }
    }

    public static final IFileElementType FILE = new IFileElementType("FL_FILE", FLLanguage.INSTANCE);

    public static final IElementType DEFINITION = new FLElementType("DEFINITION");
    public static final IElementType HEADER = new FLElementType("HEADER");
    public static final IElementType PARAMETER = new FLElementType("PARAMETER");
    /** {@code "text" doc} or {@code "text" category} directly after a header. */
    public static final IElementType DOC_DECL = new FLElementType("DOC_DECL");
    public static final IElementType WORD_REF = new FLElementType("WORD_REF");
    public static final IElementType QUOTATION = new FLElementType("QUOTATION");
    public static final IElementType LIST_LITERAL = new FLElementType("LIST_LITERAL");
    public static final IElementType CONSTANT_DECL = new FLElementType("CONSTANT_DECL");
    public static final IElementType VARIABLE_DECL = new FLElementType("VARIABLE_DECL");
    public static final IElementType PRIMITIVE_DECL = new FLElementType("PRIMITIVE_DECL");
    public static final IElementType IN_FORM = new FLElementType("IN_FORM");
    public static final IElementType USING_FORM = new FLElementType("USING_FORM");
    public static final IElementType VOCAB_FORM = new FLElementType("VOCAB_FORM");
    public static final IElementType META_BLOCK = new FLElementType("META_BLOCK");
    public static final IElementType META_CLAUSE = new FLElementType("META_CLAUSE");
    public static final IElementType ALIAS = new FLElementType("ALIAS");
    public static final IElementType PARAM_REF = new FLElementType("PARAM_REF");

    private FLElementTypes() {
    }
}
