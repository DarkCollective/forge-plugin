// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.doc;

import com.forge.fl.index.FLIndexLookup;
import com.forge.fl.psi.FLNamedElement;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Quick documentation for FL declarations, from a use, from the declaration itself and from the completion popup:
 * qualified name, kind, stack effect, documentation text, category, version, parameters with their types and help,
 * aliases and see-also. Every piece of text from the source is HTML-escaped.
 */
public final class FLDocumentationProvider extends AbstractDocumentationProvider {
    @Nullable
    @Override
    public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        if (!(element instanceof FLNamedElement named)) return null;
        return render(FLDocInfo.of(named));
    }

    @Nullable
    @Override
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        if (!(element instanceof FLNamedElement named)) return null;
        FLDocInfo info = FLDocInfo.of(named);
        return info.qname + (info.header == null ? "" : " " + info.header);
    }

    @Nullable
    @Override
    public PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file,
                                                    @Nullable PsiElement contextElement, int targetOffset) {
        PsiElement parent = contextElement == null ? null : contextElement.getParent();
        if (parent instanceof FLNamedElement named && named.getNameIdentifier() == contextElement) return named;
        return null;
    }

    @Nullable
    @Override
    public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
        if (object instanceof FLNamedElement named) return named;
        if (object instanceof FLLookupObject l) {
            return FLIndexLookup.element(psiManager.getProject(), l.file(), l.entry());
        }
        return null;
    }

    // ---- HTML ----------------------------------------------------------------------------------------------

    static String esc(String s) {
        return StringUtil.escapeXmlEntities(s);
    }

    private static String escMultiline(String s) {
        return esc(s).replace("\r\n", "\n").replace("\n", "<br/>");
    }

    /** The documentation as the HTML the popup shows. */
    @NotNull
    public static String render(@NotNull FLDocInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append(DocumentationMarkup.DEFINITION_START);
        sb.append("<b>").append(esc(info.qname)).append("</b>");
        if (info.header != null) sb.append(' ').append(esc(info.header));
        if (info.setterHeader != null) sb.append("<br/><b>").append(esc(info.qname)).append("!</b> ").append(esc(info.setterHeader));
        sb.append(DocumentationMarkup.DEFINITION_END);
        if (info.doc != null && !info.doc.isBlank()) {
            sb.append(DocumentationMarkup.CONTENT_START).append(escMultiline(info.doc)).append(DocumentationMarkup.CONTENT_END);
        }
        StringBuilder rows = new StringBuilder();
        row(rows, "Kind", kindText(info));
        if (info.parameterOf != null) row(rows, "Parameter of", esc(info.parameterOf));
        if (info.aliasOf != null) row(rows, "Alias of", esc(info.aliasOf));
        if (info.value != null) row(rows, "Value", "<code>" + esc(info.value) + "</code>");
        if (info.category != null) row(rows, "Category", esc(info.category));
        if (info.version != null) row(rows, "Version", esc(info.version));
        if (!info.aliases.isEmpty()) row(rows, "Aliases", join(info.aliases));
        if (!info.seeAlso.isEmpty()) row(rows, "See also", join(info.seeAlso));
        if (!info.params.isEmpty()) {
            StringBuilder ps = new StringBuilder();
            for (FLDocInfo.Param p : info.params) {
                if (ps.length() > 0) ps.append("<br/>");
                ps.append("<code>").append(esc(p.name())).append(':').append(esc(p.type())).append("</code>");
                if (p.help() != null) ps.append(" &ndash; ").append(escMultiline(p.help()));
            }
            row(rows, info.params.size() == 1 && "parameter".equals(info.kind) ? "Type and help" : "Parameters", ps.toString());
        }
        sb.append(DocumentationMarkup.SECTIONS_START).append(rows).append(DocumentationMarkup.SECTIONS_END);
        return sb.toString();
    }

    private static String kindText(FLDocInfo info) {
        if (info.builtinKind != null) return esc("built-in " + info.kind + " (" + info.builtinKind + ")");
        return esc(info.kind);
    }

    private static String join(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("<code>").append(esc(n)).append("</code>");
        }
        return sb.toString();
    }

    /** The value is already HTML. */
    private static void row(StringBuilder sb, String label, String html) {
        sb.append("<tr><td valign='top' class='section'><p>").append(label).append(":</p></td><td valign='top'>")
                .append(html).append("</td></tr>");
    }
}
