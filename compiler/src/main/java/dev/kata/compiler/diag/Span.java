package dev.kata.compiler.diag;

/** A source range. Lines and columns are 1-based, columns count code points, the end is exclusive. */
public record Span(int line, int column, int endLine, int endColumn) {
    public static Span between(Span start, Span end) {
        return new Span(start.line, start.column, end.endLine, end.endColumn);
    }

    /** The empty range at the start of this span. */
    public Span startPoint() {
        return new Span(line, column, line, column);
    }

    /** The empty range at the end of this span. */
    public Span endPoint() {
        return new Span(endLine, endColumn, endLine, endColumn);
    }
}
