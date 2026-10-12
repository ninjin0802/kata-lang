package dev.kata.compiler.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kata.compiler.build.fixture.HandWrittenCounter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApkBuilderTest {
    private static Sdk sdk;
    private static Path kataHome;

    @BeforeAll
    static void locateSdk() throws Exception {
        assumeTrue(System.getenv("ANDROID_HOME") != null, "ANDROID_HOME is not set");
        sdk = Sdk.locate(System.getenv());
        kataHome = Path.of(System.getProperty("kata.home"));
    }

    private Path build(Path project) throws Exception {
        Map<String, byte[]> classes = HandWrittenCounter.compile(sdk, kataHome);
        return ApkBuilder.build(project, classes, new AppInfo("dev.kata.handwritten", "Hand & <Written>"), kataHome, sdk);
    }

    @Test
    void apkContainsDexAndManifest(@TempDir Path project) throws Exception {
        Path apk = build(project);
        assertEquals(project.resolve("build/app.apk"), apk);
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            assertNotNull(zip.getEntry("classes.dex"));
            assertNotNull(zip.getEntry("AndroidManifest.xml"));
        }
        // Kept for the manual device check (adb install compiler/build/handwritten.apk).
        Files.copy(apk, Path.of("build/handwritten.apk"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    @Test
    void apkIsSigned(@TempDir Path project) throws Exception {
        Path apk = build(project);
        Process process = new ProcessBuilder(List.of(
                sdk.buildTool("apksigner").toString(), "verify", apk.toString()))
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
    }

    @Test
    void projectPathWithSpacesWorks(@TempDir Path tmp) throws Exception {
        Path project = Files.createDirectories(tmp.resolve("my project dir"));
        assertTrue(Files.exists(build(project)));
    }
}
