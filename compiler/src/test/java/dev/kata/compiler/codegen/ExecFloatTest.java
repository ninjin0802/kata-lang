package dev.kata.compiler.codegen;

import static dev.kata.compiler.codegen.ExecSupport.call;
import static dev.kata.compiler.codegen.ExecSupport.create;
import static dev.kata.compiler.codegen.ExecSupport.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import kata.rt.Handler;
import kata.rt.KList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExecFloatTest {
    static final String SOURCE = """
            class F {
              var value: Float = 0.5;
              let scale: Float;
              fn add(self, a: Float, b: Float) -> Float {
                return a + b;
              }
              fn mix(self, a: Float, b: Float) -> Float {
                return ((a - b) * 2.0) / 4.0;
              }
              fn rem(self, a: Float, b: Float) -> Float {
                return a % b;
              }
              fn neg(self, a: Float) -> Float {
                return -a;
              }
              fn less(self, a: Float, b: Float) -> Bool {
                return a < b;
              }
              fn atMost(self, a: Float, b: Float) -> Bool {
                return a <= b;
              }
              fn more(self, a: Float, b: Float) -> Bool {
                return a > b;
              }
              fn atLeast(self, a: Float, b: Float) -> Bool {
                return a >= b;
              }
              fn same(self, a: Float, b: Float) -> Bool {
                return a == b;
              }
              fn differ(self, a: Float, b: Float) -> Bool {
                return a != b;
              }
              fn fromInt(self, n: Int) -> Float {
                return n.toFloat();
              }
              fn toInt(self, a: Float) -> Int {
                return a.toInt();
              }
              fn show(self, a: Float) -> String {
                return a.toString();
              }
              fn round(self, a: Float, places: Int) -> Float {
                return a.round(decimals: places);
              }
              fn parse(self, text: String) -> Float {
                return text.toFloat();
              }
              fn scaled(self, extra: Int, a: Float, flag: Bool) -> Float {
                let local: Float = a * self.scale;
                var total: Float = local;
                if (flag) {
                  total = total + extra.toFloat();
                }
                return total + self.value;
              }
              fn sum(self, xs: List<Float>) -> Float {
                var total: Float = 0.0;
                for (x: Float in xs) {
                  total = total + x;
                }
                return total;
              }
              fn list(self) -> List<Float> {
                let base: List<Float> = [1.5, self.value];
                return base.append(item: 2.5);
              }
              fn setValue(self, next: Float) -> Unit {
                self.value = next;
              }
              fn setter(self) -> Handler<Float> {
                return self.setValue;
              }
            }
            """;

    static Object f;

    @BeforeAll
    static void compile() throws Exception {
        ClassLoader loader = ExecSupport.compile(SOURCE);
        f = create(loader, "app.main.F", 2.0);
    }

    @Test
    void arithmetic() throws Throwable {
        assertEquals(3.75, call(f, "add", 1.5, 2.25));
        assertEquals(0.30000000000000004, call(f, "add", 0.1, 0.2));
        assertEquals(1.0, call(f, "mix", 3.0, 1.0));
        assertEquals(1.5, call(f, "rem", 7.5, 2.0));
        assertEquals(-2.5, call(f, "neg", 2.5));
    }

    @Test
    void divisionByZeroFollowsIeee() throws Throwable {
        assertEquals(Double.POSITIVE_INFINITY, call(f, "mix", Double.MAX_VALUE, -Double.MAX_VALUE));
        assertEquals(true, call(f, "differ", Double.NaN, Double.NaN));
    }

    @Test
    void comparisons() throws Throwable {
        assertEquals(true, call(f, "less", 1.0, 1.5));
        assertEquals(false, call(f, "less", 1.5, 1.5));
        assertEquals(true, call(f, "atMost", 1.5, 1.5));
        assertEquals(true, call(f, "more", 2.0, 1.5));
        assertEquals(true, call(f, "atLeast", 1.5, 1.5));
        assertEquals(true, call(f, "same", 0.5, 0.5));
        assertEquals(true, call(f, "differ", 0.5, 0.25));
    }

    @Test
    void comparisonsWithNaNAreFalse() throws Throwable {
        for (String method : new String[] {"less", "atMost", "more", "atLeast", "same"}) {
            assertEquals(false, call(f, method, Double.NaN, 1.0), method);
            assertEquals(false, call(f, method, 1.0, Double.NaN), method);
        }
    }

    @Test
    void conversionsAndBuiltins() throws Throwable {
        assertEquals(7.0, call(f, "fromInt", 7L));
        assertEquals(-3L, call(f, "toInt", -3.99));
        assertEquals("3.0", call(f, "show", 3.0));
        assertEquals(0.3, call(f, "round", 0.30000000000000004, 10L));
        assertEquals(1.5, call(f, "parse", "1.5"));
        assertThrows(IllegalArgumentException.class, () -> call(f, "parse", "abc"));
        assertThrows(IllegalArgumentException.class, () -> call(f, "round", 1.0, -1L));
    }

    @Test
    void floatsShareMethodsWithOtherTypesAndLocals() throws Throwable {
        assertEquals(13.5, call(f, "scaled", 10L, 1.5, true));
        assertEquals(3.5, call(f, "scaled", 10L, 1.5, false));
    }

    @Test
    void listsAndHandlersBoxFloats() throws Throwable {
        assertEquals(4.5, call(f, "sum", KList.of(new Object[] {1.5, 3.0})));
        KList list = (KList) call(f, "list");
        assertEquals(3L, list.size());
        assertEquals(Double.valueOf(2.5), list.get(2));
        ((Handler) call(f, "setter")).run(9.25);
        assertEquals(9.25, field(f, "value"));
        ((Handler) call(f, "setter")).run(0.5);
    }
}
