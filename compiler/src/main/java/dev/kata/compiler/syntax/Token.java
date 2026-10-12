package dev.kata.compiler.syntax;

import dev.kata.compiler.diag.Span;

/** A lexical token. For STRING, text is the content with escapes resolved. */
public record Token(Kind kind, String text, Span span) {
    public enum Kind {
        LOWER_IDENT("identifier"), UPPER_IDENT("type name"), INT("integer"), STRING("string"),
        MODULE("module"), IMPORT("import"), CLASS("class"), IMPLEMENTS("implements"), LET("let"), VAR("var"),
        FN("fn"), SELF("self"), RETURN("return"), IF("if"), ELSE("else"), WHILE("while"), FOR("for"), IN("in"),
        TRUE("true"), FALSE("false"), APP("app"),
        LBRACE("{"), RBRACE("}"), LPAREN("("), RPAREN(")"), LBRACKET("["), RBRACKET("]"),
        LT("<"), GT(">"), COMMA(","), SEMI(";"), COLON(":"), DOT("."), ASSIGN("="), ARROW("->"),
        PLUS("+"), MINUS("-"), STAR("*"), SLASH("/"), PERCENT("%"),
        EQ("=="), NE("!="), LE("<="), GE(">="), AND("&&"), OR("||"), NOT("!"),
        EOF("end of file");

        private final String display;

        Kind(String display) {
            this.display = display;
        }

        /** How the token is named in diagnostics. */
        public String display() {
            return display;
        }
    }
}
