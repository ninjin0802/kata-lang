package dev.kata.compiler.check;

import static dev.kata.compiler.check.CheckSupport.checkFiles;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.diag.Fix;
import dev.kata.compiler.diag.Span;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A project made of several files, each a module that imports names from the others. */
public class ModulesTest {
    public static final String MODEL = """
            module app.model;

            enum Kind {
              Plain,
              Urgent { level: Int },
            }

            class Note {
              let title: String;
              let kind: Kind;

              fn score(self) -> Int {
                match (self.kind) {
                  Kind.Plain => {
                    return 0;
                  }
                  Kind.Urgent { level } => {
                    return level;
                  }
                }
              }
            }
            """;

    public static String main(String imports, String body) {
        return "module app.main;\n\nimport kata.ui.Screen;\nimport kata.ui.Text;\nimport kata.ui.Ui;\n" + imports
                + "\nclass Main implements Screen {\n  fn view(self) -> Ui {\n    return Text { text: \"\" };\n  }\n"
                + body + "}\n\napp {\n  id: \"dev.kata.test\",\n  name: \"Test\",\n  root: Main {},\n}\n";
    }

    private static CheckSupport check(String mainSource) {
        return checkFiles(Map.of("src/app/main.kata", mainSource, "src/app/model.kata", MODEL));
    }

    @Test
    void importedClassesAndEnumsCanBeUsed() {
        check(main("import app.model.Kind;\nimport app.model.Note;\n", """
                  fn make(self) -> Note {
                    return Note { title: "a", kind: Kind.Urgent { level: 2 } };
                  }
                  fn label(self, kind: Kind) -> String {
                    match (kind) {
                      Kind.Plain => {
                        return "plain";
                      }
                      Kind.Urgent { level } => {
                        return self.make().score().toString();
                      }
                    }
                  }
                """)).assertClean();
    }

    @Test
    void aNameFromAnotherModuleNeedsAnImport() {
        Diagnostic d = check(main("", "  fn make(self, note: Note) -> Int {\n    return note.score();\n  }\n"))
                .single("E0305");
        assertEquals(new Fix(new Span(5, 19, 5, 19), "\nimport app.model.Note;"), d.fix());
    }

    @Test
    void importsOfUserModulesAreChecked() {
        check(main("import app.model.Note;\n", "")).single("E0306");
        check(main("import app.model.Nope;\n", "")).single("E0307");
        check(main("import app.nowhere.Note;\n", "")).single("E0307");
        check(main("import app.main.Main;\n", "")).single("E0307");
    }

    @Test
    void anImportedNameCannotBeDeclaredAgain() {
        String source = main("import app.model.Note;\n", "  fn n(self, note: Note) -> Int {\n    return 1;\n  }\n")
                + "";
        String clash = source.replace("\napp {", "\nclass Extra {\n}\n\napp {").replace("class Extra", "class Note");
        assertEquals(List.of("E0303"), check(clash).codes());
    }

    @Test
    void modulesMayImportEachOther() {
        String a = "module app.a;\n\nimport app.b.B;\n\nclass A {\n  let other: List<B>;\n}\n";
        String b = "module app.b;\n\nimport app.a.A;\n\nclass B {\n  let owner: A;\n}\n";
        checkFiles(Map.of("src/app/main.kata", main("", ""), "src/app/a.kata", a, "src/app/b.kata", b)).assertClean();
    }

    @Test
    void sameClassNameInTwoModulesIsAllowed() {
        String other = "module app.other;\n\nclass Note {\n  let id: Int;\n}\n";
        CheckSupport result = checkFiles(Map.of("src/app/main.kata",
                main("import app.other.Note;\n", "  fn id(self, note: Note) -> Int {\n    return note.id;\n  }\n"),
                "src/app/model.kata", MODEL, "src/app/other.kata", other));
        result.assertClean();
    }
}
