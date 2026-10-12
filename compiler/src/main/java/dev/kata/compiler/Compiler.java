package dev.kata.compiler;

import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.diag.Result;
import dev.kata.compiler.syntax.Ast;
import dev.kata.compiler.syntax.Lexer;
import dev.kata.compiler.syntax.Parser;
import dev.kata.compiler.syntax.Token;
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
        List<Diagnostic> diagnostics = new ArrayList<>();
        parse(readSources(project), diagnostics);
        return new Result(diagnostics, null);
    }

    /** Lexes and parses every source. A file with lexical errors is not parsed, to avoid follow-on noise. */
    static List<Ast.File> parse(List<Source> sources, List<Diagnostic> diagnostics) {
        List<Ast.File> files = new ArrayList<>();
        for (Source source : sources) {
            int before = diagnostics.size();
            List<Token> tokens = Lexer.lex(source.path(), source.text(), diagnostics);
            if (diagnostics.size() == before) {
                files.add(Parser.parse(source.path(), tokens, diagnostics));
            }
        }
        if (diagnostics.isEmpty() && files.stream().noneMatch(file -> file.app() != null)) {
            diagnostics.add(Diagnostic.of("E0602", "no app declaration in project", null, null));
        }
        return files;
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
