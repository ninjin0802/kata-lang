package dev.kata.compiler.cli;

import dev.kata.compiler.Compiler;
import dev.kata.compiler.diag.Json;
import dev.kata.compiler.diag.Result;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** The `kata` command. Prints one JSON document on stdout. */
public final class Main {
    private Main() {}

    private static final String USAGE = "usage: kata check <project> | kata build <project>";

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        System.exit(run(args, out, err));
    }

    /** KATA_HOME, then the kata.home property, then the directory two levels above this class's jar. */
    static Path kataHome() {
        String env = System.getenv("KATA_HOME");
        if (env != null && !env.isEmpty()) {
            return Path.of(env);
        }
        String property = System.getProperty("kata.home");
        if (property != null && !property.isEmpty()) {
            return Path.of(property);
        }
        try {
            Path jar = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return jar.getParent().getParent();
        } catch (java.net.URISyntaxException | RuntimeException e) {
            return Path.of(".");
        }
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 2) {
            err.println(USAGE);
            return 2;
        }
        Path project = Path.of(args[1]);
        try {
            Result result;
            switch (args[0]) {
                case "check" -> result = Compiler.check(project);
                case "build" -> result = Compiler.build(project, System.getenv(), kataHome());
                default -> {
                    err.println(USAGE);
                    return 2;
                }
            }
            out.print(Json.write(result));
            return result.ok() ? 0 : 1;
        } catch (Compiler.UsageError e) {
            err.println(e.getMessage());
            return 2;
        } catch (RuntimeException | StackOverflowError e) {
            // The output contract is one JSON document, even when the compiler itself fails.
            e.printStackTrace(err);
            Result failure = new Result(java.util.List.of(dev.kata.compiler.diag.Diagnostic.of("E0999",
                    "internal compiler error: " + e, null, null)), null);
            out.print(Json.write(failure));
            return 1;
        }
    }
}
