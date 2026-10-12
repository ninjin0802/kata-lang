package dev.kata.compiler.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void writesTheSpecExample() {
        Span span = new Span(15, 22, 15, 32);
        Diagnostic diagnostic = new Diagnostic("E0401", "type mismatch", "src/app/counter.kata", span,
                "String", "Int", new Fix(span, "self.count.toString()"));
        String expected = """
                {
                  "ok": false,
                  "artifact": null,
                  "diagnostics": [
                    {
                      "code": "E0401",
                      "message": "type mismatch",
                      "file": "src/app/counter.kata",
                      "line": 15,
                      "column": 22,
                      "endLine": 15,
                      "endColumn": 32,
                      "expected": "String",
                      "found": "Int",
                      "fix": {
                        "line": 15,
                        "column": 22,
                        "endLine": 15,
                        "endColumn": 32,
                        "replacement": "self.count.toString()"
                      }
                    }
                  ]
                }
                """;
        assertEquals(expected, Json.write(new Result(List.of(diagnostic), null)));
    }

    @Test
    void writesSuccessWithArtifact() {
        String expected = """
                {
                  "ok": true,
                  "artifact": "build/app.apk",
                  "diagnostics": []
                }
                """;
        assertEquals(expected, Json.write(new Result(List.of(), "build/app.apk")));
    }

    @Test
    void escapesStrings() {
        Diagnostic diagnostic = new Diagnostic("E0903", "d8: \"x\" \\ failed\n\tline2 日本語 \u0001", null, null,
                null, null, null);
        String json = Json.write(new Result(List.of(diagnostic), null));
        assertTrue(json.contains("\"message\": \"d8: \\\"x\\\" \\\\ failed\\n\\tline2 日本語 \\u0001\""), json);
        assertTrue(json.contains("\"line\": null"), json);
    }

    @Test
    void sortsByFileLineColumnWithFilelessLast() {
        Diagnostic env = new Diagnostic("E0901", "m", null, null, null, null, null);
        Diagnostic b = new Diagnostic("E0301", "m", "src/b.kata", new Span(1, 1, 1, 2), null, null, null);
        Diagnostic a2 = new Diagnostic("E0302", "m", "src/a.kata", new Span(2, 1, 2, 2), null, null, null);
        Diagnostic a1 = new Diagnostic("E0303", "m", "src/a.kata", new Span(1, 5, 1, 6), null, null, null);
        Diagnostic a0 = new Diagnostic("E0304", "m", "src/a.kata", new Span(1, 2, 1, 3), null, null, null);
        String json = Json.write(new Result(List.of(env, b, a2, a1, a0), null));
        int[] positions = {json.indexOf("E0304"), json.indexOf("E0303"), json.indexOf("E0302"),
                json.indexOf("E0301"), json.indexOf("E0901")};
        for (int i = 1; i < positions.length; i++) {
            assertTrue(positions[i - 1] < positions[i], json);
        }
    }
}
