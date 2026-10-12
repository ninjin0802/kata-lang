package dev.kata.compiler.codegen;

import static dev.kata.compiler.codegen.ExecSupport.call;
import static dev.kata.compiler.codegen.ExecSupport.create;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kata.compiler.check.CheckSupport;
import java.util.Map;
import kata.rt.KList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** String and list methods, and the kata.sys classes, run on the desktop JVM. */
class ExecStdTest {
    static final String SOURCE = """
            class S {
              fn contains(self, text: String, part: String) -> Bool {
                return text.contains(part: part);
              }
              fn starts(self, text: String, prefix: String) -> Bool {
                return text.startsWith(prefix: prefix);
              }
              fn ends(self, text: String, suffix: String) -> Bool {
                return text.endsWith(suffix: suffix);
              }
              fn indexOf(self, text: String, part: String) -> Int {
                return text.indexOf(part: part);
              }
              fn sub(self, text: String, start: Int, end: Int) -> String {
                return text.substring(start: start, end: end);
              }
              fn split(self, text: String, separator: String) -> List<String> {
                return text.split(separator: separator);
              }
              fn trim(self, text: String) -> String {
                return text.trim();
              }
              fn upper(self, text: String) -> String {
                return text.toUpper();
              }
              fn lower(self, text: String) -> String {
                return text.toLower();
              }
              fn replace(self, text: String, part: String, with: String) -> String {
                return text.replace(part: part, with: with);
              }
              fn toInt(self, text: String) -> Int {
                return text.toInt();
              }
              fn removeAt(self, xs: List<Int>, index: Int) -> List<Int> {
                return xs.removeAt(index: index);
              }
              fn set(self, xs: List<Int>, index: Int, item: Int) -> List<Int> {
                return xs.set(index: index, item: item);
              }
              fn remember(self, key: String, value: Int) -> Int {
                let store: Store = Store {};
                store.setInt(key: key, value: value);
                store.setString(key: "name", value: "kata");
                store.setBool(key: "on", value: true);
                store.setFloat(key: "ratio", value: 0.5);
                if (store.getBool(key: "on", fallback: false) && (store.getString(key: "name", fallback: "") == "kata")
                    && (store.getFloat(key: "ratio", fallback: 0.0) == 0.5)) {
                  return store.getInt(key: key, fallback: -1);
                }
                return -2;
              }
              fn forget(self, key: String) -> Int {
                let store: Store = Store {};
                store.remove(key: key);
                return store.getInt(key: key, fallback: -1);
              }
              fn year(self) -> Int {
                return Clock {}.today().year;
              }
              fn now(self) -> Int {
                return Clock {}.now();
              }
              fn hour(self) -> Int {
                return Clock {}.time().hour;
              }
            }
            """;

    static Object s;

    @BeforeAll
    static void compile() throws Exception {
        String source = CheckSupport.wrap(SOURCE).replace("import kata.ui.Ui;",
                "import kata.ui.Ui;\nimport kata.sys.Clock;\nimport kata.sys.Store;");
        ClassLoader loader = ExecSupport.compileProgram(Map.of(CheckSupport.PATH, source));
        s = create(loader, "app.main.S");
    }

    @Test
    void stringQueries() throws Throwable {
        assertEquals(true, call(s, "contains", "hello", "ell"));
        assertEquals(false, call(s, "contains", "hello", "xyz"));
        assertEquals(true, call(s, "starts", "hello", "he"));
        assertEquals(true, call(s, "ends", "hello", "lo"));
        assertEquals(2L, call(s, "indexOf", "日本語😀x", "語"));
        assertEquals(4L, call(s, "indexOf", "日本語😀x", "x"));
        assertEquals(-1L, call(s, "indexOf", "hello", "z"));
    }

    @Test
    void substringCountsCodePoints() throws Throwable {
        assertEquals("ell", call(s, "sub", "hello", 1L, 4L));
        assertEquals("😀x", call(s, "sub", "日本語😀x", 3L, 5L));
        assertEquals("", call(s, "sub", "hello", 2L, 2L));
        assertThrows(IndexOutOfBoundsException.class, () -> call(s, "sub", "hello", 2L, 9L));
        assertThrows(IndexOutOfBoundsException.class, () -> call(s, "sub", "hello", 3L, 2L));
        assertThrows(IndexOutOfBoundsException.class, () -> call(s, "sub", "hello", -1L, 2L));
    }

    @Test
    void splitTrimCaseReplace() throws Throwable {
        KList parts = (KList) call(s, "split", "a,b,,c", ",");
        assertEquals(4L, parts.size());
        assertEquals("", parts.get(2));
        assertEquals("c", parts.get(3));
        assertEquals(1L, ((KList) call(s, "split", "abc", ",")).size());
        assertEquals(2L, ((KList) call(s, "split", "a.b", ".")).size());
        assertThrows(IllegalArgumentException.class, () -> call(s, "split", "abc", ""));
        assertEquals("a b", call(s, "trim", "  a b \n"));
        assertEquals("ABC", call(s, "upper", "abc"));
        assertEquals("abc", call(s, "lower", "ABC"));
        assertEquals("x-y-z", call(s, "replace", "x.y.z", ".", "-"));
    }

    @Test
    void toIntParsesPlainIntegersOnly() throws Throwable {
        assertEquals(42L, call(s, "toInt", "42"));
        assertEquals(-7L, call(s, "toInt", "-7"));
        for (String bad : new String[] {"", "1.5", "abc", " 1", "+1", "１２", "99999999999999999999"}) {
            assertThrows(IllegalArgumentException.class, () -> call(s, "toInt", bad), bad);
        }
    }

    @Test
    void listRemoveAtAndSetReturnNewLists() throws Throwable {
        KList xs = KList.of(new Object[] {1L, 2L, 3L});
        KList removed = (KList) call(s, "removeAt", xs, 1L);
        assertEquals(2L, removed.size());
        assertEquals(3L, removed.get(1));
        KList changed = (KList) call(s, "set", xs, 0L, 9L);
        assertEquals(9L, changed.get(0));
        assertEquals(3L, xs.size());
        assertEquals(1L, xs.get(0));
        assertThrows(IndexOutOfBoundsException.class, () -> call(s, "removeAt", xs, 3L));
        assertThrows(IndexOutOfBoundsException.class, () -> call(s, "set", xs, -1L, 0L));
    }

    @Test
    void storeKeepsValuesByKey() throws Throwable {
        assertEquals(5L, call(s, "remember", "count", 5L));
        assertEquals(-1L, call(s, "forget", "count"));
    }

    @Test
    void clockReadsTheCurrentTime() throws Throwable {
        long year = (Long) call(s, "year");
        assertTrue(year >= 2024 && year < 2200, "year " + year);
        long now = (Long) call(s, "now");
        assertTrue(Math.abs(now - System.currentTimeMillis()) < 60_000);
        long hour = (Long) call(s, "hour");
        assertTrue(hour >= 0 && hour <= 23);
    }
}
