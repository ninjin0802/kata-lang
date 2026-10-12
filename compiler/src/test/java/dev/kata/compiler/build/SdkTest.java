package dev.kata.compiler.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SdkTest {
    @Test
    void missingAndroidHomeIsE0901() {
        ToolFailure failure = assertThrows(ToolFailure.class, () -> Sdk.locate(Map.of()));
        assertEquals("E0901", failure.code());
    }

    @Test
    void nonexistentAndroidHomeIsE0901(@TempDir Path tmp) {
        Map<String, String> env = Map.of("ANDROID_HOME", tmp.resolve("nope").toString());
        ToolFailure failure = assertThrows(ToolFailure.class, () -> Sdk.locate(env));
        assertEquals("E0901", failure.code());
    }

    @Test
    void missingPlatformIsE0902(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("build-tools/35.0.0"));
        Map<String, String> env = Map.of("ANDROID_HOME", tmp.toString());
        ToolFailure failure = assertThrows(ToolFailure.class, () -> Sdk.locate(env));
        assertEquals("E0902", failure.code());
    }

    @Test
    void missingBuildToolsIsE0902(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("platforms/android-35"));
        Files.createFile(tmp.resolve("platforms/android-35/android.jar"));
        Map<String, String> env = Map.of("ANDROID_HOME", tmp.toString());
        ToolFailure failure = assertThrows(ToolFailure.class, () -> Sdk.locate(env));
        assertEquals("E0902", failure.code());
    }
}
