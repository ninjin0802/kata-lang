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
        }
    }
}
