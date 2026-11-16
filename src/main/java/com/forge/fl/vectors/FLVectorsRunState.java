// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.vectors;

import com.forge.fl.run.FLForgeEnvironment;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Starts {@code node forge-vectors.mjs}, sends the request and shows its test events in a test runner console. */
final class FLVectorsRunState extends CommandLineState {
    private final FLVectorsRunConfiguration configuration;

    FLVectorsRunState(@NotNull ExecutionEnvironment environment, @NotNull FLVectorsRunConfiguration configuration) {
        super(environment);
        this.configuration = configuration;
    }

    @NotNull
    @Override
    public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
        ProcessHandler handler = startProcess();
        FLVectorsConsoleProperties properties = new FLVectorsConsoleProperties(configuration, executor);
        ConsoleView console = SMTestRunnerConnectionUtil.createAndAttachConsole("FL Vectors", handler, properties);
        DefaultExecutionResult result = new DefaultExecutionResult(console, handler, createActions(console, handler, executor));
        AbstractRerunFailedTestsAction rerun = properties.createRerunFailedTestsAction(console);
        if (rerun != null && console instanceof SMTRunnerConsoleView view) {
            rerun.setModelProvider(view::getResultsViewer);
            result.setRestartActions(rerun);
        }
        return result;
    }

    @NotNull
    @Override
    protected ProcessHandler startProcess() throws ExecutionException {
        // The runner reads the vector files and the vocabularies from disk.
        ApplicationManager.getApplication().invokeAndWait(() -> FileDocumentManager.getInstance().saveAllDocuments());
        Path script;
        try {
            script = FLForgeEnvironment.vectorsScript();
        } catch (IOException e) {
            throw new ExecutionException("Cannot prepare the vector runner: " + e.getMessage(), e);
        }
        String node = configuration.effectiveNode();
        if (node == null) throw new ExecutionException("Node.js was not found: install Node 20 or newer, or set its path in the run configuration");
        Path checkout = configuration.effectiveCheckout();
        GeneralCommandLine cmd = new GeneralCommandLine(node, script.toString())
                .withCharset(StandardCharsets.UTF_8)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
        if (checkout != null) cmd.withWorkDirectory(checkout.toFile());
        KillableColoredProcessHandler handler = new KillableColoredProcessHandler(cmd);
        ProcessTerminatedListener.attach(handler);
        try (OutputStream in = handler.getProcess().getOutputStream()) {
            in.write(configuration.requestJson().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            handler.destroyProcess();
            throw new ExecutionException("Cannot send the request to the vector runner: " + e.getMessage(), e);
        }
        return handler;
    }
}
