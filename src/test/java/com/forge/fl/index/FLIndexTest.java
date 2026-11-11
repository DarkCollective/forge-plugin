package com.forge.fl.index;

import com.forge.fl.FLMultiFileTestBase;
import com.forge.fl.builtins.FLBuiltins;
import com.forge.fl.psi.FLNamedElement;
import com.forge.fl.psi.FLScope;
import com.forge.fl.psi.FLVariable;
import com.forge.fl.psi.FLWordRef;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.forge.fl.index.FLIndexEntry.Kind.*;

public class FLIndexTest extends FLMultiFileTestBase {
    private static final String SMALL = """
            IN: v
            USING: util ;
            : foo ( a -- b ) "d" doc bar v:baz ;
            : _p ( -- ) ;
            256 CONSTANT size
            0 VARIABLE ctr
            PRIMITIVE raw
            : >hex ( n:int -- str ) n + ;
            META: foo
              alias fu ;
              see-also q:other ;
              param a "x" ;
            ;
            +
            """;

    private static FLIndexEntry only(Map<String, List<FLIndexEntry>> m, String key) {
        List<FLIndexEntry> l = m.get(key);
        assertNotNull(key + " in " + m.keySet(), l);
        assertEquals(key, 1, l.size());
        return l.get(0);
    }

    public void testContentOfASmallFile() {
        Map<String, List<FLIndexEntry>> m = FLLightScan.scan(SMALL);
        // declarations, keyed vocab:name, with the offset of the name and a detail
        FLIndexEntry foo = only(m, "Dv:foo");
        assertEquals(WORD, foo.kind());
        assertEquals(SMALL.indexOf("foo"), foo.offset());
        assertEquals("( a -- b )", foo.detail());
        assertEquals(WORD, only(m, "Dv:_p").kind()); // private words are indexed too
        assertEquals("( -- int )", only(m, "Dv:size").detail());
        assertEquals(CONSTANT, only(m, "Dv:size").kind());
        assertEquals(VARIABLE, only(m, "Dv:ctr").kind());
        assertEquals("( -- int )", only(m, "Dv:ctr").detail());
        FLIndexEntry setter = only(m, "Dv:ctr!"); // a VARIABLE declares n and n!
        assertEquals(VARIABLE_SETTER, setter.kind());
        assertEquals("( int -- )", setter.detail());
        assertEquals(only(m, "Dv:ctr").offset(), setter.offset());
        assertEquals(PRIMITIVE, only(m, "Dv:raw").kind());
        assertEquals(WORD, only(m, "Dv:>hex").kind()); // symbol-only characters are fine in keys
        FLIndexEntry alias = only(m, "Dv:fu");
        assertEquals(ALIAS, alias.kind());
        assertEquals(SMALL.indexOf("fu"), alias.offset());
        assertEquals(VOCAB, only(m, "Vv").kind());
        // uses, keyed by the bare name
        for (String used : List.of("Ubar", "Ubaz", "Ufoo", "Uother", "U+")) assertTrue(used + " in " + m.keySet(), m.containsKey(used));
        assertEquals(SMALL.indexOf("v:baz"), only(m, "Ubaz").offset()); // a qualified use starts at its vocabulary prefix
        for (String notUsed : List.of("Udoc", "Uutil", "Ufu", "Ua", "Uv", "Ud")) {
            assertFalse(notUsed + " is not a word use", m.containsKey(notUsed));
        }
        // foo, _p, size, ctr, ctr!, raw, >hex and the alias fu
        long decls = m.values().stream().flatMap(List::stream).filter(FLIndexEntry::isDeclaration).count();
        assertEquals(8, decls);
    }

    public void testDefaultVocabularyAndSymbolWords() {
        Map<String, List<FLIndexEntry>> m = FLLightScan.scan(": + ( a b -- c ) a ;\n: g ( -- ) 1 2 + ;\n");
        assertEquals(WORD, only(m, "Duser:+").kind());
        assertEquals(USAGE, only(m, "U+").kind());
        assertTrue(m.containsKey("Vuser"));
    }

    public void testQualifiedUseIsKeyedByBareName() {
        Map<String, List<FLIndexEntry>> m = FLLightScan.scan(": g ( -- ) a:ctr! b:ctr ;\n");
        assertTrue(m.containsKey("Uctr!"));
        assertTrue(m.containsKey("Uctr"));
        assertFalse(m.containsKey("Ua:ctr!"));
    }

    public void testVersionIsDeclaredAndStable() {
        assertEquals(FLIndex.VERSION, new FLIndex().getVersion());
        assertTrue(FLIndex.VERSION >= 1);
    }

    public void testIndexIsQueryableThroughTheFileBasedIndex() {
        add("a.fth", "IN: a\n: pub ( x:int -- int ) x ;\n: _priv ( -- ) ;\n");
        add("b.fth", "IN: b\nUSING: a ;\n: f ( -- ) pub pub ;\n: >hex ( -- ) ;\n");
        List<FLIndexLookup.Hit> decl = FLIndexLookup.declarations(getProject(), "a", "pub");
        assertEquals(1, decl.size());
        assertEquals("a.fth", decl.get(0).file().getName());
        assertEquals("( x:int -- int )", decl.get(0).entries().get(0).detail());
        assertEquals(1, FLIndexLookup.declarations(getProject(), "a", "_priv").size());
        assertEquals(1, FLIndexLookup.declarations(getProject(), "b", ">hex").size());
        assertEquals(0, FLIndexLookup.declarations(getProject(), "b", "pub").size());
        List<FLIndexLookup.Hit> uses = FLIndexLookup.usages(getProject(), "pub");
        assertEquals(1, uses.size());
        assertEquals(2, uses.get(0).entries().size());
        assertTrue(FLIndexLookup.vocabularies(getProject()).containsAll(List.of("a", "b", "core", "hex", "raise")));
        assertEquals(List.of("a.fth"), FLIndexLookup.filesOfVocab(getProject(), "a").stream().map(h -> h.file().getName()).toList());
        // the built-ins are indexed like any file
        assertEquals("core.fl", FLIndexLookup.declarations(getProject(), "core", "dup").get(0).file().getName());
    }

