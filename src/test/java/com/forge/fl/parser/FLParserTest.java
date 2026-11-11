package com.forge.fl.parser;

import com.forge.fl.FLTestBase;
import com.forge.fl.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FLParserTest extends FLTestBase {
    private static <T extends PsiElement> List<T> all(PsiFile f, Class<T> c) {
        return new ArrayList<>(PsiTreeUtil.findChildrenOfType(f, c));
    }

    private static List<String> names(List<? extends FLNamedElement> els) {
        List<String> out = new ArrayList<>();
        for (FLNamedElement e : els) out.add(e.getName());
        return out;
    }

    public void testColonDefinition() {
        String text = ": f ( str amount:int[1..25]=13 -- str )\n  \"Doc.\" doc\n  \"Cat\" category\n  amount [ 1 + ] { 1 2 } recurse ;\n";
        assertClean(text);
        PsiFile f = parse(text);
        FLDefinition def = all(f, FLDefinition.class).get(0);
        assertEquals("f", def.getName());
        assertEquals("f", def.getNameIdentifier().getText());
        assertEquals(text.indexOf("f ("), def.getTextOffset());
        assertEquals(List.of("amount"), names(def.getParameters()));
        assertEquals("( str amount:int[1..25]=13 -- str )", def.getHeader().getText());
        assertEquals(2, count(def, "DOC_DECL"));
        assertEquals(1, count(def, "QUOTATION"));
        assertEquals(1, count(def, "LIST_LITERAL"));
        // body words are wrapped in word-reference elements
        assertEquals(List.of("amount"), refTexts(def, "amount"));
    }

    private static long count(PsiElement scope, String elementType) {
        return PsiTreeUtil.findChildrenOfType(scope, FLPsiElement.class).stream()
                .filter(e -> e.toString().equals(elementType)).count();
    }

    private static List<String> refTexts(PsiElement scope, String only) {
        List<String> out = new ArrayList<>();
        for (FLWordRef r : PsiTreeUtil.findChildrenOfType(scope, FLWordRef.class)) {
            if (r.getText().equals(only)) out.add(r.getText());
        }
        return out;
    }

    public void testHeaderParameters() {
        PsiFile f = parse(": f ( a b:int s:str=\"x y\" -- c r:str str ) ; : g ( str n:int -- ) ; : h ( .. -- .. ) ;");
        assertEmpty(problems(f));
        List<FLDefinition> defs = all(f, FLDefinition.class);
        // named inputs and bare labels before "--"; not bare type names, not outputs
        assertEquals(List.of("a", "b", "s"), names(defs.get(0).getParameters()));
        assertEquals(List.of("n"), names(defs.get(1).getParameters()));
        assertEquals(List.of(), names(defs.get(2).getParameters()));
    }

    public void testConstantsVariablesPrimitives() {
        String text = "256 CONSTANT block-size\n\"s\" CONSTANT greeting\n#[ de ] VARIABLE _buf\n'a' CONSTANT ch\ntrue VARIABLE flag\n"
                + "-1.5e3 CONSTANT f\nPRIMITIVE decode-raw\n";
        assertClean(text);
        PsiFile f = parse(text);
        assertEquals(List.of("block-size", "greeting", "ch", "f"), names(all(f, FLConstant.class)));
        assertEquals(List.of("_buf", "flag"), names(all(f, FLVariable.class)));
        assertEquals(List.of("decode-raw"), names(all(f, FLPrimitive.class)));
    }

    public void testVocabularyForms() {
        assertClean("IN: base64\nUSING: a b c ;\nUSING: ;\nVOCAB v\n");
        PsiFile f = parse("IN: base64\nVOCAB other\n");
        List<FLInForm> forms = all(f, FLInForm.class);
        assertEquals("base64", forms.get(0).getVocabName());
        assertEquals("other", forms.get(1).getVocabName());
    }

    public void testMeta() {
        String text = "META: rot\n  doc \"Rotate.\" ;\n  category \"Cat\" ;\n  version \"1\" ;\n"
                + "  param amount \"Shift\" ;\n  alias rot13 rot14 ;\n  see-also classical:atbash foo ;\n;\n";
        assertClean(text);
        PsiFile f = parse(text);
        FLMetaBlock block = all(f, FLMetaBlock.class).get(0);
        assertEquals("rot", block.getTarget().getText());
        assertEquals(6, block.getClauses().size());
        assertEquals(List.of("rot13", "rot14"), names(all(f, FLAlias.class)));
        assertEquals(1, all(f, FLParamRef.class).size());
        assertEquals(3, all(f, FLWordRef.class).size()); // target + 2 see-also
        assertClean("META: x\n;\n");
    }

    public void testStrayTopLevelWordsAreNotErrors() {
        // the console allows executable code at top level
        assertClean("1 2 + .\n[ dup ] call { 1 2 } \"s\" util:foo recurse true #[ ] 'x'\n");
    }

    public void testNoErrorsOnValidMixtures() {
        assertClean("( c ) \\ line\n: f ( -- ) ( inside ) [ [ 1 ] { [ ] } ] ; ");
        assertClean("");
        assertClean("   \n\n");
        assertClean(": f ( -- )\n;\n");
    }

    public void testSampleFile() throws IOException {
        Path p = Path.of("src/main/resources/examples/sample.fl");
        assertClean(Files.readString(p));
    }

    // ---- error recovery ----

    private void assertErrors(String text, int minErrors) {
        PsiFile f = parse(text);
        assertEquals("tree must reproduce the text", text, f.getText());
        assertTrue("expected at least " + minErrors + " problem(s) in: " + text, problems(f).size() >= minErrors);
    }

    public void testMissingSemicolonBeforeNextDefinition() {
        String text = ": a ( -- ) 1\n: b ( -- ) 2 ;\n";
        assertErrors(text, 1);
        PsiFile f = parse(text);
        assertEquals(List.of("a", "b"), names(all(f, FLDefinition.class)));
        assertEquals(1, problems(f).size());
    }

    public void testMissingSemicolonAtEndOfFile() {
        PsiFile f = parse(": a ( -- ) 1 2");
        assertEquals(1, problems(f).size());
        assertEquals(List.of("a"), names(all(f, FLDefinition.class)));
    }

    public void testUnbalancedBrackets() {
        assertErrors(": a ( -- ) [ 1 2 ;", 1);
        assertErrors(": a ( -- ) 1 ] ;", 1);
        assertErrors(": a ( -- ) { 1 ] } ;", 1);
        assertErrors("[ 1 2", 1);
        assertErrors("} ] ", 2);
        // the definition after the broken one still parses
        PsiFile f = parse(": a ( -- ) [ 1 ;\n: b ( -- ) ;\n");
        assertEquals(List.of("a", "b"), names(all(f, FLDefinition.class)));
    }

    public void testNestedColonIsAnError() {
        assertErrors(": a ( -- ) [ : b ( -- ) ; ;", 1);
        assertErrors(": a ( -- ) : b ( -- ) ; ;", 1);
    }

    public void testBrokenHeaders() {
        assertErrors(": a ;", 1);
        assertErrors(": a ( x y", 1);
        assertErrors(": ( -- ) ;", 1);
        assertErrors(":", 1);
    }

    public void testStrayTokens() {
        assertErrors(";", 1);
        assertErrors("CONSTANT x", 1);
        assertErrors("foo; 1", 1);
        assertErrors("1+ \"abc", 2);
        assertErrors("IN:", 1);
        assertErrors("USING: a b", 1);
    }

    public void testMetaErrors() {
        assertErrors("META: x\n  bogus 1 ;\n;", 1);
        assertErrors("META: x\n  doc \"a\" ;", 1);
        assertErrors("META: x\n  doc 1 ;\n;", 1);
        assertErrors("META:", 1);
        assertErrors("META: x doc \"a\"\n: f ( -- ) ;", 1);
    }

    public void testParserAlwaysConsumesEverything() {
        Random rnd = new Random(7);
        String[] atoms = {":", ";", "[", "]", "{", "}", "(", ")", "( -- )", "f", "g", "1", "\"s\"", "'c'", "#[ de ]", "IN:",
                "USING:", "META:", "VOCAB", "CONSTANT", "VARIABLE", "PRIMITIVE", "recurse", "doc", "alias", "param",
                "see-also", "a:b", "\n", " ", "\\", "true", "x:int", "--"};
        for (int n = 0; n < 400; n++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + rnd.nextInt(18);
            for (int i = 0; i < len; i++) sb.append(atoms[rnd.nextInt(atoms.length)]).append(' ');
            String text = sb.toString();
            PsiFile f = parse(text);
            assertEquals(text, f.getText());
        }
    }
}
