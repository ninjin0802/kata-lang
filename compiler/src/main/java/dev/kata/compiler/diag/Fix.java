package dev.kata.compiler.diag;

/** A mechanical correction: replace the span with the replacement. */
public record Fix(Span span, String replacement) {}
