// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.psi;

import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.index.FLIndexLookup;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A declaration: colon definition, constant, variable, primitive, header parameter or META: alias. The name
 * identifier is the leaf token; usages are separate {@link FLWordRef} elements that only reference it.
 */
public abstract class FLNamedElement extends FLPsiElement implements PsiNameIdentifierOwner {
    protected FLNamedElement(@NotNull ASTNode node) {
        super(node);
    }

    /** The token type of the name leaf. */
    protected abstract IElementType nameTokenType();

    /** Type name shown by find usages: word, constant, variable, primitive, parameter or alias. */
    public abstract String getKindName();

    /** A fresh leaf for the new name, parsed from a dummy FL file. */
    protected abstract PsiElement createNameLeaf(String name);

    @Nullable
    @Override
    public PsiElement getNameIdentifier() {
        ASTNode id = getNode().findChildByType(nameTokenType());
        return id == null ? null : id.getPsi();
    }

    @Nullable
    @Override
    public String getName() {
        PsiElement id = getNameIdentifier();
        return id == null ? null : id.getText();
    }

    @Override
    public PsiElement setName(@NlsSafe @NotNull String name) throws IncorrectOperationException {
        if (FLBuiltins.isBuiltin(this)) throw new IncorrectOperationException("A built-in word cannot be renamed");
        PsiElement id = getNameIdentifier();
        if (id != null) id.replace(createNameLeaf(name));
        return this;
    }

    @Override
    public int getTextOffset() {
        PsiElement id = getNameIdentifier();
        return id == null ? super.getTextOffset() : id.getTextOffset();
    }

    /**
     * Where uses can be: a private word (docs/LANGUAGE.md 5.7) is visible only in its own vocabulary, so only in
     * the files that declare into that vocabulary; a public word anywhere in the project. The platform word index
     * cannot hold symbol-only names such as {@code +}, so the search itself is done by
     * {@link com.forge.fl.search.FLReferencesSearcher} through the FL index; this scope only bounds it.
     */
    @NotNull
    @Override
    public SearchScope getUseScope() {
        PsiFile file = getContainingFile();
        String name = getName();
        VirtualFile vf = file.getOriginalFile().getVirtualFile();
        if (name == null || vf == null || !FLIndexLookup.isAvailable(getProject())) return new LocalSearchScope(file);
        if (!FLResolver.isPrivateName(name)) return GlobalSearchScope.projectScope(getProject());
        Set<VirtualFile> files = new LinkedHashSet<>();
        files.add(vf);
        for (FLIndexLookup.Hit hit : FLIndexLookup.filesOfVocab(getProject(), FLScope.of(file).vocabAt(getTextOffset()))) {
            files.add(hit.file());
        }
        return GlobalSearchScope.filesScope(getProject(), files);
    }

    @Override
    public boolean isEquivalentTo(PsiElement another) {
        if (this == another) return true;
        if (!(another instanceof FLNamedElement other) || getTextOffset() != other.getTextOffset()) return false;
        VirtualFile a = getContainingFile().getOriginalFile().getVirtualFile();
        VirtualFile b = other.getContainingFile().getOriginalFile().getVirtualFile();
        return a != null && a.equals(b) && Objects.equals(getName(), other.getName());
    }

    /**
     * The stack effect of the declared name as text, such as {@code ( a b -- c )}, for completion; null when
     * there is none (aliases, primitives). Whitespace is collapsed.
     */
    @Nullable
    public String getEffectText() {
        return null;
    }

    /** The text shown next to the name in the structure view and in navigation popups. */
    @Nullable
    public String getLocationText() {
        return null;
    }

    @Nullable
    @Override
    public ItemPresentation getPresentation() {
        return new PresentationData(getName(), getLocationText(), getIcon(0), null);
    }

    @Nullable
    @Override
    public Icon getIcon(int flags) {
        return FLIconProvider.iconFor(this);
    }
}
