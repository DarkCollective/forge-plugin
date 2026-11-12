// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.formatting;

import com.forge.fl.psi.FLElementTypes;
import com.intellij.formatting.ASTBlock;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.ChildAttributes;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Spacing;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.forge.fl.FLTokenTypes.*;

public final class FLFormattingModelBuilder implements FormattingModelBuilder {
    @NotNull
    @Override
    public FormattingModel createModel(@NotNull FormattingContext context) {
        return FormattingModelProvider.createFormattingModelForPsiFile(context.getContainingFile(),
                new FLBlock(context.getNode(), null, Indent.getNoneIndent()), context.getCodeStyleSettings());
    }

    private static final class FLBlock extends AbstractBlock {
        private final Indent indent;
        FLBlock(@NotNull ASTNode node, @Nullable Wrap wrap, @NotNull Indent indent) {
            super(node, wrap, null);
            this.indent = indent;
        }

        @Override
        protected List<Block> buildChildren() {
            List<Block> result = new ArrayList<>();
            boolean inDefinitionBody = false;
            for (ASTNode child = myNode.getFirstChildNode(); child != null; child = child.getTreeNext()) {
                if (child.getElementType() == com.intellij.psi.TokenType.WHITE_SPACE) continue;
                IElementType type = child.getElementType();
                Indent childIndent = Indent.getNoneIndent();
                if (myNode.getElementType() == FLElementTypes.DEFINITION && inDefinitionBody) {
                    childIndent = Indent.getNormalIndent();
                } else if (myNode.getElementType() == FLElementTypes.META_BLOCK
                        && type == FLElementTypes.META_CLAUSE) {
                    childIndent = Indent.getNormalIndent();
                }
                result.add(new FLBlock(child, null, childIndent));
                if (myNode.getElementType() == FLElementTypes.DEFINITION && type == FLElementTypes.HEADER) {
                    inDefinitionBody = true;
                }
            }
            return result;
        }

        @Override
        public Indent getIndent() {
            return indent;
        }

        @Override
        public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
            if (child1 == null) return Spacing.getReadOnlySpacing();
            IElementType parentType = myNode.getElementType();
            IElementType left = ((ASTBlock) child1).getNode().getElementType();
            IElementType right = ((ASTBlock) child2).getNode().getElementType();

            if (parentType == FLElementTypes.FILE) {
                if (isComment(right)) {
                    return nextNonComment(((ASTBlock) child2).getNode()) == FLElementTypes.DEFINITION
                            && !isComment(left) ? blankLine() : lineBreak();
                }
                if (isComment(left)) return lineBreak();
                return blankLine();
            }
            if (left == LINE_COMMENT || right == LINE_COMMENT) return lineBreak();
            if (parentType == FLElementTypes.DEFINITION) {
                if (left == FLElementTypes.HEADER) {
                    return right == SEMI ? space() : preserveLineBreak();
                }
                if (left == FLElementTypes.DOC_DECL || right == FLElementTypes.DOC_DECL) return lineBreak();
            }
            if (parentType == FLElementTypes.META_BLOCK) {
                if (left == META_KW) return space();
                return lineBreak();
            }
            if (parentType == FLElementTypes.PARAMETER
                    && left == HDR_PARAM_NAME && right == HDR_PARAM_REST) return noSpace();
            return space();
        }

        @Override
        public @Nullable Wrap getWrap() {
            return null;
        }

        @Override
        public @Nullable Alignment getAlignment() {
            return null;
        }

        @Override
        public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
            return new ChildAttributes(Indent.getNoneIndent(), null);
        }

        @Override
        public boolean isLeaf() {
            return myNode.getFirstChildNode() == null;
        }

        private static Spacing space() {
            return Spacing.createSpacing(1, 1, 0, true, 0);
        }

        private static Spacing noSpace() {
            return Spacing.createSpacing(0, 0, 0, true, 0);
        }

        private static Spacing lineBreak() {
            return Spacing.createSpacing(0, 0, 1, true, 0);
        }

        private static Spacing preserveLineBreak() {
            return Spacing.createSpacing(1, 1, 0, true, 0);
        }

        private static Spacing blankLine() {
            return Spacing.createSpacing(0, 0, 2, true, 0);
        }

        private static boolean isComment(IElementType type) {
            return type == LINE_COMMENT || type == BLOCK_COMMENT;
        }

        @Nullable
        private static IElementType nextNonComment(ASTNode node) {
            for (ASTNode next = node.getTreeNext(); next != null; next = next.getTreeNext()) {
                IElementType type = next.getElementType();
                if (type == com.intellij.psi.TokenType.WHITE_SPACE || isComment(type)) continue;
                return type;
            }
            return null;
        }
    }
}
