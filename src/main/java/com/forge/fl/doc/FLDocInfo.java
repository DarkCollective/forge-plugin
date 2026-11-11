// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.doc;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.index.FLIndexEntry;
import com.forge.fl.index.FLIndexLookup;
import com.forge.fl.lexer.FLSyntax;
import com.forge.fl.psi.FLAlias;
import com.forge.fl.psi.FLConstant;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLElementTypes;
import com.forge.fl.psi.FLMetaBlock;
import com.forge.fl.psi.FLMetaClause;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.psi.FLParamRef;
import com.forge.fl.psi.FLParameter;
import com.forge.fl.psi.FLPrimitive;
import com.forge.fl.psi.FLScope;
import com.forge.fl.psi.FLVariable;
import com.forge.fl.psi.FLWordRef;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the quick documentation shows about a declaration, gathered from the PSI (docs/LANGUAGE.md 12.2): the
 * leading {@code "..." doc} / {@code "..." category} declarations of the definition, the {@code META:} blocks of
 * the same file that target it, and, for a built-in word, the comment lines and the {@code [TypeScript ...]}
 * marker above its declaration. A singular field (doc, category, version) is set once; the first source wins.
 */
public final class FLDocInfo {
    /** A header parameter with its type text ({@code int[1..25]=13}) and the {@code META: param} help, if any. */
    public record Param(String name, String type, @Nullable String help) {
    }

    /** word, constant, variable, primitive, alias or parameter. */
    public String kind = "word";
    /** {@code vocab:name}; for a parameter the name itself. */
    public String qname = "";
    @Nullable public String header;
    /** For a variable: the effect of the setter {@code name!}. */
    @Nullable public String setterHeader;
    /** For a constant or variable: the literal that initialises it. */
    @Nullable public String value;
    @Nullable public String doc;
    @Nullable public String category;
    @Nullable public String version;
    public final List<Param> params = new ArrayList<>();
    public final List<String> aliases = new ArrayList<>();
    public final List<String> seeAlso = new ArrayList<>();
    /** For an alias: the qualified name of the word it stands for. */
    @Nullable public String aliasOf;
    /** For a parameter: the qualified name of its word. */
    @Nullable public String parameterOf;
    /** For a built-in word: {@code TypeScript primitive} or {@code TypeScript intrinsic}. */
    @Nullable public String builtinKind;

    private static final Pattern MARKER = Pattern.compile("^\\[TypeScript (primitive|intrinsic)(.*)]$");

    private FLDocInfo() {
    }

    @NotNull
    public static FLDocInfo of(@NotNull FLNamedElement e) {
        FLDocInfo info = new FLDocInfo();
        PsiFile file = e.getContainingFile();
        String vocab = FLScope.of(file).vocabAt(e.getTextOffset());
        String name = String.valueOf(e.getName());
        if (e instanceof FLParameter p) {
            info.fillParameter(p);
        } else if (e instanceof FLAlias a) {
            info.fillAlias(a, vocab, name);
        } else {
            info.qname = vocab + ":" + name;
            info.fillDeclaration(e, vocab);
        }
        return info;
    }

    // ---- kinds ---------------------------------------------------------------------------------------------

    private void fillDeclaration(FLNamedElement e, String vocab) {
        kind = e.getKindName();
        header = e.getEffectText();
        if (e instanceof FLConstant || e instanceof FLVariable) {
            PsiElement lit = e.getFirstChild();
            value = lit == null ? null : lit.getText();
        }
        if (e instanceof FLVariable v) {
            String eff = v.getEffectText(); // ( -- T )
            if (eff != null && eff.length() > 6) setterHeader = "( " + eff.substring(5, eff.length() - 2) + " -- )";
        }
        if (e instanceof FLDefinition d) {
            readParams(d);
            readLeadingDecls(d);
            if (FLBuiltins.isBuiltin(d)) readBuiltinComments(d);
        } else if (e instanceof FLPrimitive) {
            // the declaration of a primitive says nothing: its effect and documentation are those of the built-in
            FLDefinition builtin = findBuiltin(e, vocab);
            if (builtin != null) {
                header = builtin.getEffectText();
                readParams(builtin);
                readBuiltinComments(builtin);
            }
        }
        readMetaBlocks(e);
    }

