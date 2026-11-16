// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/** Where the Forge checkout, Node.js and the runner script are. */
public final class FLForgeEnvironment {
    /** Project-level memory of the last Forge checkout used, so that new run configurations start with it. */
    static final String LAST_CHECKOUT_KEY = "com.forge.fl.run.checkout";

    private FLForgeEnvironment() {
    }

    /** Whether {@code dir} looks like a Forge checkout: it has {@code packages/ops/vocab}. */
    public static boolean isCheckout(@Nullable Path dir) {
        return dir != null && Files.isDirectory(dir.resolve("packages").resolve("ops").resolve("vocab"));
    }

    /** Whether the checkout has been built ({@code npm run build}), which the runner needs. */
    public static boolean isBuilt(@NotNull Path checkout) {
        return Files.isRegularFile(checkout.resolve("packages/ops/dist/index.js"))
                && Files.isRegularFile(checkout.resolve("packages/lang/dist/index.js"));
    }

    /** {@code <checkout>/packages/ops/vocab}: the bundled vocabulary sources, which the runner loads itself. */
    @NotNull
    public static Path vocabDir(@NotNull Path checkout) {
        return checkout.resolve("packages").resolve("ops").resolve("vocab");
    }

    /**
     * The Forge checkout for a file: the nearest enclosing directory that is a checkout, then the project base
     * directory, then the checkout last used in this project. Null when none is known.
     */
    @Nullable
    public static Path findCheckout(@NotNull Project project, @Nullable Path file) {
        for (Path p = file; p != null; p = p.getParent()) {
            if (isCheckout(p)) return p;
        }
        String base = project.getBasePath();
        if (base != null && isCheckout(Path.of(base))) return Path.of(base);
        String last = PropertiesComponent.getInstance(project).getValue(LAST_CHECKOUT_KEY);
        if (last != null && isCheckout(Path.of(last))) return Path.of(last);
        return null;
    }

    static void rememberCheckout(@NotNull Project project, @NotNull String checkout) {
        PropertiesComponent.getInstance(project).setValue(LAST_CHECKOUT_KEY, checkout);
    }

    /**
     * The {@code node} executable: on the PATH, then the usual install places (an IDE started from the macOS
     * Dock does not see the shell's PATH). Null when none is found.
     */
    @Nullable
    public static String findNode() {
        String exe = SystemInfo.isWindows ? "node.exe" : "node";
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (dir.isBlank()) continue;
                Path candidate = Path.of(dir, exe);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return candidate.toString();
            }
        }
        String home = System.getProperty("user.home");
        for (String candidate : List.of("/opt/homebrew/bin/node", "/usr/local/bin/node", "/usr/bin/node",
                home + "/.volta/bin/node", "C:\\Program Files\\nodejs\\node.exe")) {
            if (Files.isExecutable(Path.of(candidate))) return candidate;
        }
        for (String managed : List.of(home + "/.nvm/versions/node", home + "/.local/share/fnm/node-versions")) {
            Path newest = newestNode(Path.of(managed));
            if (newest != null) return newest.toString();
        }
        return null;
    }

    @Nullable
    private static Path newestNode(Path versions) {
        if (!Files.isDirectory(versions)) return null;
        try (Stream<Path> s = Files.list(versions)) {
            return s.map(v -> Files.isExecutable(v.resolve("bin/node")) ? v.resolve("bin/node")
                            : v.resolve("installation/bin/node"))
                    .filter(Files::isExecutable)
                    .max(Comparator.comparing(FLForgeEnvironment::versionKey))
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** {@code .../v20.19.6/bin/node} sorts by its numeric version parts. */
    private static String versionKey(Path node) {
        for (Path p = node; p != null; p = p.getParent()) {
            String name = p.getFileName() == null ? "" : p.getFileName().toString();
            if (name.matches("v?\\d+(\\.\\d+)*")) {
                StringBuilder key = new StringBuilder();
                for (String part : name.replace("v", "").split("\\.")) key.append(String.format("%08d", Integer.parseInt(part)));
                return key.toString();
            }
        }
        return "";
    }

    /**
     * Copies the bundled {@code forge-runner.mjs} to the IDE system directory (once per content) and returns its
     * path. Node cannot run a script from inside the plugin jar.
     */
    @NotNull
    public static Path runnerScript() throws IOException {
        return script("forge-runner.mjs");
    }

    /** {@code forge-vectors.mjs}, the op vector runner, installed like {@link #runnerScript()}. */
    @NotNull
    public static Path vectorsScript() throws IOException {
        return script("forge-vectors.mjs");
    }

    /** Whether the packages the vector runner imports are built: worker, recipe, ops (with its checks) and lang. */
    public static boolean isBuiltForVectors(@NotNull Path checkout) {
        return isBuilt(checkout)
                && Files.isRegularFile(checkout.resolve("packages/worker/dist/index.js"))
                && Files.isRegularFile(checkout.resolve("packages/recipe/dist/index.js"))
                && Files.isRegularFile(checkout.resolve("packages/ops/dist/checks/index.js"));
    }

    @NotNull
    private static Path script(@NotNull String name) throws IOException {
        byte[] raw;
        try (InputStream in = FLForgeEnvironment.class.getResourceAsStream("/runner/" + name)) {
            if (in == null) throw new IOException("runner/" + name + " is missing from the plugin");
            raw = in.readAllBytes();
        }
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        Path dir = Path.of(PathManager.getSystemPath(), "forge-fl", "runner-" + hash);
        Path script = dir.resolve(name);
        if (Files.isRegularFile(script)
                && new String(Files.readAllBytes(script), StandardCharsets.UTF_8).equals(new String(raw, StandardCharsets.UTF_8))) {
            return script;
        }
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "runner-", ".tmp");
        Files.write(tmp, raw);
        Files.move(tmp, script, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return script;
    }
}
