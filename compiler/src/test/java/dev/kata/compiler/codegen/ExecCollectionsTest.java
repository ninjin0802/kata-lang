package dev.kata.compiler.codegen;

import static dev.kata.compiler.codegen.ExecSupport.call;
import static dev.kata.compiler.codegen.ExecSupport.create;
import static dev.kata.compiler.codegen.ExecSupport.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import kata.rt.Action;
import kata.rt.Handler;
import kata.rt.KList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExecCollectionsTest {
    static final String SOURCE = """
            class Loops {
              var calls: Int = 0;
              fn sumTo(self, n: Int) -> Int {
                var i: Int = 1;
                var total: Int = 0;
                while (i <= n) {
                  total = total + i;
                  i = i + 1;
                }
                return total;
              }
              fn sum(self, xs: List<Int>) -> Int {
                var total: Int = 0;
                for (x: Int in xs) {
                  total = total + x;
                }
                return total;
              }
              fn source(self) -> List<Int> {
                self.calls = self.calls + 1;
                return [1, 2, 3];
              }
              fn sumSource(self) -> Int {
                var total: Int = 0;
                for (x: Int in self.source()) {
                  total = total + x;
                }
                return total;
              }
              fn firstNegative(self, xs: List<Int>) -> Int {
                for (x: Int in xs) {
                  if (x < 0) {
                    return x;
                  }
                }
                return 0;
              }
              fn pairs(self, xs: List<Int>) -> Int {
                var count: Int = 0;
                for (a: Int in xs) {
                  for (b: Int in xs) {
                    count = count + 1;
                  }
                }
                return count;
              }
              fn ints(self) -> List<Int> {
                return [1, 2];
              }
              fn bools(self) -> List<Bool> {
                return [true, false];
              }
              fn strings(self) -> List<String> {
                return ["a", "b"];
              }
              fn empty(self) -> List<Int> {
                return [];
              }
              fn nested(self) -> List<List<Int>> {
                return [[1], [2, 3]];
              }
              fn second(self, xs: List<Int>) -> Int {
                return xs.get(index: 1);
              }
              fn secondBool(self, xs: List<Bool>) -> Bool {
                return xs.get(index: 1);
              }
              fn secondString(self, xs: List<String>) -> String {
                return xs.get(index: 1);
              }
              fn size(self, xs: List<Int>) -> Int {
                return xs.size();
              }
              fn grow(self, xs: List<Int>) -> List<Int> {
                return xs.append(item: 9);
              }
              fn growBool(self, xs: List<Bool>) -> List<Bool> {
                return xs.append(item: true);
              }
              fn joinAll(self, xs: List<String>) -> String {
                var out: String = "";
                for (s: String in xs) {
                  out = out.concat(other: s);
                }
                return out;
              }
              fn countTrue(self, xs: List<Bool>) -> Int {
                var n: Int = 0;
                for (b: Bool in xs) {
                  if (b) {
                    n = n + 1;
                  }
                }
                return n;
              }
            }
            class Refs {
              var count: Int = 0;
              var name: String = "";
              var flag: Bool = false;
              fn bump(self) -> Unit {
                self.count = self.count + 1;
              }
              fn setCount(self, value: Int) -> Unit {
                self.count = value;
              }
              fn setName(self, value: String) -> Unit {
                self.name = value;
              }
              fn setFlag(self, value: Bool) -> Unit {
                self.flag = value;
              }
              fn action(self) -> Action {
                return self.bump;
              }
              fn countHandler(self) -> Handler<Int> {
                return self.setCount;
              }
              fn nameHandler(self) -> Handler<String> {
                return self.setName;
              }
              fn flagHandler(self) -> Handler<Bool> {
                return self.setFlag;
              }
              fn actionOf(self, other: Refs) -> Action {
                return other.bump;
              }
            }
            """;

    static ClassLoader loader;
    static Object loops;

    @BeforeAll
    static void compile() throws Exception {
        loader = ExecSupport.compile(SOURCE);
        loops = create(loader, "app.main.Loops");
    }

    private static KList list(Object... items) {
        return KList.of(items);
    }

    @Test
    void whileLoop() throws Throwable {
        assertEquals(55L, call(loops, "sumTo", 10L));
        assertEquals(0L, call(loops, "sumTo", 0L));
    }

    @Test
    void forLoop() throws Throwable {
        assertEquals(6L, call(loops, "sum", list(1L, 2L, 3L)));
        assertEquals(0L, call(loops, "sum", list()));
        assertEquals(-2L, call(loops, "firstNegative", list(1L, -2L, -3L)));
        assertEquals(0L, call(loops, "firstNegative", list(1L)));
        assertEquals(9L, call(loops, "pairs", list(1L, 2L, 3L)));
        assertEquals("abc", call(loops, "joinAll", list("a", "b", "c")));
        assertEquals(2L, call(loops, "countTrue", list(true, false, true)));
    }

    @Test
    void forEvaluatesItsListOnce() throws Throwable {
        Object fresh = create(loader, "app.main.Loops");
        assertEquals(6L, call(fresh, "sumSource"));
        assertEquals(1L, field(fresh, "calls"));
    }

    @Test
    void listLiteralsBoxTheirElements() throws Throwable {
        KList ints = (KList) call(loops, "ints");
        assertEquals(2L, ints.size());
        assertEquals(Long.valueOf(2), ints.get(1));
        KList bools = (KList) call(loops, "bools");
        assertEquals(Boolean.TRUE, bools.get(0));
        assertEquals("b", ((KList) call(loops, "strings")).get(1));
        assertEquals(0L, ((KList) call(loops, "empty")).size());
        KList nested = (KList) call(loops, "nested");
        assertEquals(3L, ((KList) nested.get(1)).get(1));
    }

    @Test
    void listMethods() throws Throwable {
        KList ints = list(5L, 6L);
        assertEquals(6L, call(loops, "second", ints));
        assertEquals(false, call(loops, "secondBool", list(true, false)));
        assertEquals("y", call(loops, "secondString", list("x", "y")));
        assertEquals(2L, call(loops, "size", ints));
        KList grown = (KList) call(loops, "grow", ints);
        assertEquals(3L, grown.size());
        assertEquals(9L, grown.get(2));
        assertEquals(2L, ints.size());
        assertEquals(Boolean.TRUE, ((KList) call(loops, "growBool", list(false))).get(1));
    }

    @Test
    void getOutOfRangePanics() {
        assertThrows(IndexOutOfBoundsException.class, () -> call(loops, "second", list(1L)));
    }

    @Test
    void methodReferencesCallTheBoundInstance() throws Throwable {
        Object refs = create(loader, "app.main.Refs");
        Object other = create(loader, "app.main.Refs");
        Action action = (Action) call(refs, "action");
        action.run();
        action.run();
        assertEquals(2L, field(refs, "count"));
        ((Action) call(refs, "actionOf", other)).run();
        assertEquals(1L, field(other, "count"));
        assertEquals(2L, field(refs, "count"));
    }

    @Test
    void handlersUnboxTheirArgument() throws Throwable {
        Object refs = create(loader, "app.main.Refs");
        ((Handler) call(refs, "countHandler")).run(42L);
        ((Handler) call(refs, "nameHandler")).run("kata");
        ((Handler) call(refs, "flagHandler")).run(true);
        assertEquals(42L, field(refs, "count"));
        assertEquals("kata", field(refs, "name"));
        assertEquals(true, field(refs, "flag"));
    }
}
