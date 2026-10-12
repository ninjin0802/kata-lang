package dev.kata.compiler.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void noArgumentsIsUsageError() {
        assertEquals(2, run());
        assertEquals("", out.toString());
        assertFalse(err.toString().isEmpty());
    }

    @Test
    void unknownCommandIsUsageError(@TempDir Path project) {
        assertEquals(2, run("frobnicate", project.toString()));
        assertEquals("", out.toString());
    }

    @Test
    void projectWithoutSrcIsUsageError(@TempDir Path project) {
        assertEquals(2, run("check", project.toString()));
        assertEquals("", out.toString());
        assertTrue(err.toString().contains("src"), err.toString());
    }

    @Test
    void symlinkedSourceFilesAreNotRead(@TempDir Path tmp) throws Exception {
        Path project = Files.createDirectories(tmp.resolve("project"));
        Files.createDirectories(project.resolve("src/app"));
        Path outside = tmp.resolve("outside.kata");
        Files.writeString(outside, "this is not kata");
        Files.createSymbolicLink(project.resolve("src/app/main.kata"), outside);
        assertEquals(1, run("check", project.toString()));
        assertTrue(out.toString().contains("\"code\": \"E0602\""), out.toString());
        assertFalse(out.toString().contains("E0201"), out.toString());
    }

    @Test
    void emptySrcReportsNoAppDeclaration(@TempDir Path project) throws Exception {
        Files.createDirectories(project.resolve("src"));
        assertEquals(1, run("check", project.toString()));
        assertTrue(out.toString().contains("\"code\": \"E0602\""), out.toString());
        assertTrue(out.toString().contains("\"ok\": false"), out.toString());
    }
}
