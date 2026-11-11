package com.forge.fl.parser;

import com.forge.fl.FLTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class FLParser implements PsiParser {
    @NotNull
    @Override
    public ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        PsiBuilder.Marker file = builder.mark();

        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();
            if (tokenType == null) {
                break;
            }

            if (tokenType == TokenType.WHITE_SPACE) {
                builder.advanceLexer();
                continue;
            }

            if (tokenType == FLTokenTypes.KEYWORD && ":".equals(builder.getTokenText())) {
                PsiBuilder.Marker def = builder.mark();
                builder.advanceLexer();

                if (!builder.eof()) {
                    IElementType nextType = builder.getTokenType();
                    if (nextType == FLTokenTypes.IDENTIFIER || nextType == FLTokenTypes.KEYWORD || nextType == FLTokenTypes.BOOLEAN || nextType == FLTokenTypes.WORD) {
                        PsiBuilder.Marker name = builder.mark();
                        builder.advanceLexer();
                        name.done(FLTokenTypes.WORD);
                    }
                }

                while (!builder.eof()) {
                    IElementType current = builder.getTokenType();
                    if (current == FLTokenTypes.RPAREN) {
                        PsiBuilder.Marker header = builder.mark();
                        builder.advanceLexer();
                        header.done(FLTokenTypes.HEADER);
                        continue;
                    }
                    if (current == FLTokenTypes.KEYWORD && ";".equals(builder.getTokenText())) {
                        builder.advanceLexer();
                        break;
                    }
                    builder.advanceLexer();
                }

                def.done(FLTokenTypes.DEFINITION);
                continue;
            }

            PsiBuilder.Marker marker = builder.mark();
            builder.advanceLexer();

            if (tokenType == FLTokenTypes.STRING || tokenType == FLTokenTypes.NUMBER || tokenType == FLTokenTypes.COMMENT) {
                marker.done(tokenType);
            } else if (tokenType == FLTokenTypes.IDENTIFIER || tokenType == FLTokenTypes.KEYWORD || tokenType == FLTokenTypes.BOOLEAN) {
                marker.done(FLTokenTypes.WORD);
            } else if (tokenType == FLTokenTypes.LBRACKET || tokenType == FLTokenTypes.RBRACKET
                    || tokenType == FLTokenTypes.LBRACE || tokenType == FLTokenTypes.RBRACE
                    || tokenType == FLTokenTypes.LPAREN || tokenType == FLTokenTypes.RPAREN) {
                marker.done(tokenType);
            } else {
                marker.error("Unexpected token");
            }
        }

        file.done(FLTokenTypes.FILE);
        return builder.getTreeBuilt();
    }
}
