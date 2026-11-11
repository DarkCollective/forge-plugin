package com.forge.fl.completion;

import com.forge.fl.FLLanguage;
import com.forge.fl.FLTokenTypes;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLParameter;
import com.forge.fl.psi.FLScope;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Offers the language keywords, the declarations of the current file and the parameters in scope. Core words and
 * words of other files are a later phase, so nothing is invented here.
 */
public class FLCompletionContributor extends CompletionContributor {
    private static final List<String> KEYWORDS = List.of(
            ":", ";", "IN:", "USING:", "META:", "VOCAB", "CONSTANT", "VARIABLE", "PRIMITIVE", "recurse", "true", "false");

    public FLCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(FLTokenTypes.WORD_NAME).withLanguage(FLLanguage.INSTANCE),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        for (String k : KEYWORDS) result.addElement(LookupElementBuilder.create(k).bold());
                        PsiElement position = parameters.getPosition();
                        FLDefinition def = PsiTreeUtil.getParentOfType(position, FLDefinition.class);
                        if (def != null) {
                            for (FLParameter p : def.getParameters()) {
                                if (p.getName() != null) {
                                    result.addElement(LookupElementBuilder.create(p.getName())
                                            .withIcon(p.getIcon(0)).withTypeText("parameter"));
                                }
                            }
                        }
                        PsiFile file = parameters.getOriginalFile();
                        for (FLScope.Decl d : FLScope.of(file).all()) {
                            result.addElement(LookupElementBuilder.create(d.name())
                                    .withIcon(d.element().getIcon(0)).withTypeText(d.element().getKindName()));
                        }
                    }
                });
    }
}
