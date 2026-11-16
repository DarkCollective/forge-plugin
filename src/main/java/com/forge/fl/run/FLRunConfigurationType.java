// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** The "FL" run configuration type: runs FL code with the Forge engine (see {@link FLRunConfiguration}). */
public final class FLRunConfigurationType extends ConfigurationTypeBase {
    public static final String ID = "FLRunConfiguration";

    public FLRunConfigurationType() {
        super(ID, "FL", "Run FL code or a word with the Forge engine of a built Forge checkout",
                AllIcons.RunConfigurations.Application);
        addFactory(new Factory(this));
    }

    @NotNull
    public static FLRunConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(FLRunConfigurationType.class);
    }

    @NotNull
    public ConfigurationFactory getFactory() {
        return getConfigurationFactories()[0];
    }

    private static final class Factory extends ConfigurationFactory {
        Factory(FLRunConfigurationType type) {
            super(type);
        }

        @NotNull
        @Override
        public String getId() {
            return ID;
        }

        @NotNull
        @Override
        public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
            return new FLRunConfiguration(project, this, "FL");
        }
    }
}
