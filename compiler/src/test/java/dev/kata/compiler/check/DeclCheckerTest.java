package dev.kata.compiler.check;

import static dev.kata.compiler.check.CheckSupport.check;
import static dev.kata.compiler.check.CheckSupport.checkFiles;
import static dev.kata.compiler.check.CheckSupport.wrap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.diag.Fix;
import dev.kata.compiler.diag.Span;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeclCheckerTest {
    @Test
    void wrappedProgramIsClean() {
        CheckSupport result = check(wrap("class A {\n  let x: Int;\n}\n"));
        result.assertClean();
        assertEquals("dev.kata.test", result.program.app().id());
        assertEquals("Test", result.program.app().name());
        assertNotNull(result.program.rootExpr());
    }

    @Test
    void moduleNameMustMatchPath() {
        String source = wrap("").replace("module app.main;", "module app.other;");
        Diagnostic d = check(source).single("E0601");
        assertEquals(new Span(1, 8, 1, 17), d.span());
        assertEquals("app.main", d.expected());
        assertEquals("app.other", d.found());
        assertEquals(new Fix(new Span(1, 8, 1, 17), "app.main"), d.fix());
    }

    @Test
    void duplicateClassIsE0303() {
        Diagnostic d = check(wrap("class A {\n}\nclass A {\n}\n")).single("E0303");
        assertEquals(new Span(7, 7, 7, 8), d.span());
    }

    @Test
    void fieldAndMethodWithSameNameIsE0303() {
        Diagnostic d = check(wrap("class A {\n  let x: Int;\n  fn x(self) -> Unit {\n  }\n}\n")).single("E0303");
        assertEquals(new Span(7, 6, 7, 7), d.span());
    }

    @Test
    void duplicateParameterIsE0303() {
        check(wrap("class A {\n  fn m(self, a: Int, a: Int) -> Unit {\n  }\n}\n")).single("E0303");
    }

    @Test
    void unknownTypeIsE0302() {
        Diagnostic d = check(wrap("class A {\n  let x: Nope;\n}\n")).single("E0302");
        assertEquals(new Span(6, 10, 6, 14), d.span());
    }

    @Test
    void uiNameWithoutImportIsE0305WithFix() {
        Diagnostic d = check(wrap("class A {\n  let c: Column;\n}\n")).single("E0305");
        assertEquals(new Span(6, 10, 6, 16), d.span());
        assertEquals(new Fix(new Span(5, 1, 5, 1), "import kata.ui.Column;\n"), d.fix());
    }

    @Test
    void unusedImportIsE0306WithFix() {
        String source = wrap("").replace("import kata.ui.Ui;\n", "import kata.ui.Ui;\nimport kata.ui.Row;\n");
        Diagnostic d = check(source).single("E0306");
        assertEquals(new Span(5, 1, 5, 20), d.span());
        assertEquals(new Fix(new Span(5, 1, 6, 1), ""), d.fix());
    }

    @Test
    void unknownImportsAreE0307() {
        String unknownName = wrap("").replace("import kata.ui.Ui;\n", "import kata.ui.Ui;\nimport kata.ui.Nope;\n");
        check(unknownName).single("E0307");
        String unknownModule = wrap("").replace("import kata.ui.Ui;\n", "import kata.ui.Ui;\nimport other.mod.X;\n");
        check(unknownModule).single("E0307");
    }

    @Test
    void duplicateImportIsE0303() {
        String source = wrap("").replace("import kata.ui.Ui;\n", "import kata.ui.Ui;\nimport kata.ui.Ui;\n");
        check(source).single("E0303");
    }

    @Test
    void unitOutsideReturnTypeIsE0407() {
        check(wrap("class A {\n  let x: Unit;\n}\n")).single("E0407");
        check(wrap("class A {\n  fn m(self, a: Unit) -> Unit {\n  }\n}\n")).single("E0407");
        check(wrap("class A {\n  let x: List<Unit>;\n}\n")).single("E0407");
        check(wrap("class A {\n  let x: Handler<Unit>;\n}\n")).single("E0407");
    }

    @Test
    void wrongTypeArgumentCountIsE0408() {
        check(wrap("class A {\n  let x: List<Int, Int>;\n}\n")).single("E0408");
        check(wrap("class A {\n  let x: Int<Int>;\n}\n")).single("E0408");
        check(wrap("class A {\n  let x: List;\n}\n")).single("E0408");
        check(wrap("class A {\n  let x: Handler;\n}\n")).single("E0408");
        check(wrap("class A {\n  let x: A<Int>;\n}\n")).single("E0408");
    }

    @Test
    void validTypesAreAccepted() {
        check(wrap("class A {\n  let a: List<List<Int>>;\n  let b: Handler<String>;\n  let c: Action;\n"
                + "  let d: A;\n  let e: Ui;\n  let f: Bool;\n}\n")).assertClean();
    }

    @Test
    void missingInterfaceMethodIsE0506() {
        Diagnostic d = check(wrap("class B implements Screen {\n}\n")).single("E0506");
        assertEquals(new Span(5, 7, 5, 8), d.span());
        assertEquals("fn view(self) -> Ui", d.expected());
    }

    @Test
    void interfaceMethodWithWrongSignatureIsE0506() {
        check(wrap("class B implements Screen {\n  fn view(self) -> Text {\n    return Text { text: \"\" };\n  }\n}\n"))
                .single("E0506");
        check(wrap("class B implements Screen {\n  fn view(self, a: Int) -> Ui {\n    return Text { text: \"\" };\n  }\n}\n"))
                .single("E0506");
    }

    @Test
    void implementingUiOrAClassIsE0506() {
        check(wrap("class B implements Ui {\n}\n")).single("E0506");
        check(wrap("class B implements Text {\n}\n")).single("E0506");
    }

    @Test
    void missingAppIsE0602WithoutFile() {
        String source = wrap("");
        Diagnostic d = check(source.substring(0, source.indexOf("app {"))).single("E0602");
        assertEquals(null, d.file());
        assertEquals(null, d.span());
    }

    @Test
    void secondAppIsE0602() {
        String other = "module app.other;\napp {\n  id: \"a.b\",\n  name: \"n\",\n  root: 1,\n}\n";
        CheckSupport result = checkFiles(Map.of("src/app/main.kata", wrap(""), "src/app/other.kata", other));
        assertEquals(List.of("E0602"), result.codes());
        assertEquals("src/app/other.kata", result.diagnostics.get(0).file());
    }

    @Test
    void appEntriesMustBeIdNameRootInOrder() {
        String valid = wrap("");
        check(valid.replace("  id: \"dev.kata.test\",\n", "")).single("E0603");
        check(valid.replace("  id: \"dev.kata.test\",\n  name: \"Test\",\n", "  name: \"Test\",\n  id: \"dev.kata.test\",\n"))
                .single("E0603");
        check(valid.replace("  root: Main {},\n", "  root: Main {},\n  extra: 1,\n")).single("E0603");
    }

    @Test
    void appIdAndNameAreValidated() {
        String valid = wrap("");
        for (String id : List.of("Bad", "single", "a..b", "a.1b", "a.b_c", "")) {
            check(valid.replace("\"dev.kata.test\"", "\"" + id + "\"")).single("E0603");
        }
        check(valid.replace("name: \"Test\"", "name: \"\"")).single("E0603");
        check(valid.replace("name: \"Test\"", "name: 1")).single("E0603");
        check(valid.replace("\"dev.kata.test\"", "\"a1.b2\"")).assertClean();
    }
}
