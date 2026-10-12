package dev.kata.compiler.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.diag.Fix;
import dev.kata.compiler.diag.Span;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParserTest {
    static final String COUNTER = ParserTestSources.COUNTER;

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private Ast.File parse(String source) {
        List<Token> tokens = Lexer.lex("src/app/main.kata", source, diagnostics);
        return Parser.parse("src/app/main.kata", tokens, diagnostics);
    }

    /** Parses statements placed in a method body. The body starts on line 4, column 1. */
    private Ast.File parseBody(String body) {
        return parse("module app.main;\nclass A {\n  fn m(self) -> Unit {\n" + body + "\n  }\n}\n");
    }

    private List<String> codes() {
        return diagnostics.stream().map(Diagnostic::code).toList();
    }

    private void assertClean() {
        assertTrue(diagnostics.isEmpty(), diagnostics.toString());
    }

    @Test
    void parsesTheCounterExample() {
        Ast.File file = parse(COUNTER);
        assertClean();
        assertEquals("app.counter", file.module().name());
        assertEquals(5, file.imports().size());
        assertEquals(1, file.classes().size());
        Ast.ClassDecl counter = file.classes().get(0);
        assertEquals("Counter", counter.name());
        assertEquals("Screen", counter.interfaces().get(0).name());
        assertEquals(1, counter.fields().size());
        assertTrue(counter.fields().get(0).mutable());
        assertEquals(3, counter.methods().size());
        assertNotNull(file.app());
        assertEquals(List.of("id", "name", "root"), file.app().inits().stream().map(Ast.Named::name).toList());
    }

    @Test
    void sameChainableOperatorMayRepeat() {
        Ast.File file = parseBody("let x: Int = a + b + c;");
        assertClean();
        Ast.Let let = (Ast.Let) file.classes().get(0).methods().get(0).body().stmts().get(0);
        Ast.Binary binary = assertInstanceOf(Ast.Binary.class, let.value());
        assertEquals(3, binary.operands().size());
    }

    @Test
    void mixedOperatorsAreE0203() {
        parseBody("let x: Int = a + b * c;");
        assertEquals(List.of("E0203"), codes());
        assertEquals(new Span(4, 20, 4, 21), diagnostics.get(0).span());
    }

    @Test
    void comparisonMixedWithLogicIsE0203() {
        parseBody("let x: Bool = a < b && c < d;");
        assertEquals("E0203", codes().get(0));
    }

    @Test
    void repeatedNonChainableOperatorIsE0204() {
        parseBody("let x: Int = a - b - c;");
        assertEquals(List.of("E0204"), codes());
        assertEquals(new Span(4, 20, 4, 21), diagnostics.get(0).span());
    }

    @Test
    void unaryMinusBindsToItsOperand() {
        parseBody("let x: Int = -a + b;");
        assertClean();
    }

    @Test
    void parenthesesAllowMixing() {
        parseBody("let x: Bool = (a < b) && ((c + (d * e)) < f);");
        assertClean();
    }

    @Test
    void positionalArgumentIsE0205() {
        parseBody("x.f(1);");
        assertEquals(List.of("E0205"), codes());
        assertEquals(new Span(4, 5, 4, 6), diagnostics.get(0).span());
    }

    @Test
    void missingSemicolonIsE0202WithFix() {
        parseBody("let x: Int = 1\nlet y: Int = 2;");
        assertEquals(List.of("E0202"), codes());
        assertEquals(new Fix(new Span(4, 15, 4, 15), ";"), diagnostics.get(0).fix());
        assertEquals(new Span(4, 15, 4, 15), diagnostics.get(0).span());
    }

    @Test
    void ifWithoutParenthesesIsE0201() {
        parseBody("if x { }");
        assertEquals(List.of("E0201"), codes());
        assertEquals("(", diagnostics.get(0).expected());
        assertEquals("identifier", diagnostics.get(0).found());
    }

    @Test
    void twoBrokenStatementsGiveTwoDiagnostics() {
        parseBody("let x Int = 1;\nlet y: Int = 2;\nlet z Int = 3;");
        assertEquals(List.of("E0201", "E0201"), codes());
        assertEquals(4, diagnostics.get(0).span().line());
        assertEquals(6, diagnostics.get(1).span().line());
    }

    @Test
    void unclosedAndDegenerateInputsTerminateWithDiagnostics() {
        for (String source : List.of("module app.main;\nclass A {", "", "}}}}", "module app.main;\nclass A { fn m(self",
                "module app.main;\nclass A { fn m(self) -> Unit { if (", "module app.main; app {", "class")) {
            diagnostics.clear();
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> parse(source));
            assertTrue(!diagnostics.isEmpty(), "no diagnostics for: " + source);
        }
    }

    @Test
    void trailingCommasAreAccepted() {
        parse("""
                module app.main;
                class A {
                  fn m(self, a: Int, b: Int,) -> Unit {
                    self.m(a: 1, b: 2,);
                    let p: P = P { x: 1, y: 2, };
                    let xs: List<Int> = [1, 2,];
                  }
                }
                app { id: "a.b", name: "n", root: A {}, }
                """);
        assertClean();
    }

    @Test
    void closingAngleBracketDirectlyBeforeEqualsIsAccepted() {
        Ast.File file = parseBody("var xs: List<Int>= [];\nlet ok: Bool = 1 >= 0;");
        assertClean();
        Ast.Var var = (Ast.Var) file.classes().get(0).methods().get(0).body().stmts().get(0);
        assertEquals("List", var.type().name());
        assertInstanceOf(Ast.ListLit.class, var.value());
    }

    @Test
    void parsesEnumDeclarationsConstructionAndMatch() {
        Ast.File file = parse("""
                module app.main;
                enum Shape {
                  Dot,
                  Circle { radius: Int },
                  Box { width: Int, height: Int, },
                }
                class A {
                  fn area(self, shape: Shape) -> Int {
                    let dot: Shape = Shape.Dot;
                    let box: Shape = Shape.Box { width: 2, height: 3 };
                    match (shape) {
                      Shape.Dot => {
                        return 0;
                      }
                      Shape.Circle { radius } => {
                        return radius * radius * 3;
                      }
                      Shape.Box { width, height } => {
                        return width * height;
                      }
                    }
                  }
                }
                """);
        assertClean();
        Ast.EnumDecl shape = file.enums().get(0);
        assertEquals("Shape", shape.name());
        assertEquals(List.of("Dot", "Circle", "Box"), shape.variants().stream().map(Ast.VariantDecl::name).toList());
        assertEquals(2, shape.variants().get(2).fields().size());
        List<Ast.Stmt> stmts = file.classes().get(0).methods().get(0).body().stmts();
        Ast.EnumConstruct dot = assertInstanceOf(Ast.EnumConstruct.class, ((Ast.Let) stmts.get(0)).value());
        assertEquals("Dot", dot.variant());
        assertEquals(0, dot.inits().size());
        assertEquals(2, ((Ast.EnumConstruct) ((Ast.Let) stmts.get(1)).value()).inits().size());
        Ast.Match match = assertInstanceOf(Ast.Match.class, stmts.get(2));
        assertEquals(3, match.arms().size());
        assertEquals(List.of("width", "height"), match.arms().get(2).bindings().stream().map(Ast.Name::name).toList());
    }

    @Test
    void brokenMatchArmsAreReportedOnce() {
        parseBody("match (x) {\nShape.Dot {\n}\n}");
        assertEquals(List.of("E0201"), codes());
    }

    @Test
    void parsesFunctionLiterals() {
        Ast.File file = parseBody("let a: Action = fn() -> Unit {\n};\n"
                + "let f: Fn<Int, Bool> = fn(x: Int) -> Bool {\nreturn x > 0;\n};\nxs.filter(keep: fn(x: Int) -> Bool {\nreturn true;\n});");
        assertClean();
        List<Ast.Stmt> stmts = file.classes().get(0).methods().get(0).body().stmts();
        Ast.Lambda action = assertInstanceOf(Ast.Lambda.class, ((Ast.Let) stmts.get(0)).value());
        assertEquals(0, action.params().size());
        Ast.Lambda test = assertInstanceOf(Ast.Lambda.class, ((Ast.Let) stmts.get(1)).value());
        assertEquals("x", test.params().get(0).name());
        assertEquals("Bool", test.returnType().name());
        assertEquals(2, ((Ast.Let) stmts.get(1)).type().args().size());
    }

    @Test
    void parsesLoops() {
        Ast.File file = parseBody("while (c) { }\nfor (x: Int in xs) { }");
        assertClean();
        List<Ast.Stmt> stmts = file.classes().get(0).methods().get(0).body().stmts();
        assertInstanceOf(Ast.While.class, stmts.get(0));
        Ast.For loop = assertInstanceOf(Ast.For.class, stmts.get(1));
        assertEquals("x", loop.name());
        assertEquals("Int", loop.type().name());
    }

    @Test
    void parsesElseIfChainAndReturnForms() {
        Ast.File file = parseBody("if (a) { return; } else if (b) { return; } else { return; }");
        assertClean();
        Ast.If first = (Ast.If) file.classes().get(0).methods().get(0).body().stmts().get(0);
        Ast.If second = assertInstanceOf(Ast.If.class, first.orElse());
        assertInstanceOf(Ast.Block.class, second.orElse());
    }

    @Test
    void fieldsMustComeBeforeMethods() {
        parse("module app.main;\nclass A {\n  fn m(self) -> Unit { }\n  let x: Int;\n}\n");
        assertEquals(List.of("E0201"), codes());
        assertEquals(4, diagnostics.get(0).span().line());
    }

    @Test
    void memberAccessAndMethodReferenceAreDistinguishedFromCalls() {
        Ast.File file = parseBody("let a: Action = self.increment;\nself.increment();");
        assertClean();
        List<Ast.Stmt> stmts = file.classes().get(0).methods().get(0).body().stmts();
        assertInstanceOf(Ast.Member.class, ((Ast.Let) stmts.get(0)).value());
        assertInstanceOf(Ast.Call.class, ((Ast.ExprStmt) stmts.get(1)).expr());
    }

    @Test
    void spansCoverTheWholeNode() {
        Ast.File file = parseBody("let x: Int = a + b;");
        Ast.Let let = (Ast.Let) file.classes().get(0).methods().get(0).body().stmts().get(0);
        assertEquals(new Span(4, 1, 4, 20), let.span());
        assertEquals(new Span(4, 14, 4, 19), let.value().span());
    }
}
