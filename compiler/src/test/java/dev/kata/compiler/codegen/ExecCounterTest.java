package dev.kata.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.kata.compiler.syntax.ParserTestSources;
import java.util.Map;
import kata.rt.Button;
import kata.rt.Column;
import kata.rt.Screen;
import kata.rt.Text;
import org.junit.jupiter.api.Test;

class ExecCounterTest {
    private static final Map<String, String> SOURCES = Map.of("src/app/counter.kata", ParserTestSources.COUNTER);

    @Test
    void counterViewFollowsItsState() throws Exception {
        ClassLoader loader = ExecSupport.compileProgram(SOURCES);
        Screen counter = (Screen) ExecSupport.create(loader, "app.counter.Counter");

        Column column = assertInstanceOf(Column.class, counter.view());
        assertEquals(3L, column.children.size());
        assertEquals("0", ((Text) column.children.get(0)).text);
        Button plus = (Button) column.children.get(1);
        Button reset = (Button) column.children.get(2);
        assertEquals("+1", plus.label);
        assertEquals("Reset", reset.label);

        plus.onTap.run();
        assertEquals("1", ((Text) ((Column) counter.view()).children.get(0)).text);
        plus.onTap.run();
        reset.onTap.run();
        assertEquals("0", ((Text) ((Column) counter.view()).children.get(0)).text);
    }
}
