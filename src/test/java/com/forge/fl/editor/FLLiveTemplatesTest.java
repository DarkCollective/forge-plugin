// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.editor;

import com.forge.fl.FLLanguage;
import com.forge.fl.FLTestBase;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;


public class FLLiveTemplatesTest extends FLTestBase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    }

    private void finishTemplate() {
        TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
        if (state != null) WriteCommandAction.runWriteCommandAction(getProject(), () -> state.gotoEnd(false));
    }

    public void testDefinitionTemplateAtTopLevel() {
        myFixture.configureByText("t.fth", "IN: t\nwd<caret>\n");
        myFixture.type('\t');
        finishTemplate();
        myFixture.checkResult("IN: t\n: name ( s:str -- str )\n  <caret> ;\n");
    }

    public void testMetaAndChoiceTemplates() {
        myFixture.configureByText("t.fth", "IN: t\nchoice<caret>\n");
        myFixture.type('\t');
        finishTemplate();
        myFixture.checkResult("IN: t\nCHOICE: name\n  option option \"\" ;\n  <caret>\n;\n");
        myFixture.configureByText("u.fth", "IN: t\nmeta<caret>\n");
        myFixture.type('\t');
        finishTemplate();
        myFixture.checkResult("IN: t\nMETA: name\n  doc \"\" ;\n  category \"\" ;\n  <caret>\n;\n");
    }

    public void testBodyTemplates() {
        myFixture.configureByText("t.fth", "IN: t\n: f ( b:bool -- int ) b ife<caret> ;\n");
        myFixture.type('\t');
        finishTemplate();
        myFixture.checkResult("IN: t\n: f ( b:bool -- int ) b [  ] [  ] if<caret> ;\n");
    }

    public void testContexts() {
        PsiFile f = myFixture.configureByText("t.fth", "IN: t\nx\n: f ( -- ) y ;\n");
        String text = f.getText();
        int top = text.indexOf("x");
        int body = text.indexOf("y");
        int header = text.indexOf("--");
        assertTrue(new FLTemplateContexts.TopLevel().isInContext(TemplateActionContext.expanding(f, top)));
        assertFalse(new FLTemplateContexts.Body().isInContext(TemplateActionContext.expanding(f, top)));
        assertTrue(new FLTemplateContexts.Body().isInContext(TemplateActionContext.expanding(f, body)));
        assertFalse(new FLTemplateContexts.TopLevel().isInContext(TemplateActionContext.expanding(f, body)));
        assertFalse("not in the header", new FLTemplateContexts.Body().isInContext(TemplateActionContext.expanding(f, header)));
        assertTrue(new FLTemplateContexts.Any().isInContext(TemplateActionContext.expanding(f, header)));
    }

    public void testSurroundWithQuotationAndIf() {
        TemplateImpl q = TemplateSettings.getInstance().getTemplate("q", "FL");
        TemplateImpl ifs = TemplateSettings.getInstance().getTemplate("ifs", "FL");
        assertNotNull(q);
        assertNotNull(ifs);
        assertTrue(q.isSelectionTemplate());
        myFixture.configureByText("t.fth", "IN: t\n: f ( -- int ) <selection>1 2 +</selection> ;\n");
        new InvokeTemplateAction(q, myFixture.getEditor(), getProject(), new java.util.HashSet<>()).perform();
        finishTemplate();
        myFixture.checkResult("IN: t\n: f ( -- int ) [ 1 2 + ]<caret> ;\n");
        myFixture.configureByText("u.fth", "IN: t\n: f ( -- int ) <selection>1</selection> ;\n");
        new InvokeTemplateAction(ifs, myFixture.getEditor(), getProject(), new java.util.HashSet<>()).perform();
        finishTemplate();
        myFixture.checkResult("IN: t\n: f ( -- int ) true [ 1 ] [ <caret> ] if ;\n");
    }

    public void testRefactoringsAreRegistered() {
        RefactoringSupportProvider p = LanguageRefactoringSupport.INSTANCE.forLanguage(FLLanguage.INSTANCE);
        assertNotNull(p.getExtractMethodHandler());
        assertNotNull(p.getIntroduceVariableHandler());
        assertNotNull(p.getChangeSignatureHandler());
        assertTrue(InlineActionHandler.EP_NAME.getExtensionList().stream()
                .anyMatch(h -> h.isEnabledForLanguage(FLLanguage.INSTANCE)));
    }
}
