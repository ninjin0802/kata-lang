package dev.kata.compiler.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.diag.Span;
import dev.kata.compiler.syntax.Token.Kind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LexerTest {
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private List<Token> lex(String source) {
        return Lexer.lex("src/app/main.kata", source, diagnostics);
    }

    private List<Kind> kinds(String source) {
        return lex(source).stream().map(Token::kind).toList();
    }

    private void assertSingle(String code, Span span) {
        assertEquals(1, diagnostics.size(), diagnostics.toString());
        assertEquals(code, diagnostics.get(0).code());
        assertEquals(span, diagnostics.get(0).span());
        assertEquals("src/app/main.kata", diagnostics.get(0).file());
    }

    @Test
    void lexesKeywordsIdentifiersAndSymbols() {
        assertEquals(List.of(Kind.CLASS, Kind.UPPER_IDENT, Kind.LBRACE, Kind.VAR, Kind.LOWER_IDENT, Kind.COLON,
                Kind.UPPER_IDENT, Kind.ASSIGN, Kind.INT, Kind.SEMI, Kind.RBRACE, Kind.EOF),
                kinds("class Counter { var count: Int = 0; }"));
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void lexesMultiCharacterSymbols() {
        assertEquals(List.of(Kind.ARROW, Kind.EQ, Kind.NE, Kind.LE, Kind.GE, Kind.AND, Kind.OR, Kind.LT, Kind.GT,
                Kind.NOT, Kind.ASSIGN, Kind.MINUS, Kind.EOF),
                kinds("-> == != <= >= && || < > ! = -"));
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void lexesLoopKeywords() {
        assertEquals(List.of(Kind.WHILE, Kind.FOR, Kind.IN, Kind.EOF), kinds("while for in"));
    }

    @Test
    void stringTextHasEscapesResolved() {
        List<Token> tokens = lex("\"a\\\"b\\\\c\\nd\\te\"");
        assertEquals(Kind.STRING, tokens.get(0).kind());
        assertEquals("a\"b\\c\nd\te", tokens.get(0).text());
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void columnsCountCodePoints() {
        List<Token> japanese = lex("\"日本語\" x");
        assertEquals(new Span(1, 7, 1, 8), japanese.get(1).span());
        List<Token> emoji = lex("\"😀\" x");
        assertEquals(new Span(1, 5, 1, 6), emoji.get(1).span());
        assertEquals("😀", emoji.get(0).text());
    }

    @Test
    void crlfCountsAsOneLineBreakAndBomIsSkipped() {
        List<Token> tokens = lex("﻿a\r\nb");
        assertEquals(new Span(1, 1, 1, 2), tokens.get(0).span());
        assertEquals(new Span(2, 1, 2, 2), tokens.get(1).span());
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void commentRunsToEndOfLine() {
        assertEquals(List.of(Kind.LOWER_IDENT, Kind.LOWER_IDENT, Kind.EOF), kinds("a // 日本語 \"x\n b"));
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void underscoreIsE0101() {
        lex("foo_bar");
        assertSingle("E0101", new Span(1, 4, 1, 5));
    }

    @Test
    void singleAmpersandIsE0101() {
        lex("a & b");
        assertSingle("E0101", new Span(1, 3, 1, 4));
    }

    @Test
    void unterminatedStringIsE0102() {
        lex("\"abc\nx");
        assertSingle("E0102", new Span(1, 1, 1, 5));
    }

    @Test
    void unknownEscapeIsE0103() {
        lex("\"a\\qb\"");
        assertSingle("E0103", new Span(1, 3, 1, 5));
    }

    @Test
    void integerOutOfRangeIsE0104() {
        lex("9223372036854775808");
        assertSingle("E0104", new Span(1, 1, 1, 20));
    }

    @Test
    void leadingZeroIsE0104() {
        lex("007");
        assertSingle("E0104", new Span(1, 1, 1, 4));
    }

    @Test
    void largestIntegerIsAccepted() {
        List<Token> tokens = lex("9223372036854775807 0");
        assertEquals("9223372036854775807", tokens.get(0).text());
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void wordsReservedForLaterAreE0105() {
        for (String word : List.of("match", "interface", "enum", "break", "continue")) {
            diagnostics.clear();
            lex(word);
            assertSingle("E0105", new Span(1, 1, 1, 1 + word.length()));
        }
    }

    @Test
    void lexesFloatLiterals() {
        List<Token> tokens = lex("1.5 0.25 3.0 12.toString() 1.x");
        assertEquals(List.of(Kind.FLOAT, Kind.FLOAT, Kind.FLOAT, Kind.INT, Kind.DOT, Kind.LOWER_IDENT, Kind.LPAREN,
                Kind.RPAREN, Kind.INT, Kind.DOT, Kind.LOWER_IDENT, Kind.EOF),
                tokens.stream().map(Token::kind).toList());
        assertEquals("1.5", tokens.get(0).text());
        assertEquals(new Span(1, 5, 1, 9), tokens.get(1).span());
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void floatLiteralWithLeadingZeroOrOutOfRangeIsE0104() {
        lex("007.5");
        assertSingle("E0104", new Span(1, 1, 1, 6));
        diagnostics.clear();
        lex("1" + "0".repeat(400) + ".0");
        assertEquals("E0104", diagnostics.get(0).code());
    }

    @Test
    void alwaysEndsWithEof() {
        assertEquals(List.of(Kind.EOF), kinds(""));
        assertEquals(List.of(Kind.EOF), kinds("   \n\t "));
    }
}
