package com.forge.fl.parser;

import com.forge.fl.FLFile;
import com.forge.fl.FLTokenTypes;
import com.forge.fl.psi.FLDefinitionImpl;
import com.forge.fl.psi.FLFileElement;
import com.forge.fl.psi.FLWordElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class FLParserDefinition implements ParserDefinition {
    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new com.forge.fl.lexer.FLLexerAdapter();
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return FLTokenTypes.COMMENTS;
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return FLTokenTypes.STRINGS;
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return new FLParser();
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FLTokenTypes.FILE;
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        if (node.getElementType() == FLTokenTypes.WORD) {
            return new FLWordElement(node);
        }
        if (node.getElementType() == FLTokenTypes.DEFINITION) {
            return new FLDefinitionImpl(node);
        }
        return new FLFileElement(node);
    }

    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new FLFile(viewProvider);
    }

    @Override
    public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }

    @NotNull
    @Override
    public TokenSet getWhitespaceTokens() {
        return TokenSet.WHITE_SPACE;
    }
}
