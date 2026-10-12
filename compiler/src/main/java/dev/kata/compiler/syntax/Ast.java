package dev.kata.compiler.syntax;

import dev.kata.compiler.diag.Span;
import java.util.List;

/** The syntax tree. Node names follow the grammar in spec chapter 03. */
public final class Ast {
    private Ast() {}

    public record ModulePath(List<String> parts, Span span) {
        public String name() {
            return String.join(".", parts);
        }
    }

    public record Import(ModulePath module, String name, Span nameSpan, Span span) {}

    /** module and app may be null when the file did not parse. */
    public record File(String path, ModulePath module, Span moduleDeclSpan, List<Import> imports,
            List<ClassDecl> classes, AppDecl app) {}

    public record ClassDecl(String name, Span nameSpan, List<TypeRef> interfaces, List<FieldDecl> fields,
            List<MethodDecl> methods, Span span) {}

    /** init is null when the field is supplied by the construction expression. */
    public record FieldDecl(boolean mutable, String name, Span nameSpan, TypeRef type, Expr init, Span span) {}

    public record Param(String name, Span nameSpan, TypeRef type, Span span) {}

    public record MethodDecl(String name, Span nameSpan, List<Param> params, TypeRef returnType, Block body,
            Span span) {}

    public record TypeRef(String name, List<TypeRef> args, Span span) {}

    /** A `name: expr` pair: a call argument, a construction field, or an app entry. */
    public record Named(String name, Span nameSpan, Expr value, Span span) {}

    public record AppDecl(List<Named> inits, Span span) {}

    public sealed interface Stmt {
        Span span();
    }

    public record Block(List<Stmt> stmts, Span span) implements Stmt {}

    public record Let(String name, Span nameSpan, TypeRef type, Expr value, Span span) implements Stmt {}

    public record Var(String name, Span nameSpan, TypeRef type, Expr value, Span span) implements Stmt {}

    public record Assign(Expr target, Expr value, Span span) implements Stmt {}

    /** value is null for a bare `return;`. */
    public record Return(Expr value, Span span) implements Stmt {}

    /** orElse is a Block, an If, or null. */
    public record If(Expr cond, Block then, Stmt orElse, Span span) implements Stmt {}

    public record While(Expr cond, Block body, Span span) implements Stmt {}

    public record For(String name, Span nameSpan, TypeRef type, Expr iterable, Block body, Span span)
            implements Stmt {}

    public record ExprStmt(Expr expr, Span span) implements Stmt {}

    public sealed interface Expr {
        Span span();
    }

    public record IntLit(long value, Span span) implements Expr {}

    public record FloatLit(double value, Span span) implements Expr {}

    public record StringLit(String value, Span span) implements Expr {}

    public record BoolLit(boolean value, Span span) implements Expr {}

    public record SelfRef(Span span) implements Expr {}

    public record Name(String name, Span span) implements Expr {}

    public record Member(Expr target, String name, Span nameSpan, Span span) implements Expr {}

    public record Call(Expr target, String name, Span nameSpan, List<Named> args, Span argsSpan, Span span)
            implements Expr {}

    public record Construct(String type, Span typeSpan, List<Named> inits, Span initsSpan, Span span)
            implements Expr {}

    public record ListLit(List<Expr> items, Span span) implements Expr {}

    public record Unary(UnaryOp op, Expr operand, Span span) implements Expr {}

    /** One operator applied across two or more operands (only + * && || take more than two). */
    public record Binary(BinaryOp op, List<Expr> operands, Span span) implements Expr {}

    public record Paren(Expr inner, Span span) implements Expr {}

    public enum UnaryOp {
        NEG("-"), NOT("!");

        public final String symbol;

        UnaryOp(String symbol) {
            this.symbol = symbol;
        }
    }

    public enum BinaryOp {
        ADD("+", true), SUB("-", false), MUL("*", true), DIV("/", false), REM("%", false),
        EQ("==", false), NE("!=", false), LT("<", false), LE("<=", false), GT(">", false), GE(">=", false),
        AND("&&", true), OR("||", true);

        public final String symbol;
        public final boolean chainable;

        BinaryOp(String symbol, boolean chainable) {
            this.symbol = symbol;
            this.chainable = chainable;
        }
    }
}
