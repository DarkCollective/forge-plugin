// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.hierarchy;

import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.effects.FLEffect;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.psi.FLScope;
import com.forge.fl.run.FLRunInputs;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * One node of an FL call hierarchy: a word (a colon or {@code MEMO:} definition, a primitive), or a file for the
 * top-level code that calls a word. It keeps the call sites it stands for (in a caller, the uses of the word below it
 * in the tree; in a callee, the uses of it in the word above), which give the count and where double-click goes.
 */
public final class FLCallNode extends HierarchyNodeDescriptor {
    private final List<PsiElement> usages = new ArrayList<>();
    private final boolean recursive;

    FLCallNode(@NotNull Project project, @Nullable NodeDescriptor<?> parent, @NotNull PsiElement element, boolean isBase,
               boolean recursive) {
        super(project, parent, element, isBase);
        this.recursive = recursive;
    }

    void addUsage(@NotNull PsiElement usage) {
        usages.add(usage);
    }

    @NotNull
    public List<PsiElement> usages() {
        return usages;
    }

    public boolean isRecursive() {
        return recursive;
    }

    /** The node's text without the decorations: {@code vocab:name ( effect ) file:line}. */
    @NotNull
    public String text() {
        PsiElement e = getPsiElement();
        if (e == null) return "";
        if (e instanceof PsiFile f) return f.getName() + " (top-level code)";
        StringBuilder sb = new StringBuilder(qualifiedName(e));
        String effect = effectText(e);
        if (effect != null) sb.append(' ').append(effect);
        String where = location(e);
        if (where != null) sb.append("  ").append(where);
        return sb.toString();
    }

    @Override
    public boolean update() {
        boolean changes = super.update();
        PsiElement e = getPsiElement();
        if (e == null) return invalidElement();
        CompositeAppearance old = myHighlightedText;
        myHighlightedText = new CompositeAppearance();
        TextAttributes main = new TextAttributes(myColor, null, null, null, Font.PLAIN);
        if (usages.size() > 1) {
            myHighlightedText.getEnding().addText(usages.size() + " uses  ", getUsageCountPrefixAttributes());
        }
        if (e instanceof PsiFile f) {
            myHighlightedText.getEnding().addText(f.getName(), main);
            myHighlightedText.getEnding().addText(" (top-level code)", SimpleTextAttributes.GRAY_ATTRIBUTES);
        } else {
            myHighlightedText.getEnding().addText(qualifiedName(e), main);
            String effect = effectText(e);
            if (effect != null) myHighlightedText.getEnding().addText(" " + effect, SimpleTextAttributes.GRAY_ATTRIBUTES);
            String where = location(e);
            if (where != null) myHighlightedText.getEnding().addText("  " + where, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        }
        if (recursive) myHighlightedText.getEnding().addText("  (recursive)", SimpleTextAttributes.GRAY_ATTRIBUTES);
        myName = myHighlightedText.getText();
        if (!Comparing.equal(myHighlightedText, old)) changes = true;
        return changes;
    }

    static String qualifiedName(PsiElement e) {
        if (e instanceof FLDefinition def) return FLRunInputs.qualifiedName(def);
        if (e instanceof FLNamedElement named && named.getName() != null) {
            String vocab = FLScope.of(e.getContainingFile()).vocabAt(e.getTextRange().getStartOffset());
            return vocab + ":" + named.getName();
        }
        return e.getText();
    }

    @Nullable
    private static String effectText(PsiElement e) {
        if (!(e instanceof FLDefinition def)) return null;
        FLEffect effect = FLEffect.of(def.getHeader());
        return effect == null ? null : effect.text();
    }

    /** {@code file.fth:12}, or {@code built-in} for a word of the generated built-in files. */
    @Nullable
    private static String location(PsiElement e) {
        PsiFile file = e.getContainingFile();
        if (file == null) return null;
        if (FLBuiltins.isBuiltinFile(file)) return "built-in";
        Document doc = PsiDocumentManager.getInstance(e.getProject()).getDocument(file);
        int line = doc == null ? -1 : doc.getLineNumber(e.getTextRange().getStartOffset()) + 1;
        return line > 0 ? file.getName() + ":" + line : file.getName();
    }
}
