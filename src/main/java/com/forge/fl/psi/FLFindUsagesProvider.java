package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.forge.fl.lexer.FLLexer;
import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FLFindUsagesProvider implements FindUsagesProvider {
    @Nullable
    @Override
    public WordsScanner getWordsScanner() {
        return new DefaultWordsScanner(new FLLexer(),
                TokenSet.orSet(FLTokenTypes.IDENTIFIERS, FLTokenTypes.KEYWORDS),
                FLTokenTypes.COMMENTS,
                FLTokenTypes.STRINGS);
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement element) {
        return element instanceof FLNamedElement;
    }

    @NotNull
    @Override
    public String getHelpId(@NotNull PsiElement element) {
        return "reference.findUsages";
    }

    @NotNull
    @Override
    public String getType(@NotNull PsiElement element) {
        return element instanceof FLNamedElement named ? named.getKindName() : "";
    }

    @NotNull
    @Override
    public String getDescriptiveName(@NotNull PsiElement element) {
        String name = element instanceof PsiNamedElement named ? named.getName() : null;
        return name == null ? "" : name;
    }

    @NotNull
    @Override
    public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return getDescriptiveName(element);
    }
}
