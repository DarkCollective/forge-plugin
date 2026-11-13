// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.editor;

import com.forge.fl.FLIcons;
import com.forge.fl.highlighting.FLSyntaxHighlighter;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Map;

public class FLColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("Comments//Line comment", FLSyntaxHighlighter.LINE_COMMENT),
            new AttributesDescriptor("Comments//Block comment", FLSyntaxHighlighter.BLOCK_COMMENT),
            new AttributesDescriptor("Literals//String", FLSyntaxHighlighter.STRING),
            new AttributesDescriptor("Literals//Char", FLSyntaxHighlighter.CHAR),
            new AttributesDescriptor("Literals//Byte array", FLSyntaxHighlighter.BYTES),
            new AttributesDescriptor("Literals//Number", FLSyntaxHighlighter.NUMBER),
            new AttributesDescriptor("Literals//Boolean", FLSyntaxHighlighter.BOOL),
            new AttributesDescriptor("Keyword", FLSyntaxHighlighter.KEYWORD),
            new AttributesDescriptor("Brackets", FLSyntaxHighlighter.BRACKETS),
            new AttributesDescriptor("Braces", FLSyntaxHighlighter.BRACES),
            new AttributesDescriptor("Word", FLSyntaxHighlighter.WORD),
            new AttributesDescriptor("Qualified word", FLSyntaxHighlighter.QUALIFIED_WORD),
            new AttributesDescriptor("Header//Parentheses", FLSyntaxHighlighter.HEADER_PAREN),
            new AttributesDescriptor("Header//Parameter name", FLSyntaxHighlighter.HEADER_PARAMETER),
            new AttributesDescriptor("Header//Parameter type, constraint and default",
                    FLSyntaxHighlighter.HEADER_PARAMETER_DETAILS),
            new AttributesDescriptor("Header//Type name", FLSyntaxHighlighter.HEADER_TYPE),
            new AttributesDescriptor("Header//Arrow", FLSyntaxHighlighter.HEADER_ARROW),
            new AttributesDescriptor("Header//Output", FLSyntaxHighlighter.HEADER_OUTPUT),
            new AttributesDescriptor("Semantic//Declaration keyword (doc, category, META: clauses)", FLSyntaxHighlighter.META_KEYWORD),
            new AttributesDescriptor("Semantic//Declared name", FLSyntaxHighlighter.DECLARATION_NAME),
            new AttributesDescriptor("Semantic//Parameter use", FLSyntaxHighlighter.PARAMETER_USE),
            new AttributesDescriptor("Semantic//Built-in word use", FLSyntaxHighlighter.BUILTIN_WORD),
            new AttributesDescriptor("Semantic//Vocabulary name", FLSyntaxHighlighter.VOCABULARY),
            new AttributesDescriptor("Bad character", FLSyntaxHighlighter.BAD_CHARACTER),
    };

    @NotNull
    @Override
    public String getDisplayName() {
        return "FL";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return FLIcons.FILE;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return new FLSyntaxHighlighter();
    }

    @NotNull
    @Override
    public String getDemoText() {
        return "\\ A line comment\n"
                + "IN: <voc>demo</voc>\n"
                + "USING: <voc>util</voc> ;\n"
                + "256 CONSTANT <decl>block-size</decl>\n"
                + "0 VARIABLE <decl>counter</decl>\n"
                + "\n"
                + ": <decl>derive</decl> ( password:str salt:key{utf8|hex}[8..64]=\"somesalt\" -- bytes )\n"
                + "  \"The hash of the password.\" <mk>doc</mk>\n"
                + "  \"Key derivation\" <mk>category</mk>\n"
                + "  ( a block comment ) <par>password</par> <par>salt</par> <voc>util</voc>:_derive\n"
                + "  [ 1 2 <bi>+</bi> ] <bi>drop</bi> { 'a' #[ de ad ] true 0x1F 1.5e3 } <bi>drop</bi> counter!\n"
                + "  <bi>dup</bi> :> <par>hash</par> <par>hash</par> ;\n"
                + "\n"
                + "CHOICE: <decl>padding</decl>\n"
                + "  <mk>option</mk> pkcs7 \"PKCS#7 padding.\" ;\n"
                + "  <mk>option</mk> none ;\n"
                + ";\n"
                + "\n"
                + "MEMO: <decl>_table</decl> ( -- record ) { } <bi>>record</bi> ;\n"
                + "\n"
                + "META: derive\n"
                + "  <mk>param</mk> salt \"The salt, at least 8 bytes.\" ;\n"
                + "  <mk>see-also</mk> <voc>util</voc>:compare ;\n"
                + "  <mk>use-when</mk> \"You need a key from a password.\" ;\n"
                + "  <mk>link</mk> \"https://example.com/kdf\" \"About KDFs\" ;\n"
                + ";\n";
    }

    @Nullable
    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        Map<String, TextAttributesKey> m = new java.util.HashMap<>();
        m.put("decl", FLSyntaxHighlighter.DECLARATION_NAME);
        m.put("par", FLSyntaxHighlighter.PARAMETER_USE);
        m.put("bi", FLSyntaxHighlighter.BUILTIN_WORD);
        m.put("voc", FLSyntaxHighlighter.VOCABULARY);
        m.put("mk", FLSyntaxHighlighter.META_KEYWORD);
        return m;
    }
}
