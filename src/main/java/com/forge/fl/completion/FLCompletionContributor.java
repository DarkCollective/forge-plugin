// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.completion;

import com.forge.fl.FLLanguage;
import com.forge.fl.FLTokenTypes;
import com.forge.fl.doc.FLLookupObject;
import com.forge.fl.index.FLIndexLookup;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLInForm;
import com.forge.fl.psi.FLMetaBlock;
import com.forge.fl.psi.FLMetaClause;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.psi.FLParameter;
import com.forge.fl.psi.FLResolver;
import com.forge.fl.psi.FLScope;
import com.forge.fl.psi.FLVariable;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Completion of FL names. At a word position it offers what is visible there (docs/LANGUAGE.md 6.2): the
 * parameters of the enclosing definition, the current vocabulary (private words too), the public words of the
 * imported vocabularies, then {@code core} (built-ins included); each name once, the nearest meaning first. After
 * {@code vocab:} it offers the words of that vocabulary. After {@code USING:} and {@code IN:} it offers
 * vocabulary names. The stack effect is the tail text and the vocabulary the type text.
 */
public class FLCompletionContributor extends CompletionContributor {
    private static final List<String> KEYWORDS = List.of(
            ":", ";", "IN:", "USING:", "META:", "VOCAB", "CONSTANT", "VARIABLE", "PRIMITIVE", "recurse", "true", "false");

    public FLCompletionContributor() {
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(FLLanguage.INSTANCE)
                        .withElementType(TokenSet.create(FLTokenTypes.WORD_NAME, FLTokenTypes.QUALIFIED_NAME)),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        complete(parameters, result);
                    }
                });
    }

    private static void complete(CompletionParameters parameters, CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiElement parent = position.getParent();
        if (parent instanceof FLNamedElement) return; // the name of a new declaration: nothing to suggest
        boolean vocabPosition = parent instanceof FLInForm
                || (parent != null && parent.getNode().getElementType() == com.forge.fl.psi.FLElementTypes.USING_FORM);

        int caret = parameters.getOffset() - position.getTextRange().getStartOffset();
        String typed = position.getText().substring(0, Math.max(0, Math.min(caret, position.getTextLength())));
        PsiFile file = parameters.getOriginalFile();
        FLScope scope = FLScope.of(file);
        int offset = parameters.getOffset();
        String current = scope.vocabAt(offset);
        VirtualFile self = file.getVirtualFile();

        if (vocabPosition) {
            CompletionResultSet r = result.withPrefixMatcher(new PlainPrefixMatcher(typed, true));
            Set<String> names = new LinkedHashSet<>(scope.vocabularies());
            names.addAll(FLIndexLookup.vocabularies(file.getProject()));
            names.add(FLScope.DEFAULT_VOCAB); // the default vocabulary always exists
            names.remove("core");
            for (String v : names) r.addElement(LookupElementBuilder.create(v).withTypeText("vocabulary"));
            return;
        }

        int colon = typed.indexOf(':');
        if (position.getNode().getElementType() == FLTokenTypes.QUALIFIED_NAME && colon > 0) {
            String vocab = typed.substring(0, colon);
            CompletionResultSet r = result.withPrefixMatcher(new PlainPrefixMatcher(typed.substring(colon + 1), true));
            Map<String, LookupElementBuilder> items = new LinkedHashMap<>();
            addVocab(items, file, scope, vocab, self, vocab.equals(current));
            for (LookupElementBuilder b : items.values()) r.addElement(b);
            return;
        }

        CompletionResultSet r = result.withPrefixMatcher(new PlainPrefixMatcher(typed, true));
        Map<String, LookupElementBuilder> items = new LinkedHashMap<>();
        // 1. parameters (and nothing else inside a META: block header)
        FLDefinition def = PsiTreeUtil.getParentOfType(position, FLDefinition.class);
        if (def == null) {
            def = enclosingDefinitionOf(scope, file, offset);
        }
        if (def != null) {
            for (FLParameter p : def.getParameters()) {
                String n = p.getName();
                if (n != null) {
                    items.putIfAbsent(n, LookupElementBuilder.create(p, n).withIcon(p.getIcon(0)).withTypeText("parameter"));
                }
            }
        }
        // 2. the current vocabulary, 3. imports, 4. core
        addVocab(items, file, scope, current, self, true);
        boolean metaPosition = PsiTreeUtil.getParentOfType(position, FLMetaBlock.class) != null
                || PsiTreeUtil.getParentOfType(position, FLMetaClause.class) != null;
        if (!metaPosition) {
            for (String u : scope.usingAt(offset)) {
                if (!u.equals(current)) addVocab(items, file, scope, u, self, false);
            }
            addVocab(items, file, scope, "core", self, false);
        }
        for (String k : KEYWORDS) r.addElement(LookupElementBuilder.create(k).bold());
        for (LookupElementBuilder b : items.values()) r.addElement(b);
    }

    /** Definition whose text covers the offset (the position of a completion copy may not be inside the PSI). */
    @Nullable
    private static FLDefinition enclosingDefinitionOf(FLScope scope, PsiFile file, int offset) {
        PsiElement at = file.findElementAt(Math.max(0, offset - 1));
        return PsiTreeUtil.getParentOfType(at, FLDefinition.class);
    }

    /**
     * Adds the words of {@code vocab}: those declared in this file, then those in other files. A name already in
     * {@code items} (a nearer meaning) is kept. Private words only when {@code includePrivate}.
     */
    private static void addVocab(Map<String, LookupElementBuilder> items, PsiFile file, FLScope scope, String vocab,
                                 @Nullable VirtualFile self, boolean includePrivate) {
        for (FLScope.Decl d : scope.all()) {
            if (!d.vocab().equals(vocab) || (!includePrivate && FLResolver.isPrivateName(d.name()))) continue;
            FLNamedElement e = d.element();
            String effect = e instanceof FLVariable && d.name().endsWith("!") && !d.name().equals(e.getName())
                    ? setterEffect(e) : e.getEffectText();
            items.putIfAbsent(d.name(), item(e, d.name(), vocab, effect).withIcon(e.getIcon(0)));
        }
        for (FLIndexLookup.Decl d : FLIndexLookup.wordsOf(file.getProject(), vocab, self).values()) {
            if (!includePrivate && FLResolver.isPrivateName(d.name())) continue;
            items.putIfAbsent(d.name(), item(new FLLookupObject(vocab, d.name(), d.file(), d.entry()), d.name(), vocab,
                    d.entry().detail()).withIcon(com.forge.fl.FLIcons.FILE));
        }
    }

    private static String setterEffect(FLNamedElement variable) {
        String eff = variable.getEffectText(); // ( -- T )
        return eff == null ? null : "( " + eff.substring(5, eff.length() - 2) + " -- )";
    }

    /** The lookup object is the declaration (or, for index hits, where it is), so that Quick Documentation works in the popup. */
    private static LookupElementBuilder item(Object object, String name, String vocab, @Nullable String effect) {
        LookupElementBuilder b = LookupElementBuilder.create(object, name).withTypeText(vocab, true);
        if (effect != null) b = b.withTailText(" " + effect, true);
        return b;
    }
}
