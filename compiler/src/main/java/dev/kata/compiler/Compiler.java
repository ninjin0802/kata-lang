package dev.kata.compiler;

import dev.kata.compiler.build.ApkBuilder;
import dev.kata.compiler.build.Sdk;
import dev.kata.compiler.build.ToolFailure;
import dev.kata.compiler.check.CheckedProgram;
import dev.kata.compiler.check.Checker;
import dev.kata.compiler.codegen.ClassGenerator;
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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        analyze(readSources(project), readAssets(project), diagnostics);
        return new Result(diagnostics, null);
    }

    /**
     * Checks the project and, when it is clean, generates classes and assembles build/app.apk.
     *
     * @param env the environment, read for ANDROID_HOME
     * @param kataHome the installation directory containing runtime/*.jar
     */
    public static Result build(Path project, Map<String, String> env, Path kataHome) throws UsageError {
        List<Diagnostic> diagnostics = new ArrayList<>();
        CheckedProgram program = analyze(readSources(project), readAssets(project), diagnostics);
        if (!diagnostics.isEmpty()) {
            return new Result(diagnostics, null);
        }
        Map<String, byte[]> classes;
        try {
            classes = ClassGenerator.generate(program);
        } catch (RuntimeException e) {
            diagnostics.add(Diagnostic.of("E0999", "internal compiler error during code generation: " + e, null,
                    null));
            return new Result(diagnostics, null);
        }
        try {
            Sdk sdk = Sdk.locate(env);
            // Permissions follow from the components the program uses; there is nothing to declare by hand.
            List<String> permissions = new ArrayList<>();
            if (program.constructs("kata.ui.WebView")) {
                permissions.add("android.permission.INTERNET");
            }
            Path apk = ApkBuilder.build(project, classes, program.app(), permissions, kataHome, sdk);
            Path root = project.toAbsolutePath().normalize();
            return new Result(diagnostics, root.relativize(apk).toString().replace(File.separatorChar, '/'));
        } catch (ToolFailure failure) {
            diagnostics.add(Diagnostic.of(failure.code(), failure.getMessage(), null, null));
            return new Result(diagnostics, null);
        }
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
        return files;
    }

    /** Runs every phase up to checking. The program is null when lexing or parsing reported anything. */
    static CheckedProgram analyze(List<Source> sources, java.util.Set<String> assets, List<Diagnostic> diagnostics) {
        List<Ast.File> files = parse(sources, diagnostics);
        if (!diagnostics.isEmpty()) {
            return null;
        }
        Map<String, String> texts = new LinkedHashMap<>();
        for (Source source : sources) {
            texts.put(source.path(), source.text());
        }
        return Checker.check(files, texts, assets, diagnostics);
    }

    /** The files under <project>/assets, as paths relative to it. Symbolic links are not followed. */
    static java.util.Set<String> readAssets(Path project) throws UsageError {
        Path assets = project.resolve("assets");
        java.util.Set<String> names = new java.util.TreeSet<>();
        if (!Files.isDirectory(assets, LinkOption.NOFOLLOW_LINKS)) {
            return names;
        }
        try (Stream<Path> paths = Files.walk(assets)) {
            for (Path file : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                names.add(assets.relativize(file).toString().replace(File.separatorChar, '/'));
            }
        } catch (IOException e) {
            throw new UsageError("cannot read assets: " + e.getMessage());
        }
        return names;
    }

    static List<Source> readSources(Path project) throws UsageError {
        Path src = project.resolve("src");
        if (!Files.isDirectory(src)) {
            throw new UsageError("not a kata project (no src directory): " + project);
        }
        List<Source> sources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(src)) {
            List<Path> files = paths.filter(p -> p.toString().endsWith(".kata")
                            && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
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
