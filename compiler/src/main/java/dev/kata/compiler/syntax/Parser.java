package dev.kata.compiler.syntax;

import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.diag.Span;
import dev.kata.compiler.syntax.Ast.BinaryOp;
import dev.kata.compiler.syntax.Token.Kind;
import java.util.ArrayList;
import java.util.List;

/** Recursive-descent parser for the grammar in spec chapter 03. */
public final class Parser {
    /** Thrown after an E0201 has been reported, to unwind to the nearest recovery point. */
    private static final class Bail extends RuntimeException {
        Bail() {
            super(null, null, false, false);
        }
    }

    private final String file;
    private final List<Token> tokens;
    private final List<Diagnostic> out;
    private int pos;

    private Parser(String file, List<Token> tokens, List<Diagnostic> out) {
        this.file = file;
        this.tokens = tokens;
        this.out = out;
    }

    public static Ast.File parse(String file, List<Token> tokens, List<Diagnostic> out) {
        return new Parser(file, tokens, out).file();
    }

    // ---- file level

    private Ast.File file() {
        Ast.ModulePath module = null;
        Span moduleDeclSpan = null;
        List<Ast.Import> imports = new ArrayList<>();
        List<Ast.ClassDecl> classes = new ArrayList<>();
        Ast.AppDecl app = null;
        try {
            Token start = expect(Kind.MODULE);
            module = modulePath();
            semicolon();
            moduleDeclSpan = Span.between(start.span(), prev().span());
        } catch (Bail bail) {
            syncTopLevel();
        }
        while (at(Kind.IMPORT)) {
            int before = pos;
            try {
                imports.add(importDecl());
            } catch (Bail bail) {
                syncTopLevel();
                ensureProgress(before);
            }
        }
        while (!at(Kind.EOF)) {
            int before = pos;
            try {
                if (at(Kind.CLASS)) {
                    if (app != null) {
                        throw unexpected("end of file");
                    }
                    classes.add(classDecl());
                } else if (at(Kind.APP)) {
                    if (app != null) {
                        throw unexpected("end of file");
                    }
                    app = appDecl();
                } else {
                    throw unexpected(app == null ? "class or app" : "end of file");
                }
            } catch (Bail bail) {
                syncTopLevel();
                ensureProgress(before);
            }
        }
        return new Ast.File(file, module, moduleDeclSpan, imports, classes, app);
    }

    private Ast.ModulePath modulePath() {
        Token first = pathPart();
        List<String> parts = new ArrayList<>(List.of(first.text()));
        while (at(Kind.DOT) && isPathPart(peekKind(1))) {
            next();
            parts.add(next().text());
        }
        return new Ast.ModulePath(parts, Span.between(first.span(), prev().span()));
    }

    /** `app` is a keyword, but it is allowed as a part of a module path (module app.counter). */
    private static boolean isPathPart(Kind kind) {
        return kind == Kind.LOWER_IDENT || kind == Kind.APP;
    }

    private Token pathPart() {
        if (isPathPart(peek().kind())) {
            return next();
        }
        throw unexpected(Kind.LOWER_IDENT.display());
    }

    private Ast.Import importDecl() {
        Token start = expect(Kind.IMPORT);
        Ast.ModulePath module = modulePath();
        expect(Kind.DOT);
        Token name = expect(Kind.UPPER_IDENT);
        semicolon();
        return new Ast.Import(module, name.text(), name.span(), Span.between(start.span(), prev().span()));
    }

    private Ast.ClassDecl classDecl() {
        Token start = expect(Kind.CLASS);
        Token name = expect(Kind.UPPER_IDENT);
        List<Ast.TypeRef> interfaces = new ArrayList<>();
        if (accept(Kind.IMPLEMENTS)) {
            interfaces.add(type());
            while (accept(Kind.COMMA)) {
                interfaces.add(type());
            }
        }
        expect(Kind.LBRACE);
        List<Ast.FieldDecl> fields = new ArrayList<>();
        List<Ast.MethodDecl> methods = new ArrayList<>();
        while (!at(Kind.RBRACE) && !at(Kind.EOF)) {
            int before = pos;
            try {
                if (at(Kind.LET) || at(Kind.VAR)) {
                    if (!methods.isEmpty()) {
                        // Reported once; the field is still parsed so that it does not cascade.
                        unexpected("fn or }");
                    }
                    fields.add(fieldDecl());
                } else if (at(Kind.FN)) {
                    methods.add(methodDecl());
                } else {
                    throw unexpected(methods.isEmpty() ? "let, var, fn or }" : "fn or }");
                }
            } catch (Bail bail) {
                syncMember();
                ensureProgress(before);
            }
        }
        expect(Kind.RBRACE);
        return new Ast.ClassDecl(name.text(), name.span(), interfaces, fields, methods,
                Span.between(start.span(), prev().span()));
    }