    public void testIndexFollowsEdits() throws Exception {
        PsiFile a = add("a.fth", "IN: a\n: old ( -- ) ;\n");
        assertEquals(1, FLIndexLookup.declarations(getProject(), "a", "old").size());
        com.intellij.openapi.application.WriteAction.runAndWait(() ->
                com.intellij.openapi.vfs.VfsUtil.saveText(a.getVirtualFile(), "IN: a\n: fresh ( -- ) ;\n"));
        assertEquals(0, FLIndexLookup.declarations(getProject(), "a", "old").size());
        assertEquals(1, FLIndexLookup.declarations(getProject(), "a", "fresh").size());
    }

    // ---- the light scan agrees with the PSI ---------------------------------------------------------------

    private void assertScanMatchesPsi(String label, String text) {
        PsiFile file = myFixture.configureByText("scan.fth", text);
        Map<String, List<FLIndexEntry>> scan = FLLightScan.scan(text);

        TreeSet<String> expectedDecls = new TreeSet<>();
        for (FLScope.Decl d : FLScope.of(file).all()) {
            expectedDecls.add("D" + d.vocab() + ":" + d.name() + "@" + d.element().getTextOffset());
        }
        TreeSet<String> actualDecls = new TreeSet<>();
        TreeSet<String> actualUses = new TreeSet<>();
        for (Map.Entry<String, List<FLIndexEntry>> e : scan.entrySet()) {
            for (FLIndexEntry entry : e.getValue()) {
                if (entry.isDeclaration()) actualDecls.add(e.getKey() + "@" + entry.offset());
                else if (entry.kind() == USAGE) actualUses.add(e.getKey() + "@" + entry.offset());
            }
        }
        TreeSet<String> expectedUses = new TreeSet<>();
        for (FLWordRef ref : PsiTreeUtil.findChildrenOfType(file, FLWordRef.class)) {
            expectedUses.add("U" + ref.getBareName() + "@" + ref.getTextOffset());
        }
        assertEquals(label + ": declarations", expectedDecls, actualDecls);
        assertEquals(label + ": uses", expectedUses, actualUses);
    }

    public void testScanMatchesPsiOnTheBundledCorpus() throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(Path.of("src/test/testData/corpus"))) {
            files = s.filter(p -> p.toString().endsWith(".fth")).sorted().toList();
        }
        assertFalse(files.isEmpty());
        for (Path p : files) assertScanMatchesPsi(p.getFileName().toString(), Files.readString(p));
    }

    public void testScanMatchesPsiOnMalformedInput() {
        for (String text : List.of(
                ": f ( a -- b ) dup [ swap ] ; : g ( -- ) f\n: h ( -- ) 1 2 ;\n",
                "IN: ;\n: ( -- ) ;\nIN: x\n: y z:w ;\n] } 1 CONSTANT\nVARIABLE\n0 VARIABLE v : q ( -- ) v v! ;\n",
                "META: m doc \"x\" ; alias a b ; see-also x y:z ; bogus 1 2 ; param p \"d\" ;\nMETA: n\n: after ( -- ) ;\n",
                "USING: a b\nIN: c\n: f ( -- ) [ { 1 2 } a:b ] ;\nPRIMITIVE\nPRIMITIVE p\nVOCAB w\n: g ( -- ) \"s\" doc \"t\" category x ;\n",
                ": QUAL:name ( -- ) ;\n: IN: ( -- ) ;\n: ; \n: z ( a:int -- ) a ;\n")) {
            assertScanMatchesPsi(text.substring(0, Math.min(20, text.length())).replace("\n", " "), text);
        }
    }

    public void testScanMatchesPsiOnTheBuiltinFiles() throws IOException {
        Map<String, List<FLBuiltins.Word>> byVocab = FLBuiltins.loadWords().stream()
                .collect(Collectors.groupingBy(FLBuiltins.Word::vocab, java.util.TreeMap::new, Collectors.toList()));
        List<String> vocabs = new ArrayList<>(byVocab.keySet());
        for (String v : vocabs.subList(0, Math.min(10, vocabs.size()))) {
            assertScanMatchesPsi(v, FLBuiltins.render(v, byVocab.get(v)));
        }
        assertScanMatchesPsi("core", FLBuiltins.render("core", byVocab.get("core")));
    }

    public void testVariablesDeclareBothNamesInThePsiToo() {
        PsiFile f = parse("IN: v\n0 VARIABLE ctr\n");
        List<String> names = FLScope.of(f).all().stream().map(FLScope.Decl::name).toList();
        assertEquals(List.of("ctr", "ctr!"), names);
        assertTrue(FLScope.of(f).all().get(0).element() instanceof FLVariable);
        assertTrue(FLScope.of(f).all().get(0).element() instanceof FLNamedElement);
    }
}
