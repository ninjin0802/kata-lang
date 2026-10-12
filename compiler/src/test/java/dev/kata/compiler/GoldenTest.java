package dev.kata.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.kata.compiler.diag.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** Each directory under cases/ is a kata project with the exact JSON `kata check` must print. */
class GoldenTest {
    static Path casesDir() throws Exception {
        return Path.of(GoldenTest.class.getResource("/cases").toURI());
    }

    static List<Path> cases() throws Exception {
        try (Stream<Path> dirs = Files.list(casesDir())) {
            return dirs.filter(Files::isDirectory).sorted().toList();
        }
    }

    @TestFactory
    Stream<DynamicTest> goldenCases() throws Exception {
        return cases().stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> {
            String expected = Files.readString(dir.resolve("expected.json"));
            assertEquals(expected, Json.write(Compiler.check(dir)));
        }));
    }
}
