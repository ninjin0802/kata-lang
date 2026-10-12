package dev.kata.compiler.codegen;

import static dev.kata.compiler.codegen.ExecSupport.call;
import static dev.kata.compiler.codegen.ExecSupport.create;
import static dev.kata.compiler.codegen.ExecSupport.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExecBasicsTest {
    static final String SOURCE = """
            class Calc {
              fn add(self, a: Int, b: Int) -> Int {
                return a + b;
              }
              fn div(self, a: Int, b: Int) -> Int {
                return a / b;
              }
              fn rem(self, a: Int, b: Int) -> Int {
                return a % b;
              }
              fn neg(self, a: Int) -> Int {
                return -a;
              }
              fn mix(self, a: Int, b: Int, c: Int) -> Int {
                return (a + b + c) * (a - b);
              }
              fn less(self, a: Int, b: Int) -> Bool {
                return a < b;
              }
              fn atMost(self, a: Int, b: Int) -> Bool {
                return a <= b;
              }
              fn more(self, a: Int, b: Int) -> Bool {
                return a > b;
              }
              fn atLeast(self, a: Int, b: Int) -> Bool {
                return a >= b;
              }
              fn same(self, a: Int, b: Int) -> Bool {
                return a == b;
              }
              fn differ(self, a: Int, b: Int) -> Bool {
                return a != b;
              }
              fn sameBool(self, a: Bool, b: Bool) -> Bool {
                return a == b;
              }
              fn all(self, a: Bool, b: Bool, c: Bool) -> Bool {
                return a && b && c;
              }
              fn any(self, a: Bool, b: Bool, c: Bool) -> Bool {
                return a || b || c;
              }
              fn not(self, a: Bool) -> Bool {
                return !a;
              }
              fn shortAnd(self, a: Bool, zero: Int) -> Bool {
                return a && ((1 / zero) == 1);
              }
              fn shortOr(self, a: Bool, zero: Int) -> Bool {
                return a || ((1 / zero) == 1);
              }
              fn sign(self, n: Int) -> String {
                if (n < 0) {
                  return "neg";
                } else if (n == 0) {
                  return "zero";
                } else {
                  return "pos";
                }
              }
              fn fact(self, n: Int) -> Int {
                if (n <= 1) {
                  return 1;
                }
                return n * self.fact(n: n - 1);
              }
              fn greet(self, name: String) -> String {
                return "Hello, ".concat(other: name);
              }
              fn len(self, s: String) -> Int {
                return s.length();
              }
              fn sameText(self, a: String, b: String) -> Bool {
                return a == b;
              }
              fn differentText(self, a: String, b: String) -> Bool {
                return a != b;
              }
              fn show(self, n: Int) -> String {
                return n.toString();
              }
              fn big(self) -> Int {
                return 9223372036854775807;
              }
              fn locals(self) -> Int {
                var a: Int = 1;
                let b: Int = 2;
                a = a + b;
                if (a > 2) {
                  let c: Int = 10;
                  a = a + c;
                }
                if (a > 2) {
                  let c: Bool = true;
                  if (c) {
                    a = a + 100;
                  }
                }
                return a;
              }
            }
            class Point {
              let x: Int;
              let y: Int;
              var hits: Int = 0;
              let label: String = "p";
              fn sum(self) -> Int {
                return self.x + self.y;
              }
              fn hit(self) -> Unit {
                self.hits = self.hits + 1;
              }
              fn early(self, stop: Bool) -> Unit {
                if (stop) {
                  return;
                }
                self.hits = 100;
              }
              fn plus(self, other: Point) -> Int {
                return other.x + other.sum();
              }
            }
            class Sleeper {
              var log: String = "";
              fn wait(self) -> Unit {
                self.log = self.log.concat(other: "w");
              }
              fn notify(self) -> Unit {
                self.log = self.log.concat(other: "n");
              }
              fn notifyAll(self) -> Unit {
                self.log = self.log.concat(other: "a");
              }
              fn finalize(self) -> Unit {
                self.log = self.log.concat(other: "f");
              }
              fn all(self) -> Action {
                self.wait();
                self.notify();
                self.notifyAll();
                self.finalize();
                return self.wait;
              }
            }
            class Maker {
              var log: String = "";
              fn note(self, s: String) -> Int {
                self.log = self.log.concat(other: s);
                return 1;
              }
              fn make(self) -> Point {
                return Point { x: self.note(s: "x"), y: self.note(s: "y") };
              }
            }
            """;

    static ClassLoader loader;
    static Object calc;

    @BeforeAll
    static void compile() throws Exception {
        loader = ExecSupport.compile(SOURCE);
        calc = create(loader, "app.main.Calc");
    }

    @Test
    void arithmetic() throws Throwable {
        assertEquals(5L, call(calc, "add", 2L, 3L));
        assertEquals(3L, call(calc, "div", 7L, 2L));
        assertEquals(-3L, call(calc, "div", -7L, 2L));
        assertEquals(-1L, call(calc, "rem", -7L, 2L));
        assertEquals(-4L, call(calc, "neg", 4L));
        assertEquals(-6L, call(calc, "mix", 1L, 2L, 3L));
    }

    @Test
    void additionWrapsOnOverflow() throws Throwable {
        assertEquals(Long.MAX_VALUE, call(calc, "big"));
        assertEquals(Long.MIN_VALUE, call(calc, "add", Long.MAX_VALUE, 1L));
    }

    @Test
    void comparisons() throws Throwable {
        assertEquals(true, call(calc, "less", 1L, 2L));
        assertEquals(false, call(calc, "less", 2L, 2L));
        assertEquals(true, call(calc, "atMost", 2L, 2L));
        assertEquals(true, call(calc, "more", 3L, 2L));
        assertEquals(false, call(calc, "atLeast", 1L, 2L));
        assertEquals(true, call(calc, "same", 1L << 40, 1L << 40));
        assertEquals(true, call(calc, "differ", 1L, 2L));
        assertEquals(true, call(calc, "sameBool", false, false));
        assertEquals(false, call(calc, "sameBool", true, false));
    }

    @Test
    void logic() throws Throwable {
        assertEquals(true, call(calc, "all", true, true, true));
        assertEquals(false, call(calc, "all", true, false, true));
        assertEquals(false, call(calc, "any", false, false, false));
        assertEquals(true, call(calc, "any", false, false, true));
        assertEquals(false, call(calc, "not", true));
    }

    @Test
    void logicShortCircuits() throws Throwable {
        assertEquals(false, call(calc, "shortAnd", false, 0L));
        assertEquals(true, call(calc, "shortOr", true, 0L));
        assertThrows(ArithmeticException.class, () -> call(calc, "shortAnd", true, 0L));
    }

    @Test
    void branchesAndRecursion() throws Throwable {
        assertEquals("neg", call(calc, "sign", -5L));
        assertEquals("zero", call(calc, "sign", 0L));
        assertEquals("pos", call(calc, "sign", 5L));
        assertEquals(120L, call(calc, "fact", 5L));
        assertEquals(113L, call(calc, "locals"));
    }

    @Test
    void strings() throws Throwable {
        assertEquals("Hello, kata", call(calc, "greet", "kata"));
        assertEquals(4L, call(calc, "len", "日本語😀"));
        assertEquals(true, call(calc, "sameText", new String("abc"), new String("abc")));
        assertEquals(true, call(calc, "differentText", "abc", "abd"));
        assertEquals("-42", call(calc, "show", -42L));
    }

    @Test
    void fieldsAndConstruction() throws Throwable {
        Object point = create(loader, "app.main.Point", 3L, 4L);
        assertEquals(3L, field(point, "x"));
        assertEquals(0L, field(point, "hits"));
        assertEquals("p", field(point, "label"));
        assertEquals(7L, call(point, "sum"));
        call(point, "hit");
        call(point, "hit");
        assertEquals(2L, field(point, "hits"));
        call(point, "early", true);
        assertEquals(2L, field(point, "hits"));
        call(point, "early", false);
        assertEquals(100L, field(point, "hits"));
        assertEquals(10L, call(point, "plus", point));
    }

    @Test
    void constructionEvaluatesFieldsInWrittenOrder() throws Throwable {
        Object maker = create(loader, "app.main.Maker");
        Object point = call(maker, "make");
        assertEquals("xy", field(maker, "log"));
        assertEquals(2L, call(point, "sum"));
    }

    @Test
    void divisionByZeroPanicsWithKataLocation() {
        ArithmeticException e = assertThrows(ArithmeticException.class, () -> call(calc, "div", 1L, 0L));
        StackTraceElement top = e.getStackTrace()[0];
        assertEquals("main.kata", top.getFileName());
        assertEquals(ExecSupport.lineOf(SOURCE, "return a / b;"), top.getLineNumber());
        assertEquals("app.main.Calc", top.getClassName());
    }

    @Test
    void methodsNamedLikeFinalObjectMethodsWork() throws Throwable {
        Object sleeper = create(loader, "app.main.Sleeper");
        kata.rt.Action wait = (kata.rt.Action) call(sleeper, "all");
        wait.run();
        assertEquals("wnafw", field(sleeper, "log"));
    }

    @Test
    void classesAndLetFieldsAreFinal() throws Exception {
        Class<?> point = loader.loadClass("app.main.Point");
        assertTrue(Modifier.isFinal(point.getModifiers()));
        assertTrue(Modifier.isFinal(point.getField("x").getModifiers()));
        assertFalse(Modifier.isFinal(point.getField("hits").getModifiers()));
        assertTrue(Modifier.isFinal(point.getMethod("sum").getModifiers()));
    }
}