    private void fillAlias(FLAlias a, String vocab, String name) {
        kind = "alias";
        qname = vocab + ":" + name;
        FLMetaBlock block = PsiTreeUtil.getParentOfType(a, FLMetaBlock.class);
        FLWordRef target = block == null ? null : block.getTarget();
        PsiElement resolved = target == null ? null : target.getReference().resolve();
        if (resolved instanceof FLNamedElement t) {
            FLDocInfo ti = of(t);
            aliasOf = ti.qname;
            header = ti.header;
            setterHeader = ti.setterHeader;
            value = ti.value;
            doc = ti.doc;
            category = ti.category;
            version = ti.version;
            params.addAll(ti.params);
            seeAlso.addAll(ti.seeAlso);
            builtinKind = ti.builtinKind;
        } else if (target != null) {
            aliasOf = vocab + ":" + target.getText();
        }
    }

    private void fillParameter(FLParameter p) {
        kind = "parameter";
        qname = String.valueOf(p.getName());
        FLDefinition d = PsiTreeUtil.getParentOfType(p, FLDefinition.class);
        if (d == null) return;
        String vocab = FLScope.of(d.getContainingFile()).vocabAt(d.getTextOffset());
        parameterOf = vocab + ":" + d.getName();
        header = d.getEffectText();
        FLDocInfo owner = of(d);
        for (Param q : owner.params) {
            if (q.name().equals(p.getName())) params.add(q);
        }
        builtinKind = owner.builtinKind;
    }

    // ---- sources ------------------------------------------------------------------------------------------

    private void readParams(FLDefinition d) {
        for (FLParameter p : d.getParameters()) {
            String text = p.getText();
            int colon = text.indexOf(':');
            String n = String.valueOf(p.getName());
            params.add(new Param(n, colon < 0 ? "any" : text.substring(colon + 1), null));
        }
    }

