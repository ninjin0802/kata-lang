package kata.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FloatsTest {
    @Test
    void toStringIsPlainDecimalWithAPoint() {
        assertEquals("3.0", Floats.toString(3.0));
        assertEquals("0.1", Floats.toString(0.1));
        assertEquals("-2.5", Floats.toString(-2.5));
        assertEquals("0.30000000000000004", Floats.toString(0.1 + 0.2));
        assertEquals("10000000000.0", Floats.toString(1e10));
        assertEquals("100000000000000000000.0", Floats.toString(1e20));
        assertEquals("0.00001", Floats.toString(1e-5));
        assertEquals("0.0", Floats.toString(0.0));
        assertEquals("-0.0", Floats.toString(-0.0));
    }

    @Test
    void toStringNamesNonFiniteValues() {
        assertEquals("Infinity", Floats.toString(Double.POSITIVE_INFINITY));
        assertEquals("-Infinity", Floats.toString(Double.NEGATIVE_INFINITY));
        assertEquals("NaN", Floats.toString(Double.NaN));
    }

    @Test
    void toIntTruncatesAndSaturates() {
        assertEquals(3L, Floats.toInt(3.99));
        assertEquals(-3L, Floats.toInt(-3.99));
        assertEquals(Long.MAX_VALUE, Floats.toInt(1e30));
        assertEquals(Long.MIN_VALUE, Floats.toInt(-1e30));
        assertEquals(0L, Floats.toInt(Double.NaN));
    }

    @Test
    void roundKeepsTheGivenDecimals() {
        assertEquals(0.3, Floats.round(0.1 + 0.2, 10));
        assertEquals(3.14, Floats.round(3.14159, 2));
        assertEquals(3.0, Floats.round(3.14159, 0));
        assertEquals(2.5, Floats.round(2.45, 1));
        assertEquals(-2.5, Floats.round(-2.45, 1));
        assertEquals(1.0, Floats.round(0.5, 0));
        assertEquals(1e300, Floats.round(1e300, 3));
        assertTrue(Double.isNaN(Floats.round(Double.NaN, 2)));
        assertEquals(Double.POSITIVE_INFINITY, Floats.round(Double.POSITIVE_INFINITY, 2));
    }

    @Test
    void roundRejectsNegativeDecimals() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Floats.round(1.5, -1));
        assertEquals("Float.round decimals out of range: -1", e.getMessage());
    }

    @Test
    void parseAcceptsPlainDecimals() {
        assertEquals(12.0, Floats.parse("12"));
        assertEquals(-0.5, Floats.parse("-0.5"));
        assertEquals(3.0, Floats.parse("3."));
        assertEquals(0.25, Floats.parse("0.25"));
        assertEquals(7.0, Floats.parse("007"));
    }

    @Test
    void parseRejectsEverythingElse() {
        for (String text : new String[] {"", ".", ".5", "-", "1e5", "NaN", "Infinity", "0x10", "1.5d", " 1", "1 ",
            "１２", "+1", "1.2.3", "--1"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Floats.parse(text),
                    text);
            assertEquals("String.toFloat: not a number: \"" + text + "\"", e.getMessage());
        }
    }
}
