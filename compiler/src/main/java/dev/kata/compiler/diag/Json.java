package dev.kata.compiler.diag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Writes a Result in the output format of spec chapter 07. */
public final class Json {
    private Json() {}

    private static final Comparator<Diagnostic> ORDER = Comparator
            .comparing((Diagnostic d) -> d.file() == null)
            .thenComparing(d -> d.file() == null ? "" : d.file())
            .thenComparingInt(d -> d.span() == null ? 0 : d.span().line())
            .thenComparingInt(d -> d.span() == null ? 0 : d.span().column());

    public static String write(Result result) {
        List<Diagnostic> sorted = new ArrayList<>(result.diagnostics());
        sorted.sort(ORDER);
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"ok\": ").append(result.ok()).append(",\n");
        out.append("  \"artifact\": ").append(string(result.artifact())).append(",\n");
        if (sorted.isEmpty()) {
            out.append("  \"diagnostics\": []\n");
        } else {
            out.append("  \"diagnostics\": [\n");
            for (int i = 0; i < sorted.size(); i++) {
                diagnostic(out, sorted.get(i));
                out.append(i + 1 < sorted.size() ? ",\n" : "\n");
            }
            out.append("  ]\n");
        }
        out.append("}\n");
        return out.toString();
    }

    private static void diagnostic(StringBuilder out, Diagnostic d) {
        out.append("    {\n");
        out.append("      \"code\": ").append(string(d.code())).append(",\n");
        out.append("      \"message\": ").append(string(d.message())).append(",\n");
        out.append("      \"file\": ").append(string(d.file())).append(",\n");
        span(out, "      ", d.file() == null ? null : d.span());
        out.append(",\n");
        out.append("      \"expected\": ").append(string(d.expected())).append(",\n");
        out.append("      \"found\": ").append(string(d.found())).append(",\n");
        if (d.fix() == null) {
            out.append("      \"fix\": null\n");
        } else {
            out.append("      \"fix\": {\n");
            span(out, "        ", d.fix().span());
            out.append(",\n");
            out.append("        \"replacement\": ").append(string(d.fix().replacement())).append("\n");
            out.append("      }\n");
        }
        out.append("    }");
    }

    private static void span(StringBuilder out, String indent, Span span) {
        out.append(indent).append("\"line\": ").append(span == null ? "null" : span.line()).append(",\n");
        out.append(indent).append("\"column\": ").append(span == null ? "null" : span.column()).append(",\n");
        out.append(indent).append("\"endLine\": ").append(span == null ? "null" : span.endLine()).append(",\n");
        out.append(indent).append("\"endColumn\": ").append(span == null ? "null" : span.endColumn());
    }

    private static String string(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