    private Ast.FieldDecl fieldDecl() {
        Token start = next();
        Token name = expect(Kind.LOWER_IDENT);
        expect(Kind.COLON);
        Ast.TypeRef type = type();
        Ast.Expr init = accept(Kind.ASSIGN) ? expr() : null;
        semicolon();
        return new Ast.FieldDecl(start.kind() == Kind.VAR, name.text(), name.span(), type, init,
                Span.between(start.span(), prev().span()));
    }

    private Ast.MethodDecl methodDecl() {
        Token start = expect(Kind.FN);
        Token name = expect(Kind.LOWER_IDENT);
        expect(Kind.LPAREN);
        expect(Kind.SELF);
        List<Ast.Param> params = new ArrayList<>();
        while (accept(Kind.COMMA)) {
            if (at(Kind.RPAREN)) {
                break;
            }
            Token paramName = expect(Kind.LOWER_IDENT);
            expect(Kind.COLON);
            Ast.TypeRef type = type();
            params.add(new Ast.Param(paramName.text(), paramName.span(), type,
                    Span.between(paramName.span(), prev().span())));
        }
        expect(Kind.RPAREN);
        expect(Kind.ARROW);
        Ast.TypeRef returnType = type();
        Ast.Block body = block();
        return new Ast.MethodDecl(name.text(), name.span(), params, returnType, body,
                Span.between(start.span(), prev().span()));
    }

    private Ast.TypeRef type() {
        Token name = expect(Kind.UPPER_IDENT);
        List<Ast.TypeRef> args = new ArrayList<>();
        if (accept(Kind.LT)) {
            args.add(type());
            while (accept(Kind.COMMA)) {
                args.add(type());
            }
            closeTypeArguments();
        }
        return new Ast.TypeRef(name.text(), args, Span.between(name.span(), prev().span()));
    }

    /** Accepts `>`; a `>=` here is the closing bracket followed by `=` (as in `List<Int>= []`). */
    private void closeTypeArguments() {
        if (at(Kind.GE)) {
            Span span = peek().span();
            tokens.set(pos, new Token(Kind.ASSIGN, Kind.ASSIGN.display(),
                    new Span(span.line(), span.column() + 1, span.endLine(), span.endColumn())));
            return;
        }
        expect(Kind.GT);
    }

    private Ast.AppDecl appDecl() {
        Token start = expect(Kind.APP);
        expect(Kind.LBRACE);
        List<Ast.Named> inits = namedList(Kind.RBRACE, false);
        expect(Kind.RBRACE);
        return new Ast.AppDecl(inits, Span.between(start.span(), prev().span()));
    }

    // ---- statements

    private Ast.Block block() {
        Token start = expect(Kind.LBRACE);
        List<Ast.Stmt> stmts = new ArrayList<>();
        while (!at(Kind.RBRACE) && !at(Kind.EOF)) {
            int before = pos;
            try {
                stmts.add(stmt());
            } catch (Bail bail) {
                syncStatement();
                ensureProgress(before);
            }
        }
        expect(Kind.RBRACE);
        return new Ast.Block(stmts, Span.between(start.span(), prev().span()));
    }

    private Ast.Stmt stmt() {
        Token start = peek();
        switch (start.kind()) {
            case LET, VAR -> {
                next();
                Token name = expect(Kind.LOWER_IDENT);
                expect(Kind.COLON);
                Ast.TypeRef type = type();
                expect(Kind.ASSIGN);
                Ast.Expr value = expr();
                semicolon();
                Span span = Span.between(start.span(), prev().span());
                return start.kind() == Kind.LET
                        ? new Ast.Let(name.text(), name.span(), type, value, span)
                        : new Ast.Var(name.text(), name.span(), type, value, span);
            }
            case RETURN -> {
                next();
                Ast.Expr value = at(Kind.SEMI) || at(Kind.RBRACE) ? null : expr();
                semicolon();
                return new Ast.Return(value, Span.between(start.span(), prev().span()));
            }
            case IF -> {
                return ifStmt();
            }
            case WHILE -> {
                next();
                expect(Kind.LPAREN);
                Ast.Expr cond = expr();
                expect(Kind.RPAREN);
                Ast.Block body = block();
                return new Ast.While(cond, body, Span.between(start.span(), prev().span()));
            }
            case FOR -> {
                next();
                expect(Kind.LPAREN);
                Token name = expect(Kind.LOWER_IDENT);
                expect(Kind.COLON);
                Ast.TypeRef type = type();
                expect(Kind.IN);
                Ast.Expr iterable = expr();
                expect(Kind.RPAREN);
                Ast.Block body = block();
                return new Ast.For(name.text(), name.span(), type, iterable, body,
                        Span.between(start.span(), prev().span()));
            }
            default -> {
                Ast.Expr first = expr();
                if (at(Kind.ASSIGN)) {
                    if (!(first instanceof Ast.Name) && !(first instanceof Ast.Member)) {
                        throw unexpected(";");
                    }
                    next();
                    Ast.Expr value = expr();
                    semicolon();
                    return new Ast.Assign(first, value, Span.between(start.span(), prev().span()));
                }
                semicolon();
                return new Ast.ExprStmt(first, Span.between(start.span(), prev().span()));
            }
        }
    }

