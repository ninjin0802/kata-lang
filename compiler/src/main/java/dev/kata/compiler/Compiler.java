package dev.kata.compiler;

import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.diag.Result;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Entry points for `kata check` and `kata build`. */
public final class Compiler {
    private Compiler() {}

    /** The command was used wrongly; reported on stderr with exit code 2. */
    public static final class UsageError extends Exception {
        public UsageError(String message) {
            super(message);
        }
    }

    /** A source file. The path is relative to the project and uses forward slashes. */
    public record Source(String path, String text) {}

    public static Result check(Path project) throws UsageError {
        List<Source> sources = readSources(project);
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (sources.isEmpty()) {
            diagnostics.add(Diagnostic.of("E0602", "no app declaration in project", null, null));
        }
        return new Result(diagnostics, null);
    }

    static List<Source> readSources(Path project) throws UsageError {
        Path src = project.resolve("src");
        if (!Files.isDirectory(src)) {
            throw new UsageError("not a kata project (no src directory): " + project);
        }
        List<Source> sources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(src)) {
            List<Path> files = paths.filter(p -> p.toString().endsWith(".kata") && Files.isRegularFile(p))
                    .sorted().toList();
            for (Path file : files) {
                String relative = project.relativize(file).toString().replace(File.separatorChar, '/');
                sources.add(new Source(relative, Files.readString(file, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new UsageError("cannot read sources: " + e.getMessage());
        }
        return sources;
    }
}
