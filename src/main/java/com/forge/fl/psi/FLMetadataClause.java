package com.forge.fl.psi;

import com.intellij.psi.PsiElement;

public interface FLMetadataClause extends PsiElement {
    String getClauseName();
    String getClauseValue();
}
