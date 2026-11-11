package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A use of a word: a body word, a stray top-level word, a META: target or a {@code see-also} name. It wraps one
 * WORD_NAME or QUALIFIED_NAME leaf and carries a soft reference to its declaration in the same file.
 */
public class FLWordRef extends FLPsiElement {
    public FLWordRef(@NotNull ASTNode node) {
        super(node);
    }

    @Nullable
    @Override
    public PsiReference getReference() {
        return new FLWordReference(this);
    }

    @Nullable
    public PsiElement getNameLeaf() {
        ASTNode n = getNode().getFirstChildNode();
        return n == null ? null : n.getPsi();
    }

    public boolean isQualified() {
        return getText().indexOf(':') > 0;
    }

    /** The text before the colon of a qualified name, or null. */
    @Nullable
    public String getVocab() {
        String t = getText();
        int c = t.indexOf(':');
        return c > 0 ? t.substring(0, c) : null;
    }

    /** The word name without any {@code vocab:} prefix. */
    @NotNull
    public String getBareName() {
        String t = getText();
        return t.substring(t.indexOf(':') + 1);
    }

    /** Resolution per docs/LANGUAGE.md 6.2 and 6.3, restricted to this file. */
    @Nullable
    PsiElement resolveTarget() {
        PsiFile file = getContainingFile();
        if (file == null) return null;
        FLScope scope = FLScope.of(file);
        PsiElement top = this;
        while (top.getParent() != null && !(top.getParent() instanceof PsiFile)) top = top.getParent();
        int formStart = top.getTextRange().getStartOffset();
        String vocab = getVocab();
        String name = getBareName();
        if (vocab == null) {
            FLDefinition def = PsiTreeUtil.getParentOfType(this, FLDefinition.class);
            if (def != null) {
                FLParameter p = def.findParameter(name);
                if (p != null) return p;
            }
            vocab = scope.vocabAt(getTextRange().getStartOffset());
        }
        return FLScope.pick(scope.find(vocab, name), formStart);
    }

    /** Replaces the name part (after any {@code vocab:}) with {@code newName}, plus {@code !} when asked. */
    void replaceName(String newName, boolean bang) {
        PsiElement leaf = getNameLeaf();
        if (leaf == null) return;
        String vocab = getVocab();
        String text = (vocab == null ? "" : vocab + ":") + newName + (bang ? "!" : "");
        leaf.replace(FLElementFactory.createWordRefLeaf(getProject(), text));
    }

    boolean isNameLeafKind() {
        PsiElement leaf = getNameLeaf();
        return leaf != null && (leaf.getNode().getElementType() == FLTokenTypes.WORD_NAME
                || leaf.getNode().getElementType() == FLTokenTypes.QUALIFIED_NAME);
    }
}
