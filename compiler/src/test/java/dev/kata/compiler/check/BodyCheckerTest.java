package dev.kata.compiler.check;

import static dev.kata.compiler.check.CheckSupport.check;
import static dev.kata.compiler.check.CheckSupport.checkFiles;
import static dev.kata.compiler.check.CheckSupport.wrap;
import static dev.kata.compiler.check.CheckSupport.wrapBody;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.diag.Fix;
import dev.kata.compiler.diag.Span;
import dev.kata.compiler.syntax.Ast;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BodyCheckerTest {
    /** A class with members to exercise; its body starts on line 6 and `m`'s statements on line 30. */
    private static String withMembers(String statements) {
        return wrap("""
                class P {
                  let x: Int;
                  let y: Int;
                  var z: Int = 0;
                }
                class A {
                  let fixed: Int = 1;
                  var count: Int = 0;
                  var name: String = "";
                  fn add(self, a: Int, b: Int) -> Int {
                    return a + b;
                  }
                  fn get(self) -> Int {
                    return self.count;
                  }
                  fn bump(self) -> Unit {
                    self.count = self.count + 1;
                  }
                  fn setCount(self, value: Int) -> Unit {
                    self.count = value;
                  }
                  fn setName(self, value: String) -> Unit {
                    self.name = value;
                  }
                  fn m(self, p: Int, other: P) -> Unit {
                """ + statements + "\n  }\n}\n");
    }

    @Test
    void counterExampleIsClean() {
        String counter = dev.kata.compiler.syntax.ParserTestSources.COUNTER;
        checkFiles(Map.of("src/app/counter.kata", counter)).assertClean();
    }

    @Test
    void fixtureIsClean() {
        check(withMembers("")).assertClean();
    }

    @Test
    void recordsExpressionTypes() {
        CheckSupport result = check(wrapBody("let x: Int = 1 + 2;"));
        result.assertClean();
        Ast.ClassDecl a = result.program.files().get(0).classes().get(0);
        Ast.Let let = (Ast.Let) a.methods().get(0).body().stmts().get(0);
        assertEquals(Type.INT, result.program.typeOf(let.value()));
    }

    // ---- names

    @Test
    void bareFieldNameIsE0304WithFix() {
        Diagnostic d = check(withMembers("let n: Int = count;")).single("E0304");
        assertEquals(new Span(30, 14, 30, 19), d.span());
        assertEquals(new Fix(new Span(30, 14, 30, 19), "self.count"), d.fix());
    }

    @Test
    void undefinedNameIsE0301() {
        Diagnostic d = check(wrapBody("let x: Int = nope;")).single("E0301");
        assertEquals(new Span(7, 14, 7, 18), d.span());
    }

    @Test
    void shadowingIsE0303() {
        Diagnostic d = check(wrapBody("let x: Int = 1;\nif (true) {\nlet x: Int = 2;\n}")).single("E0303");
        assertEquals(new Span(9, 5, 9, 6), d.span());
        check(withMembers("let p: Int = 1;")).single("E0303");
        check(wrapBody("let xs: List<Int> = [];\nfor (xs: Int in xs) {\n}")).single("E0303");
    }

    @Test
    void siblingBlocksMayReuseNames() {
        check(wrapBody("if (true) {\nlet x: Int = 1;\n}\nif (true) {\nlet x: Int = 2;\n}")).assertClean();
    }

    @Test
    void variableIsNotVisibleInItsOwnInitializer() {
        check(wrapBody("let x: Int = x;")).single("E0301");
    }

    // ---- types

    @Test
    void intWhereStringExpectedIsE0401WithToStringFix() {
        Diagnostic d = check(wrapBody("let n: Int = 1;\nlet s: String = n;")).single("E0401");
        assertEquals(new Span(8, 17, 8, 18), d.span());
        assertEquals("String", d.expected());
        assertEquals("Int", d.found());
        assertEquals(new Fix(new Span(8, 17, 8, 18), "n.toString()"), d.fix());
    }

    @Test
    void toStringFixParenthesizesOperators() {
        Diagnostic d = check(wrapBody("let s: String = 1 + 2;")).single("E0401");
        assertEquals("(1 + 2).toString()", d.fix().replacement());
    }

    @Test
    void plainMismatchHasNoFix() {
        Diagnostic d = check(wrapBody("let n: Int = \"a\";")).single("E0401");
        assertEquals(null, d.fix());
    }

    @Test
    void classIsAssignableToItsInterfaceOnly() {
        check(wrapBody("let u: Ui = Text { text: \"a\" };")).assertClean();
        check(wrapBody("let t: Text = Text { text: \"a\" };\nlet s: Screen = t;")).single("E0401");
        check(wrapBody("let ts: List<Text> = [];\nlet us: List<Ui> = ts;")).single("E0401");
    }

    @Test
    void stringPlusIsE0402WithConcatFix() {
        Diagnostic d = check(wrapBody("let s: String = \"a\" + \"b\";")).single("E0402");
        assertEquals(new Fix(new Span(7, 17, 7, 26), "\"a\".concat(other: \"b\")"), d.fix());
    }

    @Test
    void operandTypesAreChecked() {
        check(wrapBody("let b: Bool = 1 == \"a\";")).single("E0402");
        check(withMembers("let b: Bool = self == self;")).single("E0402");
        check(wrapBody("let n: Int = -true;")).single("E0402");
        check(wrapBody("let b: Bool = !1;")).single("E0402");
        check(wrapBody("let b: Bool = 1 && true;")).single("E0402");
        check(wrapBody("let n: Int = 1 + true;")).single("E0402");
        check(wrapBody("let b: Bool = \"a\" < \"b\";")).single("E0402");
    }

    @Test
    void validOperatorsAreAccepted() {
        check(wrapBody("let a: Int = (1 + 2 + 3) * (4 - 5);\nlet b: Bool = (a < 1) || (a >= 2);\n"
                + "let c: Bool = \"a\" == \"b\";\nlet d: Bool = true != false;\nlet e: Int = -(a % 2);\n"
                + "let f: Bool = !b;")).assertClean();
    }

    @Test
    void conditionsMustBeBool() {
        check(wrapBody("if (1) {\n}")).single("E0403");
        check(wrapBody("while (\"x\") {\n}")).single("E0403");
    }

    // ---- floats

    @Test
    void floatArithmeticAndComparisonAreTyped() {
        check(wrapBody("let a: Float = 1.5;\nlet b: Float = (a + 2.0) * (a - 0.5);\nlet c: Float = -a;\n"
                + "let d: Bool = (a < b) || (a == 1.5);\nlet e: Float = a / b;\nlet f: Float = a % b;\n"
                + "let xs: List<Float> = [1.0, a];")).assertClean();
    }

    @Test
    void intAndFloatDoNotMix() {
        check(wrapBody("let a: Float = 1.5 + 1;")).single("E0402");
        check(wrapBody("let a: Bool = 1.5 < 1;")).single("E0402");
        check(wrapBody("let a: Bool = 1 == 1.0;")).single("E0402");
        check(wrapBody("let a: Int = 1.5;")).single("E0401");
    }

    @Test
    void intWhereFloatExpectedSuggestsToFloat() {
        Diagnostic d = check(wrapBody("let n: Int = 2;\nlet a: Float = n;")).single("E0401");
        assertEquals("n.toFloat()", d.fix().replacement());
        Diagnostic text = check(wrapBody("let a: Float = 1.5;\nlet s: String = a;")).single("E0401");
        assertEquals("a.toString()", text.fix().replacement());
    }

    @Test
    void floatBuiltinMethodsAreTyped() {
        check(wrapBody("let a: Float = 2.toFloat();\nlet n: Int = a.toInt();\nlet s: String = a.toString();\n"
                + "let r: Float = a.round(decimals: 2);\nlet p: Float = \"1.5\".toFloat();")).assertClean();
        check(wrapBody("let a: Float = 1.5;\nlet r: Float = a.round(decimals: 1.0);")).single("E0401");
    }

    // ---- return

    @Test
    void everyPathMustReturn() {
        check(wrap("class A {\n  fn m(self, c: Bool) -> Int {\n    if (c) {\n      return 1;\n    }\n  }\n}\n"))
                .single("E0404");
        check(wrap("class A {\n  fn m(self, c: Bool) -> Int {\n    while (c) {\n      return 1;\n    }\n  }\n}\n"))
                .single("E0404");
        check(wrap("class A {\n  fn m(self, c: Bool) -> Int {\n    if (c) {\n      return 1;\n    } else {\n"
                + "      return 2;\n    }\n  }\n}\n")).assertClean();
    }

    @Test
    void returnValueMustMatchReturnType() {
        check(wrapBody("return 1;")).single("E0405");
        check(wrap("class A {\n  fn m(self) -> Int {\n    return;\n  }\n}\n")).single("E0405");
        check(wrapBody("if (true) {\nreturn;\n}")).assertClean();
    }

    @Test
    void statementAfterReturnIsE0409() {
        Diagnostic d = check(wrapBody("return;\nlet x: Int = 1;")).single("E0409");
        assertEquals(new Span(8, 1, 8, 16), d.span());
    }

    // ---- lists

    @Test
    void listLiteralNeedsAnExpectedType() {
        check(wrapBody("let n: Int = [1].size();")).single("E0406");
    }

    @Test
    void listElementsMustBeAssignable() {
        Diagnostic d = check(wrapBody("let xs: List<Ui> = [Text { text: \"a\" }, 1];")).single("E0401");
        assertEquals("Ui", d.expected());
        assertEquals("Int", d.found());
    }

    @Test
    void builtinMethodsAreTyped() {
        check(wrapBody("let s: String = \"a\".concat(other: \"b\");\nlet n: Int = s.length();\n"
                + "let t: String = n.toString();\nlet xs: List<Int> = [1];\nlet m: Int = xs.size();\n"
                + "let g: Int = xs.get(index: 0);\nlet ys: List<Int> = xs.append(item: 2);\n"
                + "let e: List<Int> = [];")).assertClean();
        check(wrapBody("let xs: List<Int> = [1];\nlet ys: List<Int> = xs.append(item: \"a\");")).single("E0401");
    }

    // ---- members

    @Test
    void unknownMemberIsE0501() {
        check(withMembers("let n: Int = self.nope;")).single("E0501");
        check(withMembers("let n: Int = self.nope();")).single("E0501");
        check(wrapBody("let n: Int = 1;\nlet s: String = n.nope();")).single("E0501");
    }

    @Test
    void callingAFieldIsE0508() {
        check(withMembers("let n: Int = self.count();")).single("E0508");
    }

    @Test
    void argumentsMustMatchParametersInOrder() {
        check(withMembers("let n: Int = self.add(a: 1);")).single("E0502");
        check(withMembers("let n: Int = self.add(a: 1, c: 2);")).single("E0502");
        Diagnostic d = check(withMembers("let n: Int = self.add(b: 2, a: 1);")).single("E0502");
        assertEquals("a, b", d.expected());
        assertEquals("b, a", d.found());
        assertEquals(new Fix(new Span(30, 22, 30, 34), "(a: 1, b: 2)"), d.fix());
        check(withMembers("let n: Int = self.add(a: 1, b: \"x\");")).single("E0401");
    }

    @Test
    void constructionMustSupplyUninitializedFieldsInOrder() {
        check(withMembers("let q: P = P { x: 1, y: 2 };")).assertClean();
        check(withMembers("let q: P = P { x: 1 };")).single("E0503");
        check(withMembers("let q: P = P { x: 1, y: 2, z: 3 };")).single("E0503");
        Diagnostic d = check(withMembers("let q: P = P { y: 2, x: 1 };")).single("E0503");
        assertEquals("x, y", d.expected());
        assertEquals(new Fix(new Span(30, 14, 30, 28), "{ x: 1, y: 2 }"), d.fix());
        check(withMembers("let q: P = Int {};")).single("E0302");
    }

    @Test
    void immutableThingsCannotBeAssigned() {
        check(withMembers("self.fixed = 2;")).single("E0504");
        check(withMembers("p = 2;")).single("E0504");
        check(withMembers("let a: Int = 1;\na = 2;")).single("E0504");
        check(withMembers("let xs: List<Int> = [];\nfor (i: Int in xs) {\ni = 2;\n}")).single("E0504");
        check(withMembers("var a: Int = 1;\na = 2;\nself.count = a;")).assertClean();
    }

    @Test
    void anotherObjectsFieldCannotBeAssigned() {
        check(withMembers("other.z = 1;")).single("E0505");
    }

    @Test
    void methodReferencesNeedActionOrHandlerShape() {
        check(withMembers("let a: Action = self.bump;\nlet h: Handler<Int> = self.setCount;\n"
                + "let g: Handler<String> = self.setName;")).assertClean();
        check(withMembers("let a: Action = self.add;")).single("E0507");
        check(withMembers("let a: Action = self.get;")).single("E0507");
        Diagnostic d = check(withMembers("let h: Handler<String> = self.setCount;")).single("E0401");
        assertEquals("Handler<String>", d.expected());
        assertEquals("Handler<Int>", d.found());
    }

    @Test
    void fieldInitializerCannotUseSelf() {
        check(wrap("class A {\n  let x: Int = 1;\n  let y: Int = self.x;\n}\n")).single("E0509");
    }

    @Test
    void expressionStatementMustBeUnitCall() {
        check(withMembers("self.get();")).single("E0206");
        check(withMembers("1;")).single("E0206");
        check(withMembers("self.bump();")).assertClean();
    }

    // ---- loops

    @Test
    void forVariableTypeMustMatchElementType() {
        Diagnostic d = check(wrapBody("let ints: List<Int> = [1, 2];\nfor (x: String in ints) {\n}")).single("E0401");
        assertEquals("List<String>", d.expected());
        assertEquals("List<Int>", d.found());
        check(wrapBody("var total: Int = 0;\nfor (x: Int in [1, 2]) {\ntotal = total + x;\n}")).assertClean();
        check(wrapBody("for (x: Int in 5) {\n}")).single("E0401");
    }

    // ---- enums

    private static final String SHAPES = "enum Shape {\n  Dot,\n  Circle { radius: Int },\n  Box { width: Int, height: Int },\n}\n";

    private static String shapeMethod(String body) {
        return wrap(SHAPES + "class A {\n  fn m(self, shape: Shape) -> Int {\n" + body + "\n  }\n}\n");
    }

    @Test
    void exhaustiveMatchIsCleanAndCountsAsReturning() {
        check(shapeMethod("match (shape) {\nShape.Dot => {\nreturn 0;\n}\nShape.Circle { radius } => {\nreturn radius;\n}\n"
                + "Shape.Box { width, height } => {\nreturn width * height;\n}\n}")).assertClean();
    }

    @Test
    void missingVariantIsE0411() {
        Diagnostic d = check(shapeMethod("match (shape) {\nShape.Dot => {\nreturn 0;\n}\n}\nreturn 1;")).single("E0411");
        assertEquals("Circle, Box", d.expected());
        assertEquals(new Span(12, 1, 12, 6), d.span());
    }

    @Test
    void matchArmMistakesAreReported() {
        String arms = "Shape.Circle { radius } => {\nreturn 1;\n}\nShape.Box { width, height } => {\nreturn 2;\n}\n}\nreturn 3;";
        check(shapeMethod("match (shape) {\nShape.Dot => {\nreturn 0;\n}\nShape.Dot => {\nreturn 0;\n}\n" + arms)).single("E0303");
        check(shapeMethod("match (shape) {\nShape.Nope => {\nreturn 0;\n}\nShape.Dot => {\nreturn 0;\n}\n" + arms)).single("E0501");
        check(shapeMethod("match (shape) {\nShape.Dot { radius } => {\nreturn 0;\n}\n" + arms)).single("E0503");
        assertEquals(java.util.List.of("E0503", "E0503"), check(shapeMethod("match (shape) {\nShape.Dot => {\nreturn 0;\n}\n"
                + "Shape.Circle => {\nreturn 1;\n}\nShape.Box { height, width } => {\nreturn 2;\n}\n}\nreturn 3;")).codes());
    }

    @Test
    void matchBindingsAreChecked() {
        String rest = "Shape.Dot => {\nreturn 0;\n}\nShape.Box { width, height } => {\nreturn 2;\n}\n}\nreturn 3;";
        // These two matches are complete, so nothing may follow them.
        String exhaustive = rest.replace("\nreturn 3;", "");
        check(shapeMethod("match (shape) {\nShape.Circle { size } => {\nreturn 1;\n}\n" + rest)).single("E0503");
        check(shapeMethod("match (shape) {\nShape.Circle { shape } => {\nreturn 1;\n}\n" + rest)).single("E0503");
        check(shapeMethod("let radius: Int = 1;\nmatch (shape) {\nShape.Circle { radius } => {\nreturn 1;\n}\n" + exhaustive)).single("E0303");
        check(shapeMethod("match (shape) {\nShape.Circle { radius } => {\nradius = 2;\nreturn 1;\n}\n" + exhaustive)).single("E0504");
    }

    @Test
    void matchNeedsAnEnumOfTheSameType() {
        check(shapeMethod("match (1) {\nShape.Dot => {\nreturn 0;\n}\n}\nreturn 1;")).single("E0401");
        check(wrap(SHAPES + "enum Other {\n  One,\n}\nclass A {\n  fn m(self, shape: Shape) -> Int {\nmatch (shape) {\n"
                + "Other.One => {\nreturn 0;\n}\n}\nreturn 1;\n  }\n}\n")).single("E0401");
    }

    @Test
    void enumConstructionIsChecked() {
        check(shapeMethod("let a: Shape = Shape.Dot;\nlet b: Shape = Shape.Circle { radius: 2 };\nlet c: List<Shape> = [a, b];\nreturn 0;")).assertClean();
        check(shapeMethod("let a: Shape = Shape.Nope;\nreturn 0;")).single("E0501");
        check(shapeMethod("let a: Shape = Shape.Circle;\nreturn 0;")).single("E0503");
        check(shapeMethod("let a: Shape = Shape.Circle { radius: \"x\" };\nreturn 0;")).single("E0401");
        check(shapeMethod("let a: Shape = Shape.Dot { radius: 1 };\nreturn 0;")).single("E0503");
        check(shapeMethod("let a: Int = Shape.Dot;\nreturn 0;")).single("E0401");
        check(shapeMethod("let a: Shape = A.Dot;\nreturn 0;")).single("E0302");
    }

    @Test
    void enumDeclarationsAreChecked() {
        check(wrap("enum E {\n  A,\n  A,\n}\n")).single("E0303");
        check(wrap("enum E {\n  A { x: Int, x: Int },\n}\n")).single("E0303");
        check(wrap("enum E {\n  A { x: Nope },\n}\n")).single("E0302");
        check(wrap("enum E {\n  A,\n}\nclass E {\n}\n")).single("E0303");
        check(wrap("enum Tree {\n  Leaf,\n  Node { left: Tree, right: Tree },\n}\n")).assertClean();
    }

    // ---- function literals

    @Test
    void functionLiteralsHaveTheTypeOfTheirShape() {
        check(withMembers("let a: Action = fn() -> Unit {\nself.bump();\n};\n"
                + "let h: Handler<Int> = fn(value: Int) -> Unit {\nself.setCount(value: value);\n};\n"
                + "let f: Fn<Int, Bool> = fn(value: Int) -> Bool {\nreturn value > p;\n};\n"
                + "let g: Fn<String, Int> = fn(text: String) -> Int {\nreturn text.length() + self.count;\n};")).assertClean();
        Diagnostic d = check(withMembers("let f: Fn<Int, Bool> = fn(value: Int) -> Int {\nreturn value;\n};")).single("E0401");
        assertEquals("Fn<Int, Bool>", d.expected());
        assertEquals("Fn<Int, Int>", d.found());
        check(withMembers("let f: Action = fn(a: Int, b: Int) -> Unit {\n};")).single("E0507");
        check(withMembers("let f: Action = fn() -> Int {\nreturn 1;\n};")).single("E0507");
    }

    @Test
    void functionLiteralBodiesAreChecked() {
        check(withMembers("let f: Fn<Int, Bool> = fn(value: Int) -> Bool {\nif (value > 0) {\nreturn true;\n}\n};")).single("E0404");
        check(withMembers("let f: Fn<Int, Bool> = fn(value: Int) -> Bool {\nreturn value;\n};")).single("E0401");
        check(withMembers("let f: Fn<Int, Bool> = fn(p: Int) -> Bool {\nreturn true;\n};")).single("E0303");
        check(withMembers("let f: Action = fn() -> Unit {\nreturn 1;\n};")).single("E0405");
        check(withMembers("let f: Fn<Int, Unit> = fn(value: Int) -> Unit {\n};")).single("E0407");
    }

    @Test
    void functionLiteralsMayOnlyCaptureFixedLocals() {
        check(withMembers("let fixed: Int = 1;\nlet f: Fn<Int, Int> = fn(value: Int) -> Int {\nreturn value + fixed + p;\n};")).assertClean();
        Diagnostic d = check(withMembers("var total: Int = 0;\nlet f: Fn<Int, Int> = fn(value: Int) -> Int {\nreturn value + total;\n};"))
                .single("E0412");
        assertEquals(new Span(32, 16, 32, 21), d.span());
        check(withMembers("var total: Int = 0;\nlet a: Action = fn() -> Unit {\nlet inner: Action = fn() -> Unit {\nself.setCount(value: total);\n};\n};"))
                .single("E0412");
        check(withMembers("let a: Action = fn() -> Unit {\nvar mine: Int = 0;\nmine = mine + 1;\nself.setCount(value: mine);\n};")).assertClean();
    }

    @Test
    void methodReferencesWithAResultAreFnValues() {
        check(withMembers("let f: Fn<Int, Int> = other.x.toString;")).single("E0507");
        check(wrap("class A {\n  fn big(self, value: Int) -> Bool {\n    return value > 10;\n  }\n  fn m(self) -> Fn<Int, Bool> {\n    return self.big;\n  }\n}\n")).assertClean();
    }

    @Test
    void functionValuesCanBeCalled() {
        check(withMembers("let f: Fn<Int, Bool> = fn(value: Int) -> Bool {\nreturn value > 0;\n};\nlet b: Bool = f.call(arg: 1);\n"
                + "let a: Action = self.bump;\na.run();\nlet h: Handler<Int> = self.setCount;\nh.run(arg: 2);")).assertClean();
        check(withMembers("let f: Fn<Int, Bool> = fn(value: Int) -> Bool {\nreturn value > 0;\n};\nlet b: Bool = f.call(arg: \"x\");")).single("E0401");
    }

    @Test
    void listMethodsTakeFunctions() {
        check(wrapBody("let xs: List<Int> = [1, 2, 3];\nlet big: List<Int> = xs.filter(keep: fn(x: Int) -> Bool {\nreturn x > 1;\n});\n"
                + "let texts: List<String> = xs.map(transform: fn(x: Int) -> String {\nreturn x.toString();\n});\n"
                + "let any: Bool = xs.any(test: fn(x: Int) -> Bool {\nreturn x > 2;\n});\n"
                + "let all: Bool = xs.all(test: fn(x: Int) -> Bool {\nreturn x > 0;\n});\n"
                + "let n: Int = xs.count(test: fn(x: Int) -> Bool {\nreturn x > 1;\n});\n"
                + "let at: Int = xs.indexWhere(test: fn(x: Int) -> Bool {\nreturn x > 1;\n});\n"
                + "let sorted: List<Int> = xs.sortedBy(key: fn(x: Int) -> Int {\nreturn -x;\n});")).assertClean();
        check(wrapBody("let xs: List<Int> = [1];\nlet ys: List<Int> = xs.filter(keep: fn(x: String) -> Bool {\nreturn true;\n});")).single("E0401");
        check(wrapBody("let xs: List<Int> = [1];\nlet ys: List<Int> = xs.map(transform: fn(x: Int) -> String {\nreturn \"a\";\n});")).single("E0401");
        check(wrapBody("let xs: List<Int> = [1];\nlet ys: List<String> = xs.map(transform: 1);")).single("E0401");
    }

    // ---- assets

    @Test
    void imageNameMustBeAnExistingAsset() {
        String source = wrap("class A {\n  fn m(self) -> Ui {\n    return Image { name: \"logo.png\" };\n  }\n}\n")
                .replace("import kata.ui.Ui;", "import kata.ui.Ui;\nimport kata.ui.Image;");
        checkFiles(Map.of(CheckSupport.PATH, source), java.util.Set.of("logo.png", "icons/star.png")).assertClean();
        Diagnostic d = checkFiles(Map.of(CheckSupport.PATH, source), java.util.Set.of("icons/star.png"))
                .single("E0605");
        assertEquals("icons/star.png", d.expected());
        assertEquals("logo.png", d.found());
        assertEquals(new Span(8, 26, 8, 36), d.span());
    }

    @Test
    void computedImageNamesAreNotCheckedAtCompileTime() {
        String source = wrap("class A {\n  fn m(self, file: String) -> Ui {\n    return Image { name: file };\n  }\n}\n")
                .replace("import kata.ui.Ui;", "import kata.ui.Ui;\nimport kata.ui.Image;");
        check(source).assertClean();
    }

    // ---- app

    @Test
    void appRootMustBeAScreen() {
        check(withMembers("").replace("root: Main {}", "root: P { x: 1, y: 2 }")).single("E0604");
    }
}
