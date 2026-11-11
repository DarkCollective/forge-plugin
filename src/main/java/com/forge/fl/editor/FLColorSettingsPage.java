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
                "true false\n" +
                "IN: core\n" +
                ": greet ( -- )\n" +
                "  42 3.14 \"hello\"\n" +
                ";\n";
    }

    @Nullable
    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }
}
