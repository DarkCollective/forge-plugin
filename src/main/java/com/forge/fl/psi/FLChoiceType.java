// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.lexer.FLSyntax;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code CHOICE: name option... ;}, a shared choice type (FL v0.2, docs/LANGUAGE.md 5.12). Types live in a
 * namespace of their own: a type and a word of the same name in one vocabulary do not clash.
 */
public class FLChoiceType extends FLNamedElement {
    public FLChoiceType(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    protected IElementType nameTokenType() {
        return FLTokenTypes.WORD_NAME;
    }

    @Override
    public String getKindName() {
        return "choice type";
    }

    @Override
    protected PsiElement createNameLeaf(String name) {
        return FLElementFactory.createChoiceTypeName(getProject(), name);
    }

    /** The option clauses in order. */
    @NotNull
    public List<PsiElement> getOptionClauses() {
        List<PsiElement> out = new ArrayList<>();
        for (PsiElement c = getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNode().getElementType() == FLElementTypes.CHOICE_OPTION) out.add(c);
        }
        return out;
    }

    /** The option of a clause as text (a string option decoded), or null when the clause has none. */
    @Nullable
    public static String optionOf(@NotNull PsiElement clause) {
        PsiElement arg = argument(clause, 0);
        if (arg == null) return null;
        return arg.getNode().getElementType() == FLTokenTypes.STRING ? FLSyntax.decodeString(arg.getText()) : arg.getText();
    }

    /** The help text of a clause, or null. */
    @Nullable
    public static String helpOf(@NotNull PsiElement clause) {
        PsiElement arg = argument(clause, 1);
        return arg != null && arg.getNode().getElementType() == FLTokenTypes.STRING ? FLSyntax.decodeString(arg.getText()) : null;
    }

    /** The {@code k}-th argument leaf of an option clause (after the keyword), or null. */
    @Nullable
    public static PsiElement argument(@NotNull PsiElement clause, int k) {
        PsiElement first = PsiTreeUtil.skipWhitespacesAndCommentsForward(clause.getFirstChild());
        int i = 0;
        for (PsiElement c = first; c != null; c = PsiTreeUtil.skipWhitespacesAndCommentsForward(c)) {
            IElementType t = c.getNode().getElementType();
            if (t == FLTokenTypes.SEMI) return null;
            if (t == FLTokenTypes.WORD_NAME || t == FLTokenTypes.STRING || t == FLTokenTypes.NUMBER) {
                if (i++ == k) return c;
            }
        }
        return null;
    }

    /** The options in order, as the runtime stores them. */
    @NotNull
    public List<String> getOptions() {
        List<String> out = new ArrayList<>();
        for (PsiElement c : getOptionClauses()) {
            String o = optionOf(c);
            if (o != null) out.add(o);
        }
        return out;
    }

    /** {@code {a|b|c}}, as the header {@code choice{...}} form would read. */
    @Nullable
    @Override
    public String getEffectText() {
        return "{" + String.join("|", getOptions()) + "}";
    }

    @Override
    public String getLocationText() {
        return "choice" + getEffectText();
    }
}
