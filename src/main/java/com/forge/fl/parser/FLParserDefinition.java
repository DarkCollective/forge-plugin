// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.parser;

import com.forge.fl.FLFile;
import com.forge.fl.FLTokenTypes;
import com.forge.fl.lexer.FLLexer;
import com.forge.fl.psi.FLAlias;
import com.forge.fl.psi.FLConstant;
import com.forge.fl.psi.FLDefinition;
import com.forge.fl.psi.FLElementTypes;
import com.forge.fl.psi.FLHeader;
import com.forge.fl.psi.FLInForm;
import com.forge.fl.psi.FLMetaBlock;
import com.forge.fl.psi.FLMetaClause;
import com.forge.fl.psi.FLParamRef;
import com.forge.fl.psi.FLParameter;
import com.forge.fl.psi.FLPrimitive;
import com.forge.fl.psi.FLPsiElement;
import com.forge.fl.psi.FLVariable;
import com.forge.fl.psi.FLWordRef;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class FLParserDefinition implements ParserDefinition {
    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new FLLexer();
    }

    @NotNull
    @Override
    public PsiParser createParser(Project project) {
        return new FLParser();
    }

    @NotNull
    @Override
    public IFileElementType getFileNodeType() {
        return FLElementTypes.FILE;
    }

    @NotNull
    @Override
    public TokenSet getWhitespaceTokens() {
        return TokenSet.WHITE_SPACE;
    }

    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return FLTokenTypes.COMMENTS;
    }

    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return FLTokenTypes.STRINGS;
    }

    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        IElementType t = node.getElementType();
        if (t == FLElementTypes.DEFINITION) return new FLDefinition(node);
        if (t == FLElementTypes.HEADER) return new FLHeader(node);
        if (t == FLElementTypes.PARAMETER) return new FLParameter(node);
        if (t == FLElementTypes.WORD_REF) return new FLWordRef(node);
        if (t == FLElementTypes.CONSTANT_DECL) return new FLConstant(node);
        if (t == FLElementTypes.VARIABLE_DECL) return new FLVariable(node);
        if (t == FLElementTypes.PRIMITIVE_DECL) return new FLPrimitive(node);
        if (t == FLElementTypes.ALIAS) return new FLAlias(node);
        if (t == FLElementTypes.IN_FORM || t == FLElementTypes.VOCAB_FORM) return new FLInForm(node);
        if (t == FLElementTypes.META_BLOCK) return new FLMetaBlock(node);
        if (t == FLElementTypes.META_CLAUSE) return new FLMetaClause(node);
        if (t == FLElementTypes.PARAM_REF) return new FLParamRef(node);
        if (t == FLElementTypes.LOCAL_BIND) return new com.forge.fl.psi.FLLocal(node);
        if (t == FLElementTypes.CHOICE_DECL) return new com.forge.fl.psi.FLChoiceType(node);
        if (t == FLElementTypes.CHOICE_REF) return new com.forge.fl.psi.FLChoiceRef(node);
        return new FLPsiElement(node); // DOC_DECL, QUOTATION, LIST_LITERAL, USING_FORM, CHOICE_OPTION
    }

    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new FLFile(viewProvider);
    }

    @NotNull
    @Override
    public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }
}
