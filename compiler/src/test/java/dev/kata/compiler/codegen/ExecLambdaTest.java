package dev.kata.compiler.codegen;

import static dev.kata.compiler.codegen.ExecSupport.call;
import static dev.kata.compiler.codegen.ExecSupport.create;
import static dev.kata.compiler.codegen.ExecSupport.field;
import static org.junit.jupiter.api.Assertions.assertEquals;

import kata.rt.Action;
import kata.rt.Fn;
import kata.rt.Handler;
import kata.rt.KList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExecLambdaTest {
    static final String SOURCE = """
            class L {
              var count: Int = 0;
              var log: String = "";
              fn adder(self, amount: Int) -> Action {
                return fn() -> Unit {
                  self.count = self.count + amount;
                };
              }
              fn recorder(self, prefix: String) -> Handler<String> {
                let mark: String = prefix.concat(other: ":");
                return fn(text: String) -> Unit {
                  self.log = self.log.concat(other: mark).concat(other: text);
                };
              }
              fn above(self, limit: Int, scale: Float, on: Bool) -> Fn<Int, Bool> {
                return fn(value: Int) -> Bool {
                  return on && ((value.toFloat() * scale) > limit.toFloat());
                };
              }
              fn nested(self, base: Int) -> Fn<Int, Int> {
                return fn(x: Int) -> Int {
                  let twice: Fn<Int, Int> = fn(y: Int) -> Int {
                    return (y * 2) + base + self.count;
                  };
                  return twice.call(arg: x) + 1;
                };
              }
              fn isBig(self, value: Int) -> Bool {
                return value > 10;
              }
              fn bigTest(self) -> Fn<Int, Bool> {
                return self.isBig;
              }
              fn apply(self, f: Fn<Int, Int>, a: Action, h: Handler<String>, value: Int) -> Int {
                a.run();
                h.run(arg: "applied");
                return f.call(arg: value);
              }
              fn pipeline(self, xs: List<Int>, limit: Int) -> List<String> {
                return xs.filter(keep: fn(x: Int) -> Bool {
                  return x > limit;
                }).sortedBy(key: fn(x: Int) -> Int {
                  return -x;
                }).map(transform: fn(x: Int) -> String {
                  return "#".concat(other: x.toString());
                });
              }
              fn stats(self, xs: List<Int>) -> List<Int> {
                var any: Int = 0;
                if (xs.any(test: self.isBig)) {
                  any = 1;
                }
                var all: Int = 0;
                if (xs.all(test: self.isBig)) {
                  all = 1;
                }
                return [any, all, xs.count(test: self.isBig), xs.indexWhere(test: self.isBig)];
              }
              fn halves(self, xs: List<Int>) -> List<Float> {
                return xs.map(transform: fn(x: Int) -> Float {
                  return x.toFloat() / 2.0;
                });
              }
              fn flags(self, xs: List<Int>) -> List<Bool> {
                return xs.map(transform: self.isBig);
              }
            }
            """;

    static ClassLoader loader;

    @BeforeAll
    static void compile() {
        loader = ExecSupport.compile(SOURCE);
    }

    private static Object fresh() throws Exception {
        return create(loader, "app.main.L");
    }

    @Test
    void literalsCaptureSelfParametersAndFixedLocals() throws Throwable {
        Object l = fresh();
        Action add5 = (Action) call(l, "adder", 5L);
        add5.run();
        add5.run();
        assertEquals(10L, field(l, "count"));
        ((Handler) call(l, "recorder", "a")).run("x");
        ((Handler) call(l, "recorder", "b")).run("y");
        assertEquals("a:xb:y", field(l, "log"));
    }

    @Test
    void capturesOfEveryPrimitiveKind() throws Throwable {
        Object l = fresh();
        Fn above = (Fn) call(l, "above", 10L, 2.0, true);
        assertEquals(Boolean.TRUE, above.call(6L));
        assertEquals(Boolean.FALSE, above.call(5L));
        assertEquals(Boolean.FALSE, ((Fn) call(l, "above", 10L, 2.0, false)).call(6L));
    }

    @Test
    void literalsNestAndCaptureThroughEachOther() throws Throwable {
        Object l = fresh();
        ((Action) call(l, "adder", 3L)).run();
        // twice(1) = 1 * 2 + 1000 + count(3) = 1005; nested returns that + 1.
        assertEquals(1006L, ((Fn) call(l, "nested", 1000L)).call(1L));
    }

    @Test
    void methodReferencesWithAResultWork() throws Throwable {
        Fn big = (Fn) call(fresh(), "bigTest");
        assertEquals(Boolean.TRUE, big.call(11L));
        assertEquals(Boolean.FALSE, big.call(10L));
    }

    @Test
    void functionValuesCanBeCalledFromKata() throws Throwable {
        Object l = fresh();
        Fn plusOne = arg -> (Long) arg + 1;
        Object result = call(l, "apply", plusOne, call(l, "adder", 2L), call(l, "recorder", "p"), 41L);
        assertEquals(42L, result);
        assertEquals(2L, field(l, "count"));
        assertEquals("p:applied", field(l, "log"));
    }

    @Test
    void listMethodsRunFunctions() throws Throwable {
        Object l = fresh();
        KList xs = KList.of(new Object[] {5L, 30L, 12L, 7L, 20L});
        KList out = (KList) call(l, "pipeline", xs, 10L);
        assertEquals(3L, out.size());
        assertEquals("#30", out.get(0));
        assertEquals("#20", out.get(1));
        assertEquals("#12", out.get(2));
        KList stats = (KList) call(l, "stats", xs);
        assertEquals(1L, stats.get(0));
        assertEquals(0L, stats.get(1));
        assertEquals(3L, stats.get(2));
        assertEquals(1L, stats.get(3));
        assertEquals(-1L, ((KList) call(l, "stats", KList.of(new Object[] {1L}))).get(3));
        assertEquals(2.5, ((KList) call(l, "halves", xs)).get(0));
        assertEquals(Boolean.TRUE, ((KList) call(l, "flags", xs)).get(1));
    }
}
