package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FLFindUsagesProvider implements FindUsagesProvider {
    @Override
    @Nullable
    public WordsScanner getWordsScanner() {
        return new DefaultWordsScanner(
                new com.forge.fl.FLLexer(),
                TokenSet.orSet(FLTokenTypes.KEYWORDS, TokenSet.create(FLTokenTypes.IDENTIFIER)),
                FLTokenTypes.COMMENTS,
                FLTokenTypes.STRINGS
        );
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
        return psiElement instanceof FLNamedElement || psiElement instanceof FLWordElement;
    }

    @Override
    @NotNull
    public String getDescriptiveName(@NotNull PsiElement psiElement) {
        if (psiElement instanceof PsiNamedElement named) {
            return named.getName() == null ? "" : named.getName();
        }
        return psiElement.getText();
    }

    @Override
    @NotNull
    public String getType(@NotNull PsiElement psiElement) {
        return "FL word";
    }

    @Override
    @NotNull
    public String getNodeText(@NotNull PsiElement psiElement, boolean useFullName) {
        return getDescriptiveName(psiElement);
    }

    @Override
    @NotNull
    public String getHelpId(@NotNull PsiElement psiElement) {
        return "reference.findUsages";
    }
}
