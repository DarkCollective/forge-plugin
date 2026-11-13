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

    /**
     * One level, passed on to the children even when the block does not start a line: a quotation opened at the end
     * of a body line indents its contents one level deeper than that line.
     */
    private static final Indent ENFORCED = Indent.getIndent(Indent.Type.NORMAL, false, true);

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
                    childIndent = ENFORCED;
                } else if (isGroup(myNode.getElementType()) && child != myNode.getFirstChildNode()
                        && type != RBRACKET && type != RBRACE) {
                    // a line inside a multi-line [ ] or { } is one level deeper than the line that opens it;
                    // the closing bracket lines up with that line
                    childIndent = ENFORCED;
                } else if (myNode.getElementType() == FLElementTypes.USING_FORM && child != myNode.getFirstChildNode()) {
                    childIndent = Indent.getNormalIndent(); // the continuation lines of a long USING:
                } else if ((myNode.getElementType() == FLElementTypes.META_BLOCK && type == FLElementTypes.META_CLAUSE)
                        || (myNode.getElementType() == FLElementTypes.CHOICE_DECL && type == FLElementTypes.CHOICE_OPTION)) {
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
                // Between top-level forms exactly one blank line. A comment run directly above a definition or a
                // CHOICE: block is its documentation: one blank line before the run, none after it. Elsewhere
                // comments keep at most one blank line around them (section headings, licence header).
                if (isComment(right) && !isComment(left)) {
                    return isDocumented(nextNonComment(((ASTBlock) child2).getNode())) ? blankLine() : lineBreakKeepOne();
                }
                if (isComment(left)) return lineBreakKeepOne();
                // a run of one-line forms (CONSTANT, VARIABLE, PRIMITIVE, short definitions) may stay together
                if (isOneLine(((ASTBlock) child1).getNode()) && isOneLine(((ASTBlock) child2).getNode())) {
                    return lineBreakKeepOne();
                }
                return blankLine();
            }
            if (left == LINE_COMMENT) return lineBreak();
            // an end-of-line comment stays on its line, aligned as written (tables of data); one on a line of its
            // own stays there
            if (right == LINE_COMMENT) return Spacing.createSpacing(1, Integer.MAX_VALUE, 0, true, 0);
            if (parentType == FLElementTypes.DEFINITION) {
                if (left == FLElementTypes.HEADER) {
                    return right == SEMI ? space() : preserveLineBreak();
                }
                if (left == FLElementTypes.DOC_DECL || right == FLElementTypes.DOC_DECL) return lineBreak();
            }
            if (parentType == FLElementTypes.META_BLOCK || parentType == FLElementTypes.CHOICE_DECL) {
                if (left == META_KW || left == CHOICE_KW) return space();
                return lineBreak();
            }
            if (parentType == FLElementTypes.PARAMETER) return noSpace(); // name:type, name:choice<type>=x is one item
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

        /** A line break, keeping at most one blank line the author wrote. */
        private static Spacing lineBreakKeepOne() {
            return Spacing.createSpacing(0, 0, 1, true, 1);
        }

        /** A declaration or definition written on one line; those may form a run without blank lines. */
        private static boolean isOneLine(@Nullable ASTNode node) {
            if (node == null) return false;
            IElementType type = node.getElementType();
            boolean decl = type == FLElementTypes.CONSTANT_DECL || type == FLElementTypes.VARIABLE_DECL
                    || type == FLElementTypes.PRIMITIVE_DECL || type == FLElementTypes.DEFINITION;
            return decl && node.getText().indexOf('\n') < 0;
        }

        private static boolean isGroup(IElementType type) {
            return type == FLElementTypes.QUOTATION || type == FLElementTypes.LIST_LITERAL;
        }

        /** Forms whose comment run directly above them is their documentation. */
        private static boolean isDocumented(@Nullable IElementType type) {
            return type == FLElementTypes.DEFINITION || type == FLElementTypes.CHOICE_DECL;
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
