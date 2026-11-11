package com.forge.fl.builtins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The built-in words: every word that no {@code .fth} source defines (core words, intrinsics and the TypeScript
 * primitives bound into vocabularies). {@code builtins.json} lists them (see {@code tools/export-builtins.mjs});
 * this class turns it into read-only FL declaration files, one per vocabulary, in the IDE system directory. The
 * files are exposed as a library root ({@link FLBuiltinsLibraryProvider}), so they are indexed like any FL file
 * and Ctrl-click on {@code dup} opens {@code core.fl}.
 *
 * <p>A built-in word is declared as an empty colon definition: the header carries the stack effect, the comment
 * above it the documentation and the marker {@code [TypeScript ...]}, and the file header says where it comes from.
 */
public final class FLBuiltins {
    private static final Logger LOG = Logger.getInstance(FLBuiltins.class);
    private static final String RESOURCE = "/builtins/builtins.json";
    /** Bump when {@link #render} changes, so that already materialised directories are not reused. */
    private static final int RENDER_VERSION = 1;

    /** One exported word. {@code kind} is {@code primitive} or {@code intrinsic}. */
    public record Word(String vocab, String name, String kind, String effect, @Nullable String doc,
                       @Nullable String category, @Nullable String aliasOf) {
    }

    private static volatile VirtualFile root;
    private static volatile boolean missedRoot;

    private FLBuiltins() {
    }

    // ---- catalogue ----------------------------------------------------------------------------------------

