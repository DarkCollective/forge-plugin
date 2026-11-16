// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.vectors;

import com.intellij.execution.Executor;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The test console of an FL Vectors run: locations of files, ops and entries, and Rerun Failed Tests. */
final class FLVectorsConsoleProperties extends SMTRunnerConsoleProperties {
    private final FLVectorsRunConfiguration configuration;

    FLVectorsConsoleProperties(@NotNull FLVectorsRunConfiguration configuration, @NotNull Executor executor) {
        super(configuration, "FL Vectors", executor);
        this.configuration = configuration;
        setIdBasedTestTree(false);
    }

    @Nullable
    @Override
    public SMTestLocator getTestLocator() {
        return new FLVectorLocator(configuration.effectiveCheckout());
    }

    @Nullable
    @Override
    public AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
        return new FLVectorsRerunFailedAction(consoleView, this);
    }
}
