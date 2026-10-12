package dev.kata.compiler.check;

import dev.kata.compiler.diag.Span;

/** Source text addressable by Span, for building fix replacements. */
final class SourceText {
    private final String[] lines;

    SourceText(String text) {
        String withoutBom = text.startsWith("﻿") ? text.substring(1) : text;
        this.lines = withoutBom.split("\n", -1);
    }

    String slice(Span span) {
        StringBuilder out = new StringBuilder();
        for (int line = span.line(); line <= span.endLine(); line++) {
            int[] points = lines[line - 1].codePoints().toArray();
            int from = line == span.line() ? span.column() - 1 : 0;
            int to = line == span.endLine() ? span.endColumn() - 1 : points.length;
            out.append(new String(points, from, Math.max(0, Math.min(to, points.length) - from)));
            if (line < span.endLine()) {
                out.append('\n');
            }
        }
        return out.toString();
    }
}
