package com.forge.fl.psi;

import com.forge.fl.FLLanguage;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameInputValidator;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class FLRenameInputValidator implements RenameInputValidator {
    @Override
    public ElementPattern<? extends PsiElement> getPattern() {
        return PlatformPatterns.psiElement(FLWordElement.class).withLanguage(FLLanguage.INSTANCE);
    }

    @Override
    public boolean isInputValid(@NotNull String newName,
                               @NotNull PsiElement element,
                               @NotNull ProcessingContext context) {
        if (newName == null || newName.isBlank()) {
            return false;
        }
        if (newName.contains(":")) {
            return false;
        }
        if (newName.contains(" ") || newName.contains("\t") || newName.contains("\n") || newName.contains("\r")) {
            return false;
        }
        if (newName.equals("IN:") || newName.equals("USING:") || newName.equals("META:")
                || newName.equals("VOCAB") || newName.equals("CONSTANT") || newName.equals("VARIABLE")
                || newName.equals("PRIMITIVE") || newName.equals("doc") || newName.equals("category")
                || newName.equals("version") || newName.equals("param") || newName.equals("alias")
                || newName.equals("see-also") || newName.equals("seeAlso") || newName.equals("recurse")) {
            return false;
        }
        return newName.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '!' || ch == '$'
                || ch == '%' || ch == '&' || ch == '*' || ch == '+' || ch == ',' || ch == '.' || ch == '/'
                || ch == '<' || ch == '=' || ch == '>' || ch == '?' || ch == '@' || ch == '^' || ch == '|'
                || ch == '~');
    }
}
