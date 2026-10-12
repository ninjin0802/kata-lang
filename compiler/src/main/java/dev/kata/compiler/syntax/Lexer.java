package dev.kata.compiler.syntax;

import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.diag.Span;
import dev.kata.compiler.syntax.Token.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Splits source text into tokens (spec chapter 03). */
public final class Lexer {
    private static final Map<String, Kind> KEYWORDS = Map.ofEntries(
            Map.entry("module", Kind.MODULE), Map.entry("import", Kind.IMPORT), Map.entry("class", Kind.CLASS),
            Map.entry("implements", Kind.IMPLEMENTS), Map.entry("let", Kind.LET), Map.entry("var", Kind.VAR),
            Map.entry("fn", Kind.FN), Map.entry("self", Kind.SELF), Map.entry("return", Kind.RETURN),
            Map.entry("if", Kind.IF), Map.entry("else", Kind.ELSE), Map.entry("while", Kind.WHILE),
            Map.entry("for", Kind.FOR), Map.entry("in", Kind.IN), Map.entry("true", Kind.TRUE),
            Map.entry("false", Kind.FALSE), Map.entry("app", Kind.APP), Map.entry("enum", Kind.ENUM),
            Map.entry("match", Kind.MATCH));

    private static final Set<String> RESERVED_FOR_LATER = Set.of("interface", "break", "continue");

    private final String file;
    private final int[] chars;
    private final List<Diagnostic> out;
    private final List<Token> tokens = new ArrayList<>();
    private int pos;
    private int line = 1;
    private int column = 1;

    private Lexer(String file, String source, List<Diagnostic> out) {
        this.file = file;
        this.chars = source.codePoints().toArray();
        this.out = out;
    }

    public static List<Token> lex(String file, String source, List<Diagnostic> out) {
        Lexer lexer = new Lexer(file, source, out);
        lexer.run();
        return lexer.tokens;
    }

    private void run() {
        if (chars.length > 0 && chars[0] == 0xFEFF) {
            pos = 1;
        }
        while (pos < chars.length) {
            int c = chars[pos];
            if (c == '\n') {
                pos++;
                line++;
                column = 1;
            } else if (c == ' ' || c == '\t' || c == '\r') {
                advance();
            } else if (c == '/' && peek(1) == '/') {
                while (pos < chars.length && chars[pos] != '\n') {
                    advance();
                }
            } else if (isLetter(c)) {
                word();
            } else if (isDigit(c)) {
                number();
            } else if (c == '"') {
                string();
            } else {
                symbol();
            }
        }
        tokens.add(new Token(Kind.EOF, "", new Span(line, column, line, column)));
    }

    private void word() {
        int startColumn = column;
        StringBuilder text = new StringBuilder();
        while (pos < chars.length && (isLetter(chars[pos]) || isDigit(chars[pos]))) {
            text.appendCodePoint(chars[pos]);
            advance();
        }
        String word = text.toString();
        Span span = new Span(line, startColumn, line, column);
        Kind keyword = KEYWORDS.get(word);
        if (keyword != null) {
            tokens.add(new Token(keyword, word, span));
            return;
        }
        if (RESERVED_FOR_LATER.contains(word)) {
            out.add(Diagnostic.of("E0105", "'" + word + "' is reserved for a later version of kata", file, span));
        }
        Kind kind = Character.isUpperCase(word.charAt(0)) ? Kind.UPPER_IDENT : Kind.LOWER_IDENT;
        tokens.add(new Token(kind, word, span));
    }

    private void number() {
        int startColumn = column;
        StringBuilder text = new StringBuilder();
        while (pos < chars.length && isDigit(chars[pos])) {
            text.appendCodePoint(chars[pos]);
            advance();
        }
        String digits = text.toString();
        boolean leadingZero = digits.length() > 1 && digits.charAt(0) == '0';
        if (pos + 1 < chars.length && chars[pos] == '.' && isDigit(chars[pos + 1])) {
            text.append('.');
            advance();
            while (pos < chars.length && isDigit(chars[pos])) {
                text.appendCodePoint(chars[pos]);
                advance();
            }
            floatLiteral(text.toString(), leadingZero, new Span(line, startColumn, line, column));
            return;
        }
        Span span = new Span(line, startColumn, line, column);
        if (leadingZero) {
            out.add(Diagnostic.of("E0104", "integer literal has a leading zero", file, span));
            digits = "0";
        } else {
            try {
                Long.parseLong(digits);
            } catch (NumberFormatException e) {
                out.add(Diagnostic.of("E0104", "integer literal is out of range", file, span)
                        .withExpected("0 to 9223372036854775807", digits));
                digits = "0";
            }
        }
        tokens.add(new Token(Kind.INT, digits, span));
    }

