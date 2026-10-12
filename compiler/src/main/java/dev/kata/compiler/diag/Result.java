package dev.kata.compiler.diag;

import java.util.List;

/** The outcome of `kata check` or `kata build`. */
public record Result(List<Diagnostic> diagnostics, String artifact) {
    public boolean ok() {
        return diagnostics.isEmpty();
    }
}
