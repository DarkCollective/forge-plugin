package com.forge.fl.editor;

import com.forge.fl.FLIcons;
import com.forge.fl.FLSyntaxHighlighter;
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
    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[] {
            new AttributesDescriptor("Comment", FLSyntaxHighlighter.FL_COMMENT),
            new AttributesDescriptor("String", FLSyntaxHighlighter.FL_STRING),
            new AttributesDescriptor("Number", FLSyntaxHighlighter.FL_NUMBER),
            new AttributesDescriptor("Keyword", FLSyntaxHighlighter.FL_KEYWORD),
            new AttributesDescriptor("Boolean", FLSyntaxHighlighter.FL_BOOLEAN),
            new AttributesDescriptor("Identifier", FLSyntaxHighlighter.FL_IDENTIFIER),
            new AttributesDescriptor("Metadata", FLSyntaxHighlighter.FL_METADATA),
            new AttributesDescriptor("Bad Character", FLSyntaxHighlighter.FL_BAD_CHARACTER)
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

    @NotNull
    @Override
    public AttributesDescriptor[] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @NotNull
    @Override
    public ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return new com.forge.fl.FLSyntaxHighlighter();
    }

    @NotNull
    @Override
    public String getDemoText() {
        return "\\ line comments\n" +
                "IN: argon2\n" +
                "\n" +
                ": derive ( password:bytes salt:key{utf8|hex}=\"somesalt\" -- bytes )\n" +
                "  \"The Argon2 hash of the input password.\" doc\n" +
                "  \"Key derivation\" category\n" +
                "  password salt _derive ;\n" +
                "\n" +
                "META: derive\n" +
                "  param salt \"The salt, at least 8 bytes.\" ;\n" +
                "  see-also argon2:compare ;\n" +
                ";\n";
    }

    @Nullable
    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }
}
