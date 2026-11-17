// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.ui;

import org.junit.Test;

/**
 * Forge FL is not reloaded without a restart: IntelliJ asks for a restart when it is updated. (When it was reloadable,
 * a debugger expression editor left a fragment file of the old plugin that failed against the new classes, logging
 * "Language FL doesn't participate in view provider" in the IDE log.)
 */
public class FLReloadUiTest {
    @Test
    public void reload() throws Exception {
        Ide ide = new Ide();
        ide.waitFor("the project", "project() != null", 180);
        ide.waitFor("indexing", "!com.intellij.openapi.project.DumbService.isDumb(project())", 300);
        String plugin = "var d = com.intellij.ide.plugins.PluginManagerCore.getPlugin(com.intellij.openapi.extensions.PluginId.getId('com.forge.fl'));";
        String restartFree = ide.call(plugin + "String(com.intellij.ide.plugins.DynamicPlugins.INSTANCE.allowLoadUnloadWithoutRestart(d))");
        System.out.println("UI-RELOAD restart-free=" + restartFree);
        org.junit.Assert.assertEquals("an update must ask for a restart", "false", restartFree);
        if (!"true".equals(restartFree)) return;
        // a debugger expression editor's FL fragment, as Evaluate and Watches create it
        ide.run("""
                var f = com.intellij.psi.PsiFileFactory.getInstance(project()).createFileFromText('fragment.fl', com.intellij.lang.Language.findLanguageByID('FL'), '', true, false);
                var doc = com.intellij.psi.PsiDocumentManager.getInstance(project()).getDocument(f);
                var ed = com.intellij.openapi.editor.EditorFactory.getInstance().createEditor(doc, project());
                java.lang.System.setProperty('fl.reload.vf', f.getVirtualFile().getUrl());
                java.lang.System.getProperties().put('fl.reload.file', f.getVirtualFile());
                """);
        String result = ide.call(plugin + """
                var ok = com.intellij.ide.plugins.DynamicPlugins.INSTANCE.unloadPlugin(d);
                var loaded = false; var err = '';
                if (ok) {
                  var fresh = com.intellij.ide.plugins.PluginDescriptorLoader.loadDescriptor(d.getPluginPath(), false, com.intellij.ide.plugins.PluginXmlPathResolver.DEFAULT_PATH_RESOLVER);
                  loaded = com.intellij.ide.plugins.DynamicPlugins.INSTANCE.loadPlugin(fresh);
                }
                var vf = java.lang.System.getProperties().get('fl.reload.file');
                try {
                  com.intellij.psi.PsiManager.getInstance(project()).dropPsiCaches();
                  com.intellij.psi.impl.PsiManagerEx.getInstanceEx(project()).getFileManager().setViewProvider(vf, null);
                  var f2 = com.intellij.psi.PsiManager.getInstance(project()).findFile(vf);
                  if (f2 != null) f2.getLanguage();
                } catch (e) { err = String(e); }
                'unloaded=' + ok + ' loaded=' + loaded + ' findFile=' + (err == '' ? 'ok' : err);
                """);
        System.out.println("UI-RELOAD " + result);
    }
}
