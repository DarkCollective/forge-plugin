package com.forge.fl.index;

import com.forge.fl.FLFile;
import com.forge.fl.psi.FLNamedElement;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Queries over {@link FLIndex}, always over the whole index scope (project, libraries and the built-in words).
 * Results are cached per project and dropped whenever any PSI or VFS change happens, so a file with thousands of
 * references asks the index once per distinct name, not once per reference and not once per keystroke.
 */
public final class FLIndexLookup {
    /** An index hit: a file and the entries it holds for the key. */
    public record Hit(VirtualFile file, List<FLIndexEntry> entries) {
    }

    private FLIndexLookup() {
    }

    /** Changes to any file, including ones no PSI is loaded for (the index may have changed with them). */
    private static final ModificationTracker VFS_TRACKER = () -> VirtualFileManager.getInstance().getModificationCount();

    private static final com.intellij.openapi.util.Key<CachedValue<Map<String, List<Hit>>>> CACHE =
            com.intellij.openapi.util.Key.create("com.forge.fl.indexLookupCache");

    private static Map<String, List<Hit>> cache(Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, CACHE, () ->
                CachedValueProvider.Result.create(new ConcurrentHashMap<>(),
                        PsiModificationTracker.MODIFICATION_COUNT, VFS_TRACKER), false);
    }

    /** Whether index queries can run now. */
    public static boolean isAvailable(@NotNull Project project) {
        return !DumbService.isDumb(project);
    }

    /** The hits of one key, files ordered by path. Empty while indexing. */
    @NotNull
    public static List<Hit> hits(@NotNull Project project, @NotNull String key) {
        if (!isAvailable(project)) return Collections.emptyList();
        Map<String, List<Hit>> cache = cache(project);
        List<Hit> cached = cache.get(key);
        if (cached != null) return cached;
        List<Hit> result = query(project, key);
        cache.put(key, result);
        return result;
    }

    /** Number of real index queries made (cache misses); a test hook for the caching guarantee. */
    public static final java.util.concurrent.atomic.AtomicInteger QUERIES = new java.util.concurrent.atomic.AtomicInteger();

    private static List<Hit> query(Project project, String key) {
        QUERIES.incrementAndGet();
        List<Hit> hits = new ArrayList<>();
        try {
            FileBasedIndex.getInstance().processValues(FLIndex.NAME, key, null, (file, entries) -> {
                hits.add(new Hit(file, entries));
                return true;
            }, GlobalSearchScope.allScope(project));
        } catch (IndexNotReadyException e) {
            return Collections.emptyList();
        }
        hits.sort(Comparator.comparing(h -> h.file().getPath()));
        return hits;
    }

    /** Declarations of {@code name} in {@code vocab} found in the index (all files, the current one included). */
    @NotNull
    public static List<Hit> declarations(@NotNull Project project, @NotNull String vocab, @NotNull String name) {
        return hits(project, FLIndex.declKey(vocab, name));
    }

    /** Files that use the bare word name. */
    @NotNull
    public static List<Hit> usages(@NotNull Project project, @NotNull String bareName) {
        return hits(project, FLIndex.usageKey(bareName));
    }

    /** Files that declare into, or switch to, the vocabulary. */
    @NotNull
    public static List<Hit> filesOfVocab(@NotNull Project project, @NotNull String vocab) {
        return hits(project, FLIndex.vocabKey(vocab));
    }

    /** All vocabulary names known to the index. */
    @NotNull
    public static Set<String> vocabularies(@NotNull Project project) {
        // processAllKeys may report keys whose files are gone (the persisted index keeps stale keys), so only
        // vocabularies that still have a file count.
        return keysWithPrefix(project, "V").stream().filter(k -> !hits(project, k).isEmpty())
                .map(k -> k.substring(1)).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The words of a vocabulary as {@code name -> a declaration entry with a file}, for completion. The first
     * declaration (by file path) of a name wins.
     */
    @NotNull
    public static Map<String, Decl> wordsOf(@NotNull Project project, @NotNull String vocab, @Nullable VirtualFile exclude) {
        String prefix = FLIndex.declVocabPrefix(vocab);
        Map<String, Decl> out = new java.util.TreeMap<>();
        for (String key : keysWithPrefix(project, prefix)) {
            String name = key.substring(prefix.length());
            for (Hit h : hits(project, key)) {
                if (h.file().equals(exclude)) continue;
                for (FLIndexEntry e : h.entries()) {
                    if (e.isDeclaration()) {
                        out.putIfAbsent(name, new Decl(vocab, name, h.file(), e));
                        break;
                    }
                }
                if (out.containsKey(name)) break;
            }
        }
        return out;
    }

    /** A declaration found through the index. */
    public record Decl(String vocab, String name, VirtualFile file, FLIndexEntry entry) {
    }

    private static final com.intellij.openapi.util.Key<CachedValue<Map<String, List<String>>>> KEYS =
            com.intellij.openapi.util.Key.create("com.forge.fl.indexKeysCache");

    @NotNull
    private static List<String> keysWithPrefix(Project project, String prefix) {
        if (!isAvailable(project)) return Collections.emptyList();
        Map<String, List<String>> cache = CachedValuesManager.getManager(project).getCachedValue(project, KEYS, () ->
                CachedValueProvider.Result.create(new ConcurrentHashMap<>(),
                        PsiModificationTracker.MODIFICATION_COUNT, VFS_TRACKER), false);
        List<String> cached = cache.get(prefix);
        if (cached != null) return cached;
        List<String> keys = new ArrayList<>();
        try {
            FileBasedIndex.getInstance().processAllKeys(FLIndex.NAME, k -> {
                if (k.startsWith(prefix)) keys.add(k);
                return true;
            }, GlobalSearchScope.allScope(project), null);
        } catch (IndexNotReadyException e) {
            return Collections.emptyList();
        }
        Collections.sort(keys);
        cache.put(prefix, keys);
        return keys;
    }

    // ---- from index entries to PSI --------------------------------------------------------------------------

    /** The declaration element at {@code entry} in {@code file}, or null when the index is stale. */
    @Nullable
    public static FLNamedElement element(@NotNull Project project, @NotNull VirtualFile file, @NotNull FLIndexEntry entry) {
        try {
            PsiFile psi = PsiManager.getInstance(project).findFile(file);
            if (!(psi instanceof FLFile)) return null;
            PsiElement leaf = psi.findElementAt(entry.offset());
            FLNamedElement named = PsiTreeUtil.getParentOfType(leaf, FLNamedElement.class, false);
            return named != null && named.getTextOffset() == entry.offset() ? named : null;
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
