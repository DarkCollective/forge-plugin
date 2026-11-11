// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.builtins;

import com.forge.fl.FLMultiFileTestBase;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.psi.FLScope;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class FLBuiltinsTest extends FLMultiFileTestBase {
    private static Map<String, List<FLBuiltins.Word>> byVocab() throws IOException {
        return FLBuiltins.loadWords().stream()
                .collect(Collectors.groupingBy(FLBuiltins.Word::vocab, TreeMap::new, Collectors.toList()));
    }

    public void testCatalogueIsSortedUniqueAndTypeScriptOnly() throws IOException {
        List<FLBuiltins.Word> words = FLBuiltins.loadWords();
        assertTrue("catalogue too small: " + words.size(), words.size() > 200);
        Set<String> seen = new HashSet<>();
        String previous = "";
        for (FLBuiltins.Word w : words) {
            String key = w.vocab() + " " + w.name();
            assertTrue("sorted: " + key, previous.compareTo(key) < 0);
            assertTrue("unique: " + key, seen.add(key));
            assertTrue(key, w.kind().equals("primitive") || w.kind().equals("intrinsic"));
            assertTrue(key, w.effect().startsWith("(") && w.effect().endsWith(")"));
            previous = key;
        }
        // grounded in the forge sources: dup/swap/rot/+ are core; raise:range and hex:encode are TypeScript primitives
        assertTrue(seen.contains("core dup") && seen.contains("core swap") && seen.contains("core +"));
        assertTrue(seen.contains("raise range") && seen.contains("hex encode"));
        // util:decode-table is defined in util.fth, so it must not be in the catalogue
        assertFalse(seen.contains("util decode-table"));
        assertFalse(seen.stream().anyMatch(k -> k.startsWith("util ")));
        // intrinsics are core's
        for (FLBuiltins.Word w : words) assertTrue(w.name(), !w.kind().equals("intrinsic") || w.vocab().equals("core"));
    }

    public void testEveryWordIsADeclarationInItsVocabularyFile() throws IOException {
        for (Map.Entry<String, List<FLBuiltins.Word>> e : byVocab().entrySet()) {
            String text = FLBuiltins.render(e.getKey(), e.getValue());
            PsiFile file = myFixture.configureByText("v.fth", text);
            assertEmpty(problems(file)); // the declaration shape is one the parser accepts
            List<String> declared = FLScope.of(file).all().stream().map(FLScope.Decl::name).toList();
            assertEquals(e.getKey(), e.getValue().stream().map(FLBuiltins.Word::name).toList(), declared);
            for (FLScope.Decl d : FLScope.of(file).all()) {
                assertEquals(e.getKey(), d.vocab());
                FLBuiltins.Word w = e.getValue().stream().filter(x -> x.name().equals(d.name())).findFirst().orElseThrow();
                FLNamedElement el = d.element();
                assertEquals(w.effect().replaceAll("\\s+", " "), el.getEffectText());
            }
        }
    }

    public void testMaterialisedFilesAreReadOnlyLibraryFilesWithTheRenderedText() throws IOException {
        VirtualFile root = FLBuiltins.getRoot();
        assertNotNull(root);
        for (Map.Entry<String, List<FLBuiltins.Word>> e : byVocab().entrySet()) {
            VirtualFile f = root.findChild(e.getKey() + ".fl");
            assertNotNull(e.getKey(), f);
            assertFalse(f.isWritable());
            assertTrue(FLBuiltins.isBuiltinFile(f));
            assertEquals(FLBuiltins.render(e.getKey(), e.getValue()), new String(f.contentsToByteArray(), "UTF-8"));
        }
        VirtualFile core = root.findChild("core.fl");
        assertTrue(ProjectFileIndex.getInstance(getProject()).isInLibrary(core));
        assertFalse(ProjectFileIndex.getInstance(getProject()).isInContent(core));
    }

    public void testDocumentationAndTypeScriptMarkerAreInTheFile() {
        String text = FLBuiltins.render("hex", List.of(new FLBuiltins.Word("hex", "encode", "primitive", "( bytes -- str )",
                "Encode bytes.\nTwo digits per byte.", "Encodings", null)));
        assertEquals("""
                \\ Built-in words of the vocabulary hex, implemented in TypeScript.
                \\ They have no FL source. Generated from the Forge runtime by tools/export-builtins.mjs;
                \\ read-only, do not edit.
                IN: hex

                \\ Encode bytes.
                \\ Two digits per byte.
                \\ [TypeScript primitive, category Encodings]
                : encode ( bytes -- str ) ;
                """, text);
    }
}
