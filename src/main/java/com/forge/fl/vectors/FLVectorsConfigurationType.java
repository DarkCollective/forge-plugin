// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.vectors;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** The "FL Vectors" run configuration type: runs op test vectors (see {@link FLVectorsRunConfiguration}). */
public final class FLVectorsConfigurationType extends ConfigurationTypeBase {
    public static final String ID = "FLVectors";

    public FLVectorsConfigurationType() {
        super(ID, "FL Vectors", "Run the op test vectors (packages/ops/vocab/*.test.json) of a built Forge checkout",
                AllIcons.RunConfigurations.Junit);
        addFactory(new Factory(this));
    }

    @NotNull
    public static FLVectorsConfigurationType getInstance() {
        return ConfigurationTypeUtil.findConfigurationType(FLVectorsConfigurationType.class);
    }

    @NotNull
    public ConfigurationFactory getFactory() {
        return getConfigurationFactories()[0];
    }

    private static final class Factory extends ConfigurationFactory {
        Factory(FLVectorsConfigurationType type) {
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
            return new FLVectorsRunConfiguration(project, this, "FL Vectors");
        }
    }
}