    /** All exported words, sorted as in the file (vocabulary, then name). */
    @NotNull
    public static List<Word> loadWords() throws IOException {
        try (InputStream in = FLBuiltins.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("missing resource " + RESOURCE);
            return parseWords(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static List<Word> parseWords(String json) {
        JsonArray array = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("words");
        List<Word> words = new ArrayList<>();
        for (JsonElement e : array) {
            JsonObject o = e.getAsJsonObject();
            words.add(new Word(o.get("vocab").getAsString(), o.get("name").getAsString(), o.get("kind").getAsString(),
                    o.get("effect").getAsString(), str(o, "doc"), str(o, "category"), str(o, "aliasOf")));
        }
        return words;
    }

    @Nullable
    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    /** The text of the declaration file of one vocabulary. */
    @NotNull
    public static String render(@NotNull String vocab, @NotNull List<Word> words) {
        StringBuilder sb = new StringBuilder();
        sb.append("\\ Built-in words of the vocabulary ").append(vocab).append(", implemented in TypeScript.\n");
        sb.append("\\ They have no FL source. Generated from the Forge runtime by tools/export-builtins.mjs;\n");
        sb.append("\\ read-only, do not edit.\n");
        sb.append("IN: ").append(vocab).append('\n');
        for (Word w : words) {
            sb.append('\n');
            if (w.doc() != null && !w.doc().isBlank()) {
                for (String line : w.doc().split("\\R", -1)) sb.append("\\ ").append(line).append('\n');
            }
            sb.append("\\ [TypeScript ").append(w.kind());
            if (w.category() != null) sb.append(", category ").append(w.category());
            if (w.aliasOf() != null) sb.append(", alias of ").append(w.aliasOf());
            sb.append("]\n");
            sb.append(": ").append(w.name()).append(' ').append(w.effect()).append(" ;\n");
        }
        return sb.toString();
    }

    // ---- materialisation ----------------------------------------------------------------------------------

    /**
     * Writes the declaration files (once per content version) and returns their directory.
     */
    @NotNull
    public static Path materialize() throws IOException {
        byte[] raw;
        try (InputStream in = FLBuiltins.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IOException("missing resource " + RESOURCE);
            raw = in.readAllBytes();
        }
        String hash;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((RENDER_VERSION + ":").getBytes(StandardCharsets.UTF_8));
            hash = java.util.HexFormat.of().formatHex(md.digest(raw)).substring(0, 12);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        Path parent = Path.of(PathManager.getSystemPath(), "forge-fl");
        Path dir = parent.resolve("builtins-" + hash);
        if (Files.isDirectory(dir)) return dir;
        Files.createDirectories(parent);

        Map<String, List<Word>> byVocab = new TreeMap<>();
        for (Word w : parseWords(new String(raw, StandardCharsets.UTF_8))) {
            byVocab.computeIfAbsent(w.vocab(), v -> new ArrayList<>()).add(w);
        }
        Path tmp = Files.createTempDirectory(parent, "builtins-tmp-");
        try {
            for (Map.Entry<String, List<Word>> e : byVocab.entrySet()) {
                Path file = tmp.resolve(e.getKey() + ".fl");
                Files.writeString(file, render(e.getKey(), e.getValue()), StandardCharsets.UTF_8);
                file.toFile().setWritable(false, false);
            }
            try {
                Files.move(tmp, dir, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException raced) {
                if (!Files.isDirectory(dir)) throw raced; // else another process won the race: use its copy
                deleteTree(tmp);
            }
        } catch (IOException | RuntimeException e) {
            deleteTree(tmp);
            throw e;
        }
        // Older versions are no longer used.
        try (Stream<Path> siblings = Files.list(parent)) {
            for (Path p : siblings.filter(p -> p.getFileName().toString().startsWith("builtins-") && !p.equals(dir)).toList()) {
                if (!p.getFileName().toString().startsWith("builtins-tmp-")) deleteTree(p);
            }
        } catch (IOException ignored) {
            // best effort
        }
        return dir;
    }

    private static void deleteTree(Path p) {
        try (Stream<Path> walk = Files.walk(p)) {
            walk.sorted(Comparator.reverseOrder()).forEach(f -> {
                f.toFile().setWritable(true, false);
                f.toFile().delete();
            });
        } catch (IOException ignored) {
            // best effort
        }
    }

    /**
     * The virtual directory of the declaration files. Writes them on first use. Null when the directory is not
     * visible in the VFS yet (see {@link #consumeMissedRoot()}); the next call tries again.
     */
    @Nullable
    public static VirtualFile getRoot() {
        VirtualFile r = root;
        if (r != null && r.isValid()) return r;
        synchronized (FLBuiltins.class) {
            if (root != null && root.isValid()) return root;
            Path dir;
            try {
                dir = materialize();
            } catch (IOException | RuntimeException e) {
                if (e instanceof ProcessCanceledException) throw (ProcessCanceledException) e;
                LOG.warn("Cannot materialise the FL built-ins", e);
                return null;
            }
            LocalFileSystem fs = LocalFileSystem.getInstance();
            boolean inRead = ApplicationManager.getApplication().isReadAccessAllowed();
            // The roots provider runs in cancellable read actions; the lookup is short and must not be cancelled
            // half way (that would also make the result look like a failure).
            VirtualFile vf = ProgressManager.getInstance().computeInNonCancelableSection(() -> {
                VirtualFile found = fs.findFileByNioFile(dir);
                if (found == null && !inRead) found = fs.refreshAndFindFileByNioFile(dir);
                return found;
            });
            if (vf == null) {
                missedRoot = true;
                LOG.info("FL built-ins directory is not visible in the VFS yet: " + dir);
                return null;
            }
            root = vf;
            return vf;
        }
    }

    /**
     * Writes the declaration files and makes the VFS see them, with a synchronous refresh. Call it outside read
     * actions, early (project startup, test set-up); {@link #getRoot()} then finds the directory without disk access.
     */
    public static void prepare() {
        try {
            Path dir = materialize();
            if (ApplicationManager.getApplication() == null) return; // not started yet: the files are written
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir);
            if (vf != null) {
                vf.refresh(false, true);
                root = vf;
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ProcessCanceledException) throw (ProcessCanceledException) e;
            LOG.warn("Cannot materialise the FL built-ins", e);
        }
    }

    /** Whether {@link #getRoot()} returned null since the last call (the library needs a root change event). */
    public static boolean consumeMissedRoot() {
        boolean m = missedRoot;
        missedRoot = false;
        return m;
    }

    // ---- identification -----------------------------------------------------------------------------------

    public static boolean isBuiltinFile(@Nullable VirtualFile file) {
        VirtualFile r = root;
        return file != null && r != null && VfsUtilCore.isAncestor(r, file, true);
    }

    public static boolean isBuiltinFile(@Nullable PsiFile file) {
        if (file == null) return false;
        PsiFile original = file.getOriginalFile();
        return isBuiltinFile(original.getVirtualFile());
    }

    public static boolean isBuiltin(@Nullable PsiElement element) {
        return element != null && element.isValid() && isBuiltinFile(element.getContainingFile());
    }
}
