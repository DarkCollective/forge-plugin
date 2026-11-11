package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The stack-effect header {@code ( inputs -- outputs )}. */
public class FLHeader extends FLPsiElement {
    public FLHeader(@NotNull ASTNode node) {
        super(node);
    }

    /** Named inputs and bare labels before {@code --}; not bare type names and not outputs. */
    @NotNull
    public List<FLParameter> getParameters() {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, FLParameter.class);
    }

    /** Whether the header has its {@code --} item. */
    public boolean hasArrow() {
        return getNode().findChildByType(FLTokenTypes.HDR_ARROW) != null;
    }

    /**
     * The input slots in order, as the compiler counts them (docs/LANGUAGE.md 12.1): a named input is its
     * {@link FLParameter}, an anonymous input (a bare type name) is {@code null}; a leading {@code ..} is no slot.
     * Returns {@code null} when the inputs are not fully understood (an item that fits no category, or no
     * {@code --}), so that callers can stay silent instead of guessing.
     */
    @Nullable
    public List<FLParameter> inputSlots() {
        List<FLParameter> slots = new ArrayList<>();
        boolean arrow = false;
        for (PsiElement c = getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof FLParameter p) {
                if (!arrow) slots.add(p);
                continue;
            }
            var t = c.getNode().getElementType();
            if (t == FLTokenTypes.HDR_ARROW) {
                arrow = true;
            } else if (!arrow) {
                if (t == FLTokenTypes.HDR_TYPE) slots.add(null);
                else if (t == FLTokenTypes.HDR_OTHER || t == FLTokenTypes.HDR_PARAM_REST) return null;
            }
        }
        return arrow ? slots : null;
    }
}
