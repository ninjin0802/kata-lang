package dev.kata.compiler.build;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Assembles an APK from generated classes using the SDK build-tools directly. */
public final class ApkBuilder {
    private ApkBuilder() {}

    public static Path build(Path project, Map<String, byte[]> classes, AppInfo app, Path kataHome, Sdk sdk)
            throws ToolFailure {
        return build(project, classes, app, List.of(), kataHome, sdk);
    }

    /** @param permissions Android permission names the app needs, e.g. android.permission.INTERNET */
    public static Path build(Path project, Map<String, byte[]> classes, AppInfo app, List<String> permissions,
            Path kataHome, Sdk sdk) throws ToolFailure {
        try {
            Path build = project.toAbsolutePath().normalize().resolve("build");
            refuseSymbolicLinks(build);
            deleteTree(build.resolve("classes"));
            deleteTree(build.resolve("dex"));
            deleteTree(build.resolve("assets"));
            Path classesDir = Files.createDirectories(build.resolve("classes"));
            Path dexDir = Files.createDirectories(build.resolve("dex"));

            List<String> classFiles = new ArrayList<>();
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                Path file = classesDir.resolve(entry.getKey() + ".class");
                Files.createDirectories(file.getParent());
                Files.write(file, entry.getValue());
                classFiles.add(file.toString());
            }

            Path manifest = build.resolve("AndroidManifest.xml");
            Files.writeString(manifest, manifest(app, permissions), StandardCharsets.UTF_8);

            Path base = build.resolve("base.apk");
            Path aligned = build.resolve("aligned.apk");
            Path apk = build.resolve("app.apk");
            Files.deleteIfExists(base);
            Files.deleteIfExists(aligned);
            Files.deleteIfExists(apk);

            List<String> link = new ArrayList<>(List.of(sdk.buildTool("aapt2").toString(), "link",
                    "-o", base.toString(),
                    "--manifest", manifest.toString(),
                    "-I", sdk.androidJar().toString(),
                    "--min-sdk-version", Integer.toString(Sdk.MIN_SDK),
                    "--target-sdk-version", Integer.toString(Sdk.TARGET_SDK),
                    "--version-code", "1",
                    "--version-name", "1.0"));
            Path stagedAssets = stageAssets(project.toAbsolutePath().normalize().resolve("assets"),
                    build.resolve("assets"));
            if (stagedAssets != null) {
                link.add("-A");
                link.add(stagedAssets.toString());
            }
            run("aapt2", link);

            List<String> d8 = new ArrayList<>(List.of(javaBinary(), "-cp", sdk.d8Jar().toString(),
                    "com.android.tools.r8.D8",
                    "--min-api", Integer.toString(Sdk.MIN_SDK),
                    "--lib", sdk.androidJar().toString(),
                    "--output", dexDir.toString()));
            d8.addAll(classFiles);
            d8.add(kataHome.resolve("runtime").resolve("kata-rt-core.jar").toString());
            d8.add(kataHome.resolve("runtime").resolve("kata-rt-android.jar").toString());
            run("d8", d8);

            URI zipUri = URI.create("jar:" + base.toUri());
            try (FileSystem zip = FileSystems.newFileSystem(zipUri, Map.of())) {
                Files.copy(dexDir.resolve("classes.dex"), zip.getPath("classes.dex"),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            run("zipalign", List.of(sdk.buildTool("zipalign").toString(), "-f", "4",
                    base.toString(), aligned.toString()));

            Path keystore = debugKeystore();
            run("apksigner", List.of(sdk.buildTool("apksigner").toString(), "sign",
                    "--ks", keystore.toString(),
                    "--ks-pass", "pass:android",
                    "--key-pass", "pass:android",
                    "--out", apk.toString(),
                    aligned.toString()));
            return apk;
        } catch (IOException e) {
            throw new ToolFailure("E0903", "io: " + e);
        }
    }

    /**
     * The build directory is deleted from and written to, so neither it nor the entries kata owns inside it
     * may be symbolic links: a project could otherwise redirect those writes outside itself.
     */
    private static void refuseSymbolicLinks(Path build) throws ToolFailure {
        List<Path> owned = List.of(build, build.resolve("classes"), build.resolve("dex"), build.resolve("assets"),
                build.resolve("AndroidManifest.xml"), build.resolve("base.apk"), build.resolve("aligned.apk"),
                build.resolve("app.apk"));
        for (Path path : owned) {
            if (Files.isSymbolicLink(path)) {
                throw new ToolFailure("E0903", "kata: " + path + " is a symbolic link; remove it and build again");
            }
        }
    }

    /**
     * Copies the regular files of the project's assets/ into build/assets and returns that directory, or
     * null when there is nothing to package. aapt2 follows symbolic links, so it is never pointed at the
     * project's own directory: a link there would pull a file from outside the project into the APK.
     */
    private static Path stageAssets(Path assets, Path staged) throws IOException {
        java.nio.file.LinkOption noFollow = java.nio.file.LinkOption.NOFOLLOW_LINKS;
        if (!Files.isDirectory(assets, noFollow)) {
            return null;
        }
        Files.createDirectories(staged);
        try (Stream<Path> paths = Files.walk(assets)) {
            for (Path file : paths.filter(p -> Files.isRegularFile(p, noFollow)).toList()) {
                Path target = staged.resolve(assets.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target, noFollow);
            }
        }
        return staged;
    }

    static String manifest(AppInfo app, List<String> permissions) {
        StringBuilder uses = new StringBuilder();
        for (String permission : permissions) {
            uses.append("\n    <uses-permission android:name=\"").append(xml(permission)).append("\" />");
        }
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="%s">%s
                    <application android:label="%s" android:theme="@android:style/Theme.Material.Light.NoActionBar">
                        <activity
                            android:name="kata.gen.MainActivity"
                            android:exported="true"
                            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode|density">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
                """.formatted(xml(app.id()), uses, xml(app.name()));
    }

    /** Escapes for an XML attribute; a leading @ or ? would otherwise be read as a resource reference. */
    private static String xml(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
        if (escaped.startsWith("@") || escaped.startsWith("?")) {
            escaped = "\\" + escaped;
        }
        return escaped;
    }

    private static Path debugKeystore() throws ToolFailure, IOException {
        Path keystore = Path.of(System.getProperty("user.home"), ".android", "debug.keystore");
        if (!Files.exists(keystore)) {
            Files.createDirectories(keystore.getParent());
            run("keytool", List.of(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                    "-genkeypair",
                    "-keystore", keystore.toString(),
                    "-storepass", "android",
                    "-keypass", "android",
                    "-alias", "androiddebugkey",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", "10000",
                    "-dname", "CN=Android Debug,O=Android,C=US"));
        }
        return keystore;
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static void run(String tool, List<String> command) throws ToolFailure {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int status = process.waitFor();
            if (status != 0) {
                throw new ToolFailure("E0903", tool + ": " + output.strip());
            }
        } catch (IOException e) {
            throw new ToolFailure("E0903", tool + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolFailure("E0903", tool + ": interrupted");
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
