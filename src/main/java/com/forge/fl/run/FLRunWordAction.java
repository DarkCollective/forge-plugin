// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.forge.fl.psi.FLDefinition;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Asks for the inputs of a word, then runs it through a temporary FL run configuration named after the word. */
final class FLRunWordAction extends AnAction {
    private final SmartPsiElementPointer<FLDefinition> pointer;

    FLRunWordAction(@NotNull FLDefinition def) {
        super("Run '" + FLRunInputs.qualifiedName(def) + "'…", "Run the word with inputs you enter", AllIcons.Actions.Execute);
        pointer = SmartPointerManager.createPointer(def);
    }

    @NotNull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        FLDefinition def = pointer.getElement();
        Project project = e.getProject();
        if (def == null || project == null) return;
        String qname = FLRunInputs.qualifiedName(def);
        String vocab = FLRunInputs.vocabOf(def);
        List<FLRunInputs.Slot> slots = FLRunInputs.slots(def);
        String program;
        if (slots != null && slots.isEmpty()) {
            program = qname;
        } else {
            FLRunWordDialog dialog = new FLRunWordDialog(project, qname, slots);
            if (!dialog.showAndGet()) return;
            program = dialog.program();
        }
        VirtualFile vf = def.getContainingFile().getVirtualFile();
        Path file = vf == null || !vf.isInLocalFileSystem() ? null : vf.toNioPath();
        Path checkout = FLForgeEnvironment.findCheckout(project, file);
        if (checkout == null) {
            Messages.showErrorDialog(project, "No Forge checkout was found for this file. Create an FL run configuration and set its Forge checkout.",
                    "Run " + qname);
            return;
        }
        ProgramRunnerUtil.executeConfiguration(configure(project, qname, program, vocab, checkout, file),
                DefaultRunExecutor.getRunExecutorInstance());
    }

    /** Finds or creates the temporary configuration named after the word and fills it in. */
    static RunnerAndConfigurationSettings configure(@NotNull Project project, @NotNull String qname, @NotNull String program,
                                                    @NotNull String vocab, @NotNull Path checkout, @Nullable Path file) {
        RunManager rm = RunManager.getInstance(project);
        FLRunConfigurationType type = FLRunConfigurationType.getInstance();
        RunnerAndConfigurationSettings settings = null;
        for (RunnerAndConfigurationSettings s : rm.getConfigurationSettingsList(type)) {
            if (s.getName().equals(qname)) settings = s;
        }
        if (settings == null) {
            settings = rm.createConfiguration(qname, type.getFactory());
            rm.setTemporaryConfiguration(settings);
        }
        FLRunConfiguration c = (FLRunConfiguration) settings.getConfiguration();
        c.setProgram(program);
        c.setVocab(vocab);
        if (c.getCheckout().isBlank()) c.setCheckout(checkout.toString());
        // A file outside the checkout's vocabulary directory is not in the bundle: load it as a user unit.
        List<String> extra = new ArrayList<>(c.getExtraFiles());
        if (file != null && !file.startsWith(FLForgeEnvironment.vocabDir(Path.of(c.getCheckout())))
                && !extra.contains(file.toString())) {
            extra.add(file.toString());
        }
        c.setExtraFiles(extra);
        rm.setSelectedConfiguration(settings);
        return settings;
    }
}