    private Ast.If ifStmt() {
        Token start = expect(Kind.IF);
        expect(Kind.LPAREN);
        Ast.Expr cond = expr();
        expect(Kind.RPAREN);
        Ast.Block then = block();
        Ast.Stmt orElse = null;
        if (accept(Kind.ELSE)) {
            orElse = at(Kind.IF) ? ifStmt() : block();
        }
        return new Ast.If(cond, then, orElse, Span.between(start.span(), prev().span()));
    }

    // ---- expressions

    private Ast.Expr expr() {
        Ast.Expr first = unary();
        BinaryOp op = binaryOp(peek().kind());
        if (op == null) {
            return first;
        }
        List<Ast.Expr> operands = new ArrayList<>(List.of(first));
        while (true) {
            BinaryOp nextOp = binaryOp(peek().kind());
            if (nextOp == null) {
                break;
            }
            Token opToken = next();
            if (nextOp != op) {
                out.add(Diagnostic.of("E0203", "operators '" + op.symbol + "' and '" + nextOp.symbol
                        + "' cannot be mixed without parentheses", file, opToken.span()));
            } else if (operands.size() >= 2 && !op.chainable) {
                out.add(Diagnostic.of("E0204", "operator '" + op.symbol
                        + "' cannot be chained; add parentheses", file, opToken.span()));
            }
            operands.add(unary());
        }
        return new Ast.Binary(op, operands, Span.between(first.span(), prev().span()));
    }

    private static BinaryOp binaryOp(Kind kind) {
        return switch (kind) {
            case PLUS -> BinaryOp.ADD;
            case MINUS -> BinaryOp.SUB;
            case STAR -> BinaryOp.MUL;
            case SLASH -> BinaryOp.DIV;
            case PERCENT -> BinaryOp.REM;
            case EQ -> BinaryOp.EQ;
            case NE -> BinaryOp.NE;
            case LT -> BinaryOp.LT;
            case LE -> BinaryOp.LE;
            case GT -> BinaryOp.GT;
            case GE -> BinaryOp.GE;
            case AND -> BinaryOp.AND;
            case OR -> BinaryOp.OR;
            default -> null;
        };
    }

    private Ast.Expr unary() {
        if (at(Kind.MINUS) || at(Kind.NOT)) {
            Token op = next();
            Ast.Expr operand = unary();
            return new Ast.Unary(op.kind() == Kind.MINUS ? Ast.UnaryOp.NEG : Ast.UnaryOp.NOT, operand,
                    Span.between(op.span(), operand.span()));
        }
        return postfix();
    }

    private Ast.Expr postfix() {
        Ast.Expr result = primary();
        while (at(Kind.DOT)) {
            next();
            Token name = expect(Kind.LOWER_IDENT);
            if (at(Kind.LPAREN)) {
                Token open = next();
                List<Ast.Named> args = namedList(Kind.RPAREN, true);
                expect(Kind.RPAREN);
                result = new Ast.Call(result, name.text(), name.span(), args,
                        Span.between(open.span(), prev().span()), Span.between(result.span(), prev().span()));
            } else {
                result = new Ast.Member(result, name.text(), name.span(),
                        Span.between(result.span(), name.span()));
            }
        }
        return result;
    }

    private Ast.Expr primary() {
        Token token = peek();
        switch (token.kind()) {
            case INT -> {
                next();
                return new Ast.IntLit(Long.parseLong(token.text()), token.span());
            }
            case FLOAT -> {
                next();
                return new Ast.FloatLit(Double.parseDouble(token.text()), token.span());
            }
            case STRING -> {
                next();
                return new Ast.StringLit(token.text(), token.span());
            }
            case TRUE, FALSE -> {
                next();
                return new Ast.BoolLit(token.kind() == Kind.TRUE, token.span());
            }
            case SELF -> {
                next();
                return new Ast.SelfRef(token.span());
            }
            case LOWER_IDENT -> {
                next();
                return new Ast.Name(token.text(), token.span());
            }
            case UPPER_IDENT -> {
                next();
                Token open = expect(Kind.LBRACE);
                List<Ast.Named> inits = namedList(Kind.RBRACE, false);
                expect(Kind.RBRACE);
                return new Ast.Construct(token.text(), token.span(), inits,
                        Span.between(open.span(), prev().span()), Span.between(token.span(), prev().span()));
            }
            case LBRACKET -> {
                next();
                List<Ast.Expr> items = new ArrayList<>();
                while (!at(Kind.RBRACKET)) {
                    items.add(expr());
                    if (!accept(Kind.COMMA)) {
                        break;
                    }
                }
                expect(Kind.RBRACKET);
                return new Ast.ListLit(items, Span.between(token.span(), prev().span()));
            }
            case LPAREN -> {
                next();
                Ast.Expr inner = expr();
                expect(Kind.RPAREN);
                return new Ast.Paren(inner, Span.between(token.span(), prev().span()));
            }
            default -> throw unexpected("expression");
        }
    }

