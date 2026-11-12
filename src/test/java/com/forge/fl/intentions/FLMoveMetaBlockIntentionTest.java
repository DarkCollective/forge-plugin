// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.intentions;

import com.forge.fl.FLTestBase;
import com.forge.fl.diagnostics.FLMetaPlacementInspection;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.actions.CodeCleanupCodeProcessor;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;

public class FLMoveMetaBlockIntentionTest extends FLTestBase {
    public void testMovesMetaBlockAfterItsDefinition() {
        myFixture.configureByText("move.fth", "IN: demo\n"
                + ": target ( -- ) ;\n"
                + ": other ( -- ) ;\n"
                + "<caret>META: target\n  doc \"description\" ;\n;\n");
        IntentionAction action = myFixture.findSingleIntention("Move META block next to definition");
        myFixture.launchAction(action);
        myFixture.checkResult("IN: demo\n"
                + ": target ( -- ) ;\n\nMETA: target\n  doc \"description\" ;\n;\n"
                + ": other ( -- ) ;\n");
    }

    public void testIntentionIsNotAvailableWhenMetaIsAlreadyNextToDefinition() {
        myFixture.configureByText("move.fth", "IN: demo\n"
                + ": target ( -- ) ;\n"
                + "<caret>META: target\n  doc \"description\" ;\n;\n");
        assertEmpty(myFixture.filterAvailableIntentions("Move META block next to definition"));
    }

    public void testIntentionIsNotAvailableForUnresolvedOrNonDefinitionTargets() {
        myFixture.configureByText("move.fth", "<caret>META: missing\n  doc \"description\" ;\n;\n");
        assertEmpty(myFixture.filterAvailableIntentions("Move META block next to definition"));

        myFixture.configureByText("move.fth", "256 CONSTANT target\n"
                + "<caret>META: target\n  doc \"description\" ;\n;\n");
        assertEmpty(myFixture.filterAvailableIntentions("Move META block next to definition"));
    }

    public void testCleanupInspectionReportsAndFixesMisplacedMetaBlock() {
        myFixture.enableInspections(new FLMetaPlacementInspection());
        myFixture.configureByText("cleanup.fth", "IN: demo\n"
                + ": target ( -- ) ;\n"
                + ": other ( -- ) ;\n"
                + "<caret>META: target\n  doc \"description\" ;\n;\n");
        myFixture.doHighlighting();
        myFixture.launchAction(myFixture.findSingleIntention("Move META block next to definition (Code Cleanup)"));
        myFixture.checkResult("IN: demo\n"
                + ": target ( -- ) ;\n\n"
                + "META: target\n  doc \"description\" ;\n;\n"
                + ": other ( -- ) ;\n");
    }

    public void testCodeCleanupProcessorMovesMisplacedMetaBlock() throws Exception {
        myFixture.enableInspections(new FLMetaPlacementInspection());
        PsiFile file = myFixture.configureByText("cleanup.fth", "IN: demo\n"
                + ": target ( -- ) ;\n"
                + ": other ( -- ) ;\n"
                + "META: target\n  doc \"description\" ;\n;\n\n\n");
        var profile = InspectionProjectProfileManager.getInstance(getProject()).getCurrentProfile();
        var tool = profile.getToolById("FLMetaPlacement", file);
        assertNotNull(tool);
        assertTrue(tool.isCleanupTool());
        assertTrue(profile.isToolEnabled(HighlightDisplayKey.find("FLMetaPlacement"), file));
        new CodeCleanupCodeProcessor(getProject(), new PsiFile[]{file}, null, false)
                .processFilesUnderProgress(new EmptyProgressIndicator());
        myFixture.checkResult("IN: demo\n"
                + ": target ( -- ) ;\n\n"
                + "META: target\n  doc \"description\" ;\n;\n"
                + ": other ( -- ) ;\n");
    }
}
