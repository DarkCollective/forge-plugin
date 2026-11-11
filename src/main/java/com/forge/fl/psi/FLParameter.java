package com.forge.fl.psi;

import com.forge.fl.FLTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** A named input of a header: {@code pname:type...} or a bare label before {@code --}. */
public class FLParameter extends FLNamedElement {
    public FLParameter(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    protected IElementType nameTokenType() {
        return FLTokenTypes.HDR_PARAM_NAME;
    }

    @Override
    public String getKindName() {
        return "parameter";
    }

    @Override
    protected PsiElement createNameLeaf(String name) {
        return FLElementFactory.createParameterName(getProject(), name);
    }

    /**
     * A parameter is local to its definition. The {@code param} clauses of META: blocks that target the
     * definition live outside it, so those blocks are part of the scope too.
     */
    @NotNull
    @Override
    public SearchScope getUseScope() {
        FLDefinition def = PsiTreeUtil.getParentOfType(this, FLDefinition.class);
        if (def == null) return super.getUseScope();
        List<PsiElement> scope = new ArrayList<>();
        scope.add(def);
        for (FLMetaBlock block : PsiTreeUtil.getChildrenOfTypeAsList(getContainingFile(), FLMetaBlock.class)) {
            FLWordRef target = block.getTarget();
            if (target != null && target.getReference() != null && target.getReference().resolve() == def) {
                scope.add(block);
            }
        }
        return new LocalSearchScope(scope.toArray(PsiElement.EMPTY_ARRAY));
    }

    @Override
    public String getLocationText() {
        FLDefinition def = PsiTreeUtil.getParentOfType(this, FLDefinition.class);
        return def == null ? null : def.getName();
    }
}
