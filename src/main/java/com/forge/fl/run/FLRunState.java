// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Starts {@code node forge-runner.mjs} and writes the request to its stdin. */
public final class FLRunState extends CommandLineState {
    private final FLRunConfiguration configuration;

    FLRunState(@NotNull ExecutionEnvironment environment, @NotNull FLRunConfiguration configuration) {
        super(environment);
        this.configuration = configuration;
        TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(environment.getProject());
        builder.addFilter(new FLRunConsoleFilter(environment.getProject(), configuration.unitFiles()));
        setConsoleBuilder(builder);
    }

    @NotNull
    public FLRunConfiguration getConfiguration() {
        return configuration;
    }

    @NotNull
    @Override
    protected ProcessHandler startProcess() throws ExecutionException {
        KillableColoredProcessHandler handler = new KillableColoredProcessHandler(commandLine());
        ProcessTerminatedListener.attach(handler);
        try (OutputStream in = handler.getProcess().getOutputStream()) {
            in.write(configuration.requestJson().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            handler.destroyProcess();
            throw new ExecutionException("Cannot send the request to the FL runner: " + e.getMessage(), e);
        }
        return handler;
    }

    /**
     * Starts the runner in debug mode: the request is the first line of its stdin, which stays open for the
     * debugger's commands (see forge-runner.mjs).
     */
    @NotNull
    public FLDebugProcessHandler startDebugProcess() throws ExecutionException {
        FLDebugProcessHandler handler = new FLDebugProcessHandler(commandLine());
        ProcessTerminatedListener.attach(handler);
        if (!handler.send(configuration.requestJson(true))) {
            handler.destroyProcess();
            throw new ExecutionException("Cannot send the request to the FL runner");
        }
        return handler;
    }

    private GeneralCommandLine commandLine() throws ExecutionException {
        // The runner reads the files from disk: a fresh runtime per run sees exactly the edited sources (early
        // binding, docs/LANGUAGE.md 6.5, rules out reloading one file into a long-lived runtime).
        ApplicationManager.getApplication().invokeAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());
        Path script;
        try {
            script = FLForgeEnvironment.runnerScript();
        } catch (IOException e) {
            throw new ExecutionException("Cannot prepare the FL runner: " + e.getMessage(), e);
        }
        String node = configuration.effectiveNode();
        if (node == null) throw new ExecutionException("Node.js was not found: install Node 20 or newer, or set its path in the run configuration");
        Path checkout = configuration.effectiveCheckout();
        GeneralCommandLine cmd = new GeneralCommandLine(node, script.toString())
                .withCharset(StandardCharsets.UTF_8)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
        if (checkout != null) cmd.withWorkDirectory(checkout.toFile());
        return cmd;
    }
}
