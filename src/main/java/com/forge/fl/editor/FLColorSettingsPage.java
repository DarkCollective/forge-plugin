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
                + "IN: demo\n"
                + "USING: util ;\n"
                + "256 CONSTANT block-size\n"
                + "0 VARIABLE counter\n"
                + "\n"
                + ": derive ( password:str salt:key{utf8|hex}[8..64]=\"somesalt\" -- bytes )\n"
                + "  \"The hash of the password.\" doc\n"
                + "  \"Key derivation\" category\n"
                + "  ( a block comment ) password salt util:_derive\n"
                + "  [ 1 2 + ] drop { 'a' #[ de ad ] true 0x1F 1.5e3 } drop counter! ;\n"
                + "\n"
                + "META: derive\n"
                + "  param salt \"The salt, at least 8 bytes.\" ;\n"
                + "  see-also util:compare ;\n"
                + ";\n";
    }

    @Nullable
    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }
}
