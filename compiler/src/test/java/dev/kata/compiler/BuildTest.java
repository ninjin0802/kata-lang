package dev.kata.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kata.compiler.diag.Result;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildTest {
    private static final Path KATA_HOME = Path.of(System.getProperty("kata.home"));

    private static Path counterProject(Path tmp) throws Exception {
        Path project = tmp.resolve("counter");
        Path source = Path.of("..", "examples", "counter", "src", "app", "counter.kata");
        Files.createDirectories(project.resolve("src/app"));
        Files.copy(source, project.resolve("src/app/counter.kata"));
        return project;
    }

    @Test
    void buildsTheCounterExample(@TempDir Path tmp) throws Exception {
        assumeTrue(System.getenv("ANDROID_HOME") != null, "ANDROID_HOME is not set");
        Path project = counterProject(tmp);
        Result result = Compiler.build(project, System.getenv(), KATA_HOME);
        assertTrue(result.ok(), result.diagnostics().toString());
        assertEquals("build/app.apk", result.artifact());
        assertTrue(Files.size(project.resolve("build/app.apk")) > 0);
    }

    @Test
    void missingSdkIsReportedWithoutFile(@TempDir Path tmp) throws Exception {
        Result result = Compiler.build(counterProject(tmp), Map.of(), KATA_HOME);
        assertFalse(result.ok());
        assertEquals(1, result.diagnostics().size());
        assertEquals("E0901", result.diagnostics().get(0).code());
        assertNull(result.diagnostics().get(0).file());
        assertNull(result.artifact());
    }

    @Test
    void sourceErrorsStopTheBuildBeforeTheSdkIsNeeded(@TempDir Path tmp) throws Exception {
        Path project = counterProject(tmp);
        Path file = project.resolve("src/app/counter.kata");
        Files.writeString(file, Files.readString(file).replace("self.count + 1", "self.count + true"));
        Result result = Compiler.build(project, Map.of(), KATA_HOME);
        assertEquals("E0402", result.diagnostics().get(0).code());
        assertEquals(1, result.diagnostics().size());
        assertFalse(Files.exists(project.resolve("build")));
    }
}
