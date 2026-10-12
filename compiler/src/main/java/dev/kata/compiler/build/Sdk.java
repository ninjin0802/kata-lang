package dev.kata.compiler.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Locates the fixed Android SDK components kata builds against. */
public final class Sdk {
    public static final String PLATFORM = "android-35";
    public static final String BUILD_TOOLS = "35.0.0";
    public static final int MIN_SDK = 26;
    public static final int TARGET_SDK = 35;

    private final Path home;

    private Sdk(Path home) {
        this.home = home;
    }

    public static Sdk locate(Map<String, String> env) throws ToolFailure {
        String value = env.get("ANDROID_HOME");
        if (value == null || value.isEmpty()) {
            throw new ToolFailure("E0901", "ANDROID_HOME is not set");
        }
        Path home = Path.of(value);
        if (!Files.isDirectory(home)) {
            throw new ToolFailure("E0901", "ANDROID_HOME does not exist: " + value);
        }
        Sdk sdk = new Sdk(home);
        if (!Files.isRegularFile(sdk.androidJar())) {
            throw new ToolFailure("E0902", "missing SDK component: platforms/" + PLATFORM);
        }
        if (!Files.isRegularFile(sdk.d8Jar())) {
            throw new ToolFailure("E0902", "missing SDK component: build-tools/" + BUILD_TOOLS);
        }
        return sdk;
    }

    public Path androidJar() {
        return home.resolve("platforms").resolve(PLATFORM).resolve("android.jar");
    }

    public Path buildTool(String name) {
        return home.resolve("build-tools").resolve(BUILD_TOOLS).resolve(name);
    }

    public Path d8Jar() {
        return buildTool("lib").resolve("d8.jar");
    }
}
