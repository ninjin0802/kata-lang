package dev.kata.compiler.diag;

/** One error. file, span, expected, found and fix may be null. */
public record Diagnostic(String code, String message, String file, Span span, String expected, String found,
        Fix fix) {
    public static Diagnostic of(String code, String message, String file, Span span) {
        return new Diagnostic(code, message, file, span, null, null, null);
    }

    public Diagnostic withExpected(String expected, String found) {
        return new Diagnostic(code, message, file, span, expected, found, fix);
    }

    public Diagnostic withFix(Span fixSpan, String replacement) {
        return new Diagnostic(code, message, file, span, expected, found, new Fix(fixSpan, replacement));
    }
}
