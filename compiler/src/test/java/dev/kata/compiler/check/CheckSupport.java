package dev.kata.compiler.check;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.syntax.Ast;
import dev.kata.compiler.syntax.Lexer;
import dev.kata.compiler.syntax.Parser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs the checker on in-memory sources. */
public final class CheckSupport {
    public static final String PATH = "src/app/main.kata";

    /** Lines 1-4 of a wrapped program. Declarations passed to wrap() start on line 5. */
    public static final String HEADER = """
            module app.main;
            import kata.ui.Screen;
            import kata.ui.Text;
            import kata.ui.Ui;
            """;

    public static final String FOOTER = """
            class Main implements Screen {
              fn view(self) -> Ui {
                return Text { text: "" };
              }
            }
            app {
              id: "dev.kata.test",
              name: "Test",
              root: Main {},
            }
            """;

    public final List<Diagnostic> diagnostics = new ArrayList<>();
    public CheckedProgram program;

    /** A complete valid program around the given declarations, which start on line 5. */
    public static String wrap(String declarations) {
        return HEADER + declarations + (declarations.endsWith("\n") ? "" : "\n") + FOOTER;
    }

    /** Wraps statements in `class A { fn m(self) -> Unit { ... } }`; the statements start on line 7. */
    public static String wrapBody(String statements) {
        return wrap("class A {\n  fn m(self) -> Unit {\n" + statements + "\n  }\n}\n");
    }

    public static CheckSupport check(String source) {
        return checkFiles(Map.of(PATH, source));
    }

    public static CheckSupport checkFiles(Map<String, String> sources) {
        return checkFiles(sources, java.util.Set.of());
    }

    /** @param assets the paths of the files under the project's assets/ directory */
    public static CheckSupport checkFiles(Map<String, String> sources, java.util.Set<String> assets) {
        CheckSupport support = new CheckSupport();
        List<Ast.File> files = new ArrayList<>();
        Map<String, String> ordered = new LinkedHashMap<>(new java.util.TreeMap<>(sources));
        for (Map.Entry<String, String> entry : ordered.entrySet()) {
            files.add(Parser.parse(entry.getKey(),
                    Lexer.lex(entry.getKey(), entry.getValue(), support.diagnostics), support.diagnostics));
        }
        if (!support.diagnostics.isEmpty()) {
            fail("test source has syntax errors: " + support.diagnostics);
        }
        support.program = Checker.check(files, ordered, assets, support.diagnostics);
        return support;
    }

    public List<String> codes() {
        return diagnostics.stream().map(Diagnostic::code).toList();
    }

    public void assertClean() {
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
    }

    /** The only diagnostic, which must have the given code. */
    public Diagnostic single(String code) {
        if (diagnostics.size() != 1 || !diagnostics.get(0).code().equals(code)) {
            fail("expected exactly one " + code + " but got: " + diagnostics);
        }
        return diagnostics.get(0);
    }
}