    /** Parses `name: expr` pairs up to (not including) the closing token. */
    private List<Ast.Named> namedList(Kind close, boolean isArguments) {
        List<Ast.Named> result = new ArrayList<>();
        while (!at(close)) {
            if (at(Kind.LOWER_IDENT) && peekKind(1) == Kind.COLON) {
                Token name = next();
                next();
                Ast.Expr value = expr();
                result.add(new Ast.Named(name.text(), name.span(), value, Span.between(name.span(), value.span())));
            } else if (isArguments) {
                Ast.Expr value = expr();
                out.add(Diagnostic.of("E0205", "arguments must be named: write 'name: value'", file, value.span()));
            } else {
                expect(Kind.LOWER_IDENT);
                expect(Kind.COLON);
            }
            if (!accept(Kind.COMMA)) {
                break;
            }
        }
        return result;
    }

    // ---- recovery

    /** Skips to just after the next `;` or balanced `{...}`, or to an unmatched `}`. */
    private void syncStatement() {
        int depth = 0;
        while (!at(Kind.EOF)) {
            Kind kind = peek().kind();
            if (kind == Kind.LBRACE) {
                depth++;
            } else if (kind == Kind.RBRACE) {
                if (depth == 0) {
                    return;
                }
                depth--;
                if (depth == 0) {
                    next();
                    return;
                }
            } else if (kind == Kind.SEMI && depth == 0) {
                next();
                return;
            }
            next();
        }
    }

    /** Skips to the next member start or the `}` closing the class. */
    private void syncMember() {
        int depth = 0;
        while (!at(Kind.EOF)) {
            Kind kind = peek().kind();
            if (depth == 0 && (kind == Kind.FN || kind == Kind.LET || kind == Kind.VAR || kind == Kind.RBRACE)) {
                return;
            }
            if (kind == Kind.LBRACE) {
                depth++;
            } else if (kind == Kind.RBRACE) {
                depth--;
            }
            next();
        }
    }

    /** Skips to the next top-level declaration. */
    private void syncTopLevel() {
        int depth = 0;
        while (!at(Kind.EOF)) {
            Kind kind = peek().kind();
            if (depth == 0 && (kind == Kind.CLASS || kind == Kind.APP || kind == Kind.IMPORT)) {
                return;
            }
            if (kind == Kind.LBRACE) {
                depth++;
            } else if (kind == Kind.RBRACE && depth > 0) {
                depth--;
            }
            next();
        }
    }

    private void ensureProgress(int before) {
        if (pos == before && !at(Kind.EOF)) {
            next();
        }
    }

    // ---- token helpers

    private Token peek() {
        return tokens.get(pos);
    }

    private Kind peekKind(int offset) {
        return tokens.get(Math.min(pos + offset, tokens.size() - 1)).kind();
    }

    private Token prev() {
        return tokens.get(Math.max(pos - 1, 0));
    }

    private boolean at(Kind kind) {
        return peek().kind() == kind;
    }

    private Token next() {
        Token token = tokens.get(pos);
        if (pos < tokens.size() - 1) {
            pos++;
        }
        return token;
    }

    private boolean accept(Kind kind) {
        if (at(kind)) {
            next();
            return true;
        }
        return false;
    }

    private Token expect(Kind kind) {
        if (at(kind)) {
            return next();
        }
        throw unexpected(kind.display());
    }

    /** A missing `;` is reported with a fix and parsing continues as if it were there. */
    private void semicolon() {
        if (accept(Kind.SEMI)) {
            return;
        }
        Span point = prev().span().endPoint();
        out.add(Diagnostic.of("E0202", "missing ';'", file, point)
                .withExpected(";", peek().kind().display())
                .withFix(point, ";"));
    }

    private Bail unexpected(String expected) {
        Token token = peek();
        out.add(Diagnostic.of("E0201", "unexpected " + token.kind().display() + ", expected " + expected, file,
                token.span()).withExpected(expected, token.kind().display()));
        return new Bail();
    }
}