    private void floatLiteral(String literal, boolean leadingZero, Span span) {
        if (leadingZero) {
            out.add(Diagnostic.of("E0104", "decimal literal has a leading zero", file, span));
            literal = "0.0";
        } else if (Double.isInfinite(Double.parseDouble(literal))) {
            out.add(Diagnostic.of("E0104", "decimal literal is out of range for Float", file, span));
            literal = "0.0";
        }
        tokens.add(new Token(Kind.FLOAT, literal, span));
    }

    private void string() {
        int startColumn = column;
        advance();
        StringBuilder text = new StringBuilder();
        while (true) {
            if (pos >= chars.length || chars[pos] == '\n' || chars[pos] == '\r') {
                out.add(Diagnostic.of("E0102", "string literal is not terminated", file,
                        new Span(line, startColumn, line, column)));
                break;
            }
            int c = chars[pos];
            if (c == '"') {
                advance();
                break;
            }
            if (c == '\\') {
                int escapeColumn = column;
                advance();
                if (pos >= chars.length || chars[pos] == '\n' || chars[pos] == '\r') {
                    continue;
                }
                int escaped = chars[pos];
                advance();
                switch (escaped) {
                    case '"' -> text.append('"');
                    case '\\' -> text.append('\\');
                    case 'n' -> text.append('\n');
                    case 't' -> text.append('\t');
                    default -> out.add(Diagnostic.of("E0103",
                            "unknown escape '\\" + Character.toString(escaped) + "'", file,
                            new Span(line, escapeColumn, line, column))
                            .withExpected("\\\" \\\\ \\n \\t", "\\" + Character.toString(escaped)));
                }
                continue;
            }
            text.appendCodePoint(c);
            advance();
        }
        tokens.add(new Token(Kind.STRING, text.toString(), new Span(line, startColumn, line, column)));
    }

    private void symbol() {
        int c = chars[pos];
        int next = peek(1);
        Kind two = switch (c) {
            case '-' -> next == '>' ? Kind.ARROW : null;
            case '=' -> next == '=' ? Kind.EQ : next == '>' ? Kind.FAT_ARROW : null;
            case '!' -> next == '=' ? Kind.NE : null;
            case '<' -> next == '=' ? Kind.LE : null;
            case '>' -> next == '=' ? Kind.GE : null;
            case '&' -> next == '&' ? Kind.AND : null;
            case '|' -> next == '|' ? Kind.OR : null;
            default -> null;
        };
        int startColumn = column;
        if (two != null) {
            advance();
            advance();
            tokens.add(new Token(two, two.display(), new Span(line, startColumn, line, column)));
            return;
        }
        Kind one = switch (c) {
            case '{' -> Kind.LBRACE;
            case '}' -> Kind.RBRACE;
            case '(' -> Kind.LPAREN;
            case ')' -> Kind.RPAREN;
            case '[' -> Kind.LBRACKET;
            case ']' -> Kind.RBRACKET;
            case '<' -> Kind.LT;
            case '>' -> Kind.GT;
            case ',' -> Kind.COMMA;
            case ';' -> Kind.SEMI;
            case ':' -> Kind.COLON;
            case '.' -> Kind.DOT;
            case '=' -> Kind.ASSIGN;
            case '+' -> Kind.PLUS;
            case '-' -> Kind.MINUS;
            case '*' -> Kind.STAR;
            case '/' -> Kind.SLASH;
            case '%' -> Kind.PERCENT;
            case '!' -> Kind.NOT;
            default -> null;
        };
        advance();
        Span span = new Span(line, startColumn, line, column);
        if (one == null) {
            out.add(Diagnostic.of("E0101", "unexpected character '" + Character.toString(c) + "'", file, span));
            return;
        }
        tokens.add(new Token(one, one.display(), span));
    }

    private void advance() {
        pos++;
        column++;
    }

    private int peek(int offset) {
        return pos + offset < chars.length ? chars[pos + offset] : -1;
    }

    private static boolean isLetter(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }
}
