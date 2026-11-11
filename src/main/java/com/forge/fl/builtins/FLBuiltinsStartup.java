package com.forge.fl.builtins;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.EmptyRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * Makes sure the built-in declaration files are in the VFS (a refresh is not allowed inside the roots provider's
 * read action). If the provider ran before they were visible, the project roots are announced as changed so the
 * library is picked up and indexed.
 */
public class FLBuiltinsStartup implements StartupActivity.Background {
    @Override
    public void runActivity(@NotNull Project project) {
        FLBuiltins.prepare();
        if (FLBuiltins.consumeMissedRoot()) {
            // A roots change needs a write action on the EDT; this activity runs on a background thread.
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed()) return;
                WriteAction.run(() -> ProjectRootManagerEx.getInstanceEx(project)
                        .makeRootsChange(EmptyRunnable.INSTANCE, false, true));
            });
        }
    }
}
