package dev.kata.compiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.diag.Result;
import dev.kata.compiler.diag.Span;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Applying a diagnostic's fix must make that diagnostic go away (spec 07 #output). */
class FixApplyTest {
    @TestFactory
    Stream<DynamicTest> everyFixResolvesItsDiagnostic() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (Path dir : GoldenTest.cases()) {
            Result result = Compiler.check(dir);
            for (Diagnostic diagnostic : result.diagnostics()) {
                if (diagnostic.fix() == null) {
                    continue;
                }
                String name = dir.getFileName() + " " + diagnostic.code() + "@" + diagnostic.span().line();
                tests.add(DynamicTest.dynamicTest(name, () -> {
                    Path copy = copyProject(dir);
                    Path file = copy.resolve(diagnostic.file());
                    Files.writeString(file, apply(Files.readString(file), diagnostic.fix().span(),
                            diagnostic.fix().replacement()));
                    Result after = Compiler.check(copy);
                    boolean stillThere = after.diagnostics().stream().anyMatch(d ->
                            d.code().equals(diagnostic.code()) && d.message().equals(diagnostic.message())
                                    && sameStart(d.span(), diagnostic.span()));
                    assertTrue(!stillThere, "fix did not resolve " + diagnostic + "\nafter: " + after.diagnostics());
                }));
            }
        }
        assertTrue(tests.size() >= 8, "expected the golden cases to contain fixes, found " + tests.size());
        return tests.stream();
    }

    private static boolean sameStart(Span a, Span b) {
        return a != null && b != null && a.line() == b.line() && a.column() == b.column();
    }

    /** Replaces the span (1-based, columns in code points, end exclusive) with the replacement. */
    static String apply(String text, Span span, String replacement) {
        int start = offset(text, span.line(), span.column());
        int end = offset(text, span.endLine(), span.endColumn());
        return text.substring(0, start) + replacement + text.substring(end);
    }

    private static int offset(String text, int line, int column) {
        int index = 0;
        for (int current = 1; current < line; current++) {
            int newline = text.indexOf('\n', index);
            if (newline < 0) {
                return text.length();
            }
            index = newline + 1;
        }
        return Math.min(text.length(), text.offsetByCodePoints(index, column - 1));
    }

    private static Path copyProject(Path dir) throws IOException {
        Path copy = Files.createTempDirectory("kata-fix");
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path source : paths.toList()) {
                Path target = copy.resolve(dir.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target);
                }
            }
        }
        return copy;
    }
}
