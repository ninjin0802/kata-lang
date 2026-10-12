package dev.kata.compiler.build.fixture;

import dev.kata.compiler.build.Sdk;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.ToolProvider;

/** Compiles, with javac, the classes the kata compiler will later generate for the counter example. */
public final class HandWrittenCounter {
    private HandWrittenCounter() {}

    private static final String COUNTER = """
            package app.counter;

            import kata.rt.*;

            public final class Counter implements Screen {
                public long count;

                public Counter() {
                    this.count = 0L;
                }

                public final Ui view() {
                    return new Column(KList.of(new Object[] {
                        new Text(Long.toString(this.count)),
                        new Button("+1", this::increment),
                        new Button("Reset", this::reset),
                    }));
                }

                public final void increment() {
                    this.count = this.count + 1L;
                }

                public final void reset() {
                    this.count = 0L;
                }
            }
            """;

    private static final String MAIN = """
            package kata.gen;

            public final class MainActivity extends kata.rt.KataActivity {
                @Override
                protected kata.rt.Screen createRoot() {
                    return new app.counter.Counter();
                }
            }
            """;

    public static Map<String, byte[]> compile(Sdk sdk, Path kataHome) throws Exception {
        Path dir = Files.createTempDirectory("kata-fixture");
        Path src = Files.createDirectories(dir.resolve("src"));
        Path out = Files.createDirectories(dir.resolve("out"));
        Path counter = Files.createDirectories(src.resolve("app/counter")).resolve("Counter.java");
        Path main = Files.createDirectories(src.resolve("kata/gen")).resolve("MainActivity.java");
        Files.writeString(counter, COUNTER);
        Files.writeString(main, MAIN);
        String classpath = String.join(File.pathSeparator,
                sdk.androidJar().toString(),
                kataHome.resolve("runtime/kata-rt-core.jar").toString(),
                kataHome.resolve("runtime/kata-rt-android.jar").toString());
        int status = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-source", "8", "-target", "8", "-Xlint:-options", "-proc:none",
                "-classpath", classpath, "-d", out.toString(), counter.toString(), main.toString());
        if (status != 0) {
            throw new IllegalStateException("fixture did not compile");
        }
        Map<String, byte[]> classes = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(out)) {
            List<Path> classFiles = files.filter(p -> p.toString().endsWith(".class")).sorted().toList();
            for (Path file : classFiles) {
                String name = out.relativize(file).toString().replace(File.separatorChar, '/');
                classes.put(name.substring(0, name.length() - ".class".length()), Files.readAllBytes(file));
            }
        }
        return classes;
    }
}
