package com.forge.fl.psi;

import com.forge.fl.FLLanguage;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class FLReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(FLWordElement.class).withLanguage(FLLanguage.INSTANCE),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                           @NotNull ProcessingContext context) {
                        if (!(element instanceof FLWordElement word)) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        String text = word.getText();
                        if (text == null || text.isBlank()) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        if (text.contains(":") || text.equals("IN:") || text.equals("USING:") || text.equals("META:")) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        return new PsiReference[] { new FLReference(word) };
                    }
                }
        );
    }
}
