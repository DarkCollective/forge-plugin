package com.forge.fl.psi;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

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
     * Declarations are searched for in their own file only. This deliberately bypasses the word index, which
     * cannot index symbol-only names such as {@code +} or {@code >str}; a later phase (cross-file and core
     * resolution) replaces this with an index.
     */
    @NotNull
    @Override
    public SearchScope getUseScope() {
        return new LocalSearchScope(getContainingFile());
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
