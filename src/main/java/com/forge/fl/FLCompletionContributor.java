package com.forge.fl;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class FLCompletionContributor extends CompletionContributor {
    public FLCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(FLLanguage.INSTANCE),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                @NotNull ProcessingContext context,
                                                @NotNull CompletionResultSet result) {
                        result.addElement(LookupElementBuilder.create("IN:"));
                        result.addElement(LookupElementBuilder.create("USING:"));
                        result.addElement(LookupElementBuilder.create("META:"));
                        result.addElement(LookupElementBuilder.create("VOCAB"));
                        result.addElement(LookupElementBuilder.create("CONSTANT"));
                        result.addElement(LookupElementBuilder.create("VARIABLE"));
                        result.addElement(LookupElementBuilder.create("PRIMITIVE"));
                        result.addElement(LookupElementBuilder.create("recurse"));
                        result.addElement(LookupElementBuilder.create("dup"));
                        result.addElement(LookupElementBuilder.create("drop"));
                        result.addElement(LookupElementBuilder.create("swap"));
                        result.addElement(LookupElementBuilder.create("over"));
                        result.addElement(LookupElementBuilder.create("emit"));
                        result.addElement(LookupElementBuilder.create("if"));
                        result.addElement(LookupElementBuilder.create("else"));
                        result.addElement(LookupElementBuilder.create("then"));
                        result.addElement(LookupElementBuilder.create("true"));
                        result.addElement(LookupElementBuilder.create("false"));
                    }
                }
        );
    }
}