    private void readLeadingDecls(FLDefinition d) {
        for (PsiElement c = d.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNode().getElementType() != FLElementTypes.DOC_DECL) continue;
            PsiElement str = c.getFirstChild();
            PsiElement word = c.getLastChild();
            if (str == null || word == null) continue;
            setText(word.getText(), FLSyntax.decodeString(str.getText()));
        }
    }

    private void setText(String field, String text) {
        switch (field) {
            case "doc" -> {
                if (doc == null) doc = text;
            }
            case "category" -> {
                if (category == null) category = text;
            }
            case "version" -> {
                if (version == null) version = text;
            }
            default -> {
            }
        }
    }

    /** META: blocks of the same file that target this declaration (5.9: in the vocabulary of the declaration). */
    private void readMetaBlocks(FLNamedElement e) {
        PsiFile file = e.getContainingFile();
        Map<String, String> help = new LinkedHashMap<>();
        for (FLMetaBlock block : PsiTreeUtil.getChildrenOfTypeAsList(file, FLMetaBlock.class)) {
            FLWordRef target = block.getTarget();
            if (target == null || !target.getBareName().equals(e.getName())) continue;
            PsiElement resolved = target.getReference().resolve();
            if (resolved == null || !e.getManager().areElementsEquivalent(resolved, e)) continue;
            for (FLMetaClause clause : block.getClauses()) {
                readClause(clause, e, help);
            }
        }
        for (int i = 0; i < params.size(); i++) {
            Param p = params.get(i);
            String h = help.get(p.name());
            if (h != null) params.set(i, new Param(p.name(), p.type(), h));
        }
    }

    private void readClause(FLMetaClause clause, FLNamedElement e, Map<String, String> help) {
        String key = clause.getKeyword();
        if (key == null) return;
        switch (key) {
            case "doc", "category", "version" -> {
                PsiElement str = lastOfType(clause, FLTokenTypes.STRING);
                if (str != null) setText(key, FLSyntax.decodeString(str.getText()));
            }
            case "param" -> {
                FLParamRef ref = PsiTreeUtil.getChildOfType(clause, FLParamRef.class);
                PsiElement str = lastOfType(clause, FLTokenTypes.STRING);
                if (ref != null && str != null) help.putIfAbsent(ref.getText(), FLSyntax.decodeString(str.getText()));
            }
            case "alias" -> {
                for (FLAlias a : PsiTreeUtil.getChildrenOfTypeAsList(clause, FLAlias.class)) {
                    if (a.getName() != null && !aliases.contains(a.getName())) aliases.add(a.getName());
                }
            }
            case "see-also" -> {
                String vocab = FLScope.of(e.getContainingFile()).vocabAt(clause.getTextOffset());
                for (FLWordRef r : PsiTreeUtil.getChildrenOfTypeAsList(clause, FLWordRef.class)) {
                    String q = r.isQualified() ? r.getText() : vocab + ":" + r.getText();
                    if (!seeAlso.contains(q)) seeAlso.add(q);
                }
            }
            default -> {
            }
        }
    }

    @Nullable
    private static PsiElement lastOfType(PsiElement parent, com.intellij.psi.tree.IElementType type) {
        PsiElement found = null;
        for (PsiElement c = parent.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNode().getElementType() == type) found = c;
        }
        return found;
    }

    /** The generated declaration of the same name in the same vocabulary (a PRIMITIVE binds one). */
    @Nullable
    private static FLDefinition findBuiltin(FLNamedElement e, String vocab) {
        String name = e.getName();
        if (name == null) return null;
        for (FLIndexLookup.Hit hit : FLIndexLookup.declarations(e.getProject(), vocab, name)) {
            if (!FLBuiltins.isBuiltinFile(hit.file())) continue;
            for (FLIndexEntry entry : hit.entries()) {
                if (entry.isDeclaration() && FLIndexLookup.element(e.getProject(), hit.file(), entry) instanceof FLDefinition d) {
                    return d;
                }
            }
        }
        return null;
    }

    /**
     * The generated declaration files put the documentation in {@code \} comment lines right above the word, the
     * last of them the marker {@code [TypeScript primitive, category X, alias of Y]} (see FLBuiltins#render).
     */
    private void readBuiltinComments(FLDefinition d) {
        List<String> lines = new ArrayList<>();
        PsiElement p = d.getPrevSibling();
        while (p != null) {
            if (p instanceof PsiWhiteSpace) {
                if (com.intellij.openapi.util.text.StringUtil.countNewLines(p.getText()) > 1) break;
            } else if (p instanceof PsiComment) {
                String t = p.getText();
                t = t.startsWith("\\") ? t.substring(1) : t;
                lines.add(0, t.startsWith(" ") ? t.substring(1) : t);
            } else {
                break;
            }
            p = p.getPrevSibling();
        }
        if (!lines.isEmpty()) {
            Matcher m = MARKER.matcher(lines.get(lines.size() - 1).trim());
            if (m.matches()) {
                lines.remove(lines.size() - 1);
                builtinKind = "TypeScript " + m.group(1);
                String rest = m.group(2);
                int aliasAt = rest.indexOf(", alias of ");
                if (aliasAt >= 0) {
                    aliasOf = rest.substring(aliasAt + ", alias of ".length()).trim();
                    rest = rest.substring(0, aliasAt);
                }
                int catAt = rest.indexOf(", category ");
                if (catAt >= 0 && category == null) category = rest.substring(catAt + ", category ".length()).trim();
            }
        }
        String text = String.join("\n", lines).trim();
        if (!text.isEmpty() && doc == null) doc = text;
    }
}
