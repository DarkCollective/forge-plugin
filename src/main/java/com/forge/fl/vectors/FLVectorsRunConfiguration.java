// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.vectors;

import com.forge.fl.run.FLForgeEnvironment;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Runs op test vectors of a built Forge checkout with {@code forge-vectors.mjs}, shown in the test runner window.
 * What runs: the given files of {@code packages/ops/vocab} (all when none), narrowed to the entries of one op, and to
 * given entries (array indices) per file.
 */
public final class FLVectorsRunConfiguration extends RunConfigurationBase<Object> {
    private String checkout = "";
    private String nodePath = "";
    private List<String> files = new ArrayList<>();
    private String op = "";
    private Map<String, List<Integer>> entries = new LinkedHashMap<>();

    FLVectorsRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    @NotNull
    @Override
    public SettingsEditor<FLVectorsRunConfiguration> getConfigurationEditor() {
        return new FLVectorsSettingsEditor(getProject());
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
        if (!FLForgeEnvironment.isBuiltForVectors(dir)) {
            return "The Forge checkout is not built (packages worker, recipe, ops and lang): run 'npm run build' in " + dir;
        }
        if (effectiveNode() == null) return "Node.js was not found: install Node 20 or newer, or set its path";
        for (String f : files) {
            if (!f.endsWith(FLVectorFiles.SUFFIX)) return "Not a vectors file: " + f + " (expected <vocab>" + FLVectorFiles.SUFFIX + ")";
            if (!Files.isRegularFile(FLForgeEnvironment.vocabDir(dir).resolve(f))) return "No such vectors file: " + f;
        }
        return null;
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
        String problem = problem();
        if (problem != null) throw new ExecutionException(problem);
        return new FLVectorsRunState(environment, this);
    }

    @Nullable
    Path effectiveCheckout() {
        if (!checkout.isBlank()) return Path.of(checkout.strip());
        return FLForgeEnvironment.findCheckout(getProject(), null);
    }

    @Nullable
    String effectiveNode() {
        return nodePath.isBlank() ? FLForgeEnvironment.findNode() : nodePath.strip();
    }

    /** The request read by {@code forge-vectors.mjs}. */
    @NotNull
    String requestJson() {
        Path dir = effectiveCheckout();
        JsonObject r = new JsonObject();
        r.addProperty("checkout", dir == null ? "" : dir.toString());
        JsonArray fs = new JsonArray();
        for (String f : files) fs.add(f);
        r.add("files", fs);
        if (!op.isBlank()) r.addProperty("op", op.strip());
        if (!entries.isEmpty()) {
            JsonObject es = new JsonObject();
            for (var e : entries.entrySet()) {
                JsonArray idx = new JsonArray();
                for (int i : e.getValue()) idx.add(i);
                es.add(e.getKey(), idx);
            }
            r.add("entries", es);
        }
        return r.toString();
    }

    /** A name for what this configuration runs: "base32:encode vectors", "base32 #3", "Forge vectors". */
    @NotNull
    String suggestedName() {
        if (files.size() == 1) {
            String vocab = FLVectorFiles.vocabOf(files.get(0));
            List<Integer> only = entries.get(files.get(0));
            if (only != null && only.size() == 1) return vocab + " #" + only.get(0);
            if (only != null) return vocab + " vectors (" + only.size() + " entries)";
            if (!op.isBlank()) return op.strip() + " vectors";
            return vocab + " vectors";
        }
        if (!op.isBlank()) return op.strip() + " vectors";
        return files.isEmpty() ? "Forge vectors" : files.size() + " vector files";
    }

    @Override
    public void readExternal(@NotNull Element element) {
        super.readExternal(element);
        checkout = StringUtil.notNullize(JDOMExternalizerUtil.readField(element, "CHECKOUT"));
        nodePath = StringUtil.notNullize(JDOMExternalizerUtil.readField(element, "NODE"));
        files = new ArrayList<>(StringUtil.split(StringUtil.notNullize(JDOMExternalizerUtil.readField(element, "FILES")), ","));
        op = StringUtil.notNullize(JDOMExternalizerUtil.readField(element, "OP"));
        entries = parseEntries(StringUtil.notNullize(JDOMExternalizerUtil.readField(element, "ENTRIES")));
    }

    @Override
    public void writeExternal(@NotNull Element element) {
        super.writeExternal(element);
        JDOMExternalizerUtil.writeField(element, "CHECKOUT", checkout);
        JDOMExternalizerUtil.writeField(element, "NODE", nodePath);
        JDOMExternalizerUtil.writeField(element, "FILES", String.join(",", files));
        JDOMExternalizerUtil.writeField(element, "OP", op);
        if (!entries.isEmpty()) JDOMExternalizerUtil.writeField(element, "ENTRIES", formatEntries(entries));
    }

    /** {@code "a.test.json:1,2;b.test.json:7"}. */
    @NotNull
    static String formatEntries(@NotNull Map<String, List<Integer>> entries) {
        List<String> parts = new ArrayList<>();
        for (var e : entries.entrySet()) {
            parts.add(e.getKey() + ":" + String.join(",", e.getValue().stream().map(String::valueOf).toList()));
        }
        return String.join(";", parts);
    }

    @NotNull
    static Map<String, List<Integer>> parseEntries(@NotNull String text) {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        for (String part : StringUtil.split(text, ";")) {
            int colon = part.lastIndexOf(':');
            if (colon <= 0) continue;
            TreeSet<Integer> idx = new TreeSet<>();
            for (String n : StringUtil.split(part.substring(colon + 1), ",")) {
                int i = StringUtil.parseInt(n.strip(), -1);
                if (i >= 0) idx.add(i);
            }
            if (!idx.isEmpty()) out.put(part.substring(0, colon).strip(), new ArrayList<>(idx));
        }
        return out;
    }

    @Override
    public FLVectorsRunConfiguration clone() {
        FLVectorsRunConfiguration copy = (FLVectorsRunConfiguration) super.clone();
        copy.files = new ArrayList<>(files);
        copy.entries = new LinkedHashMap<>();
        entries.forEach((k, v) -> copy.entries.put(k, new ArrayList<>(v)));
        return copy;
    }

    /** Fills an empty checkout field: the checkout around {@code forFile}, the project's, or the one used last. */
    void defaultCheckout(@Nullable Path forFile) {
        if (!checkout.isBlank()) return;
        Path found = FLForgeEnvironment.findCheckout(getProject(), forFile);
        if (found != null) checkout = found.toString();
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

    public List<String> getFiles() {
        return files;
    }

    public void setFiles(List<String> files) {
        this.files = new ArrayList<>(files);
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = StringUtil.notNullize(op);
    }

    public Map<String, List<Integer>> getEntries() {
        return entries;
    }

    public void setEntries(Map<String, List<Integer>> entries) {
        this.entries = new LinkedHashMap<>();
        entries.forEach((k, v) -> this.entries.put(k, new ArrayList<>(new TreeSet<>(v))));
    }
}
