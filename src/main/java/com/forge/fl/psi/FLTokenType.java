package com.forge.fl.psi;

import com.forge.fl.FLLanguage;
import com.intellij.psi.tree.IElementType;

public class FLTokenType extends IElementType {
    public FLTokenType(String debugName) {
        super(debugName, FLLanguage.INSTANCE);
    }
}
