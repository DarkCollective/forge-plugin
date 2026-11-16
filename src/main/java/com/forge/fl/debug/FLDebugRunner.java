// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.debug;

import com.forge.fl.run.FLDebugProcessHandler;
import com.forge.fl.run.FLRunConfiguration;
import com.forge.fl.run.FLRunState;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Debug for the FL run configuration: starts the runner in debug mode and an XDebugger session over it. */
public final class FLDebugRunner extends GenericProgramRunner<com.intellij.execution.configurations.RunnerSettings> {
    @NotNull
    @Override
    public String getRunnerId() {
        return "FLDebugRunner";
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && profile instanceof FLRunConfiguration;
    }

    @Nullable
    @Override
    protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment)
            throws ExecutionException {
        FLRunState flState = (FLRunState) state;
        FLDebugProcessHandler handler = flState.startDebugProcess();
        ConsoleView console = flState.getConsoleBuilder().getConsole();
        console.attachToProcess(handler);
        XDebugSession session = XDebuggerManager.getInstance(environment.getProject()).startSession(environment,
                new XDebugProcessStarter() {
                    @NotNull
                    @Override
                    public XDebugProcess start(@NotNull XDebugSession session) {
                        return new FLDebugProcess(session, handler, console, flState.getConfiguration());
                    }
                });
        handler.startNotify();
        return session.getRunContentDescriptor();
    }
}
