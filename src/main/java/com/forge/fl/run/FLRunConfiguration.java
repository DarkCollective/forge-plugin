// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs FL source text at the Forge console (docs/LANGUAGE.md section 9) in a fresh runtime built from a Forge
 * checkout: all bundled vocabularies from {@code packages/ops/vocab}, then the extra files as user units. The
 * program is any FL code, typically the inputs and a word: {@code "hello" standard true base32:encode}.
 */
public final class FLRunConfiguration extends RunConfigurationBase<Object> {
    private String checkout = "";
    private String nodePath = "";
    private String program = "";
    private String vocab = "";
    private List<String> extraFiles = new ArrayList<>();
    private int maxSteps;
    private int maxMillis;

    FLRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    @NotNull
    @Override
    public SettingsEditor<FLRunConfiguration> getConfigurationEditor() {
        return new FLRunSettingsEditor(getProject());
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        String problem = problem();
        if (problem != null) throw new RuntimeConfigurationError(problem);
    }

    /** What stops this configuration from running, or null. */
    @Nullable
    String problem() {
        Path dir = effectiveCheckout();
        if (dir == null) return "Choose the Forge checkout (a directory with packages/ops/vocab)";
        if (!FLForgeEnvironment.isCheckout(dir)) return dir + " is not a Forge checkout: it has no packages/ops/vocab";
        if (!FLForgeEnvironment.isBuilt(dir)) return "The Forge checkout is not built: run 'npm run build' in " + dir;
        if (effectiveNode() == null) return "Node.js was not found: install Node 20 or newer, or set its path";
        if (program.isBlank()) return "The FL program is empty";
        for (String f : extraFiles) {
            if (!Files.isRegularFile(Path.of(f))) return "Extra file not found: " + f;
        }
        return null;
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
        String problem = problem();
        if (problem != null) throw new ExecutionException(problem);
        FLForgeEnvironment.rememberCheckout(getProject(), effectiveCheckout().toString());
        return new FLRunState(environment, this);
    }

    /** The checkout as set, or the one found for the project when the field is empty. */
    @Nullable
    Path effectiveCheckout() {
        if (!checkout.isBlank()) return Path.of(checkout.strip());
        return FLForgeEnvironment.findCheckout(getProject(), null);
    }

    @Nullable
    String effectiveNode() {
        return nodePath.isBlank() ? FLForgeEnvironment.findNode() : nodePath.strip();
    }

    @Override
    public void readExternal(@NotNull Element element) {
        super.readExternal(element);
        checkout = StringUtil.notNullize(JDOMExternalizerUtil.readField(element, "CHECKOUT"));
        nodePath = StringUtil.notNullize(JDOMExternalizerUtil.readField(element, "NODE"));
        program = StringUtil.notNullize(JDOMExternalizerUtil.readField(element, "PROGRAM"));
        vocab = StringUtil.notNullize(JDOMExternalizerUtil.readField(element, "VOCAB"));
        extraFiles = new ArrayList<>(StringUtil.split(StringUtil.notNullize(JDOMExternalizerUtil.readField(element, "EXTRA_FILES")), "\n"));
        maxSteps = StringUtil.parseInt(JDOMExternalizerUtil.readField(element, "MAX_STEPS"), 0);
        maxMillis = StringUtil.parseInt(JDOMExternalizerUtil.readField(element, "MAX_MILLIS"), 0);
    }

    @Override
    public void writeExternal(@NotNull Element element) {
        super.writeExternal(element);
        JDOMExternalizerUtil.writeField(element, "CHECKOUT", checkout);
        JDOMExternalizerUtil.writeField(element, "NODE", nodePath);
        JDOMExternalizerUtil.writeField(element, "PROGRAM", program);
        JDOMExternalizerUtil.writeField(element, "VOCAB", vocab);
        JDOMExternalizerUtil.writeField(element, "EXTRA_FILES", String.join("\n", extraFiles));
        if (maxSteps > 0) JDOMExternalizerUtil.writeField(element, "MAX_STEPS", Integer.toString(maxSteps));
        if (maxMillis > 0) JDOMExternalizerUtil.writeField(element, "MAX_MILLIS", Integer.toString(maxMillis));
    }

    @Override
    public FLRunConfiguration clone() {
        FLRunConfiguration copy = (FLRunConfiguration) super.clone();
        copy.extraFiles = new ArrayList<>(extraFiles);
        return copy;
    }

    /** The files this configuration loads, by unit name. */
    @NotNull
    public FLUnitFiles unitFiles() {
        return new FLUnitFiles(getProject(), effectiveCheckout(), extraFiles);
    }

    /** The JSON request read by {@code forge-runner.mjs}, one line. */
    @NotNull
    String requestJson() {
        return requestJson(false);
    }

    /** The JSON request, for a debug session when {@code debug} is set. */
    @NotNull
    String requestJson(boolean debug) {
        Path dir = effectiveCheckout();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"checkout\":").append(json(dir == null ? "" : dir.toString()));
        sb.append(",\"program\":").append(json(program));
        if (!vocab.isBlank()) sb.append(",\"vocab\":").append(json(vocab.strip()));
        sb.append(",\"units\":[");
        for (int i = 0; i < extraFiles.size(); i++) {
            Path p = Path.of(extraFiles.get(i));
            if (i > 0) sb.append(',');
            sb.append("{\"name\":").append(json(p.getFileName().toString())).append(",\"path\":").append(json(p.toString())).append('}');
        }
        sb.append("],\"limits\":{");
        List<String> limits = new ArrayList<>();
        if (maxSteps > 0) limits.add("\"maxSteps\":" + maxSteps);
        if (maxMillis > 0) limits.add("\"maxMillis\":" + maxMillis);
        sb.append(String.join(",", limits)).append('}');
        if (debug) sb.append(",\"debug\":true");
        sb.append('}');
        return sb.toString();
    }

    static String json(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    public String getCheckout() {
        return checkout;
    }

    public void setCheckout(String checkout) {
        this.checkout = StringUtil.notNullize(checkout);
    }

    public String getNodePath() {
        return nodePath;
    }

    public void setNodePath(String nodePath) {
        this.nodePath = StringUtil.notNullize(nodePath);
    }

    public String getProgram() {
        return program;
    }

    public void setProgram(String program) {
        this.program = StringUtil.notNullize(program);
    }

    public String getVocab() {
        return vocab;
    }

    public void setVocab(String vocab) {
        this.vocab = StringUtil.notNullize(vocab);
    }

    public List<String> getExtraFiles() {
        return extraFiles;
    }

    public void setExtraFiles(List<String> extraFiles) {
        this.extraFiles = new ArrayList<>(extraFiles);
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = Math.max(0, maxSteps);
    }

    public int getMaxMillis() {
        return maxMillis;
    }

    public void setMaxMillis(int maxMillis) {
        this.maxMillis = Math.max(0, maxMillis);
    }
}
