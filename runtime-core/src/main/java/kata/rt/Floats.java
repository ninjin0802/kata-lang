package kata.rt;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The built-in methods of Float, and String.toFloat (spec 04 #builtin-methods). */
public final class Floats {
    private Floats() {}

    /** Plain decimal notation, never an exponent, always with a decimal point. */
    public static String toString(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (value == 0) {
            return 1 / value < 0 ? "-0.0" : "0.0";
        }
        String plain = new BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString();
        return plain.indexOf('.') < 0 ? plain + ".0" : plain;
    }

    /** Truncates toward zero; out-of-range values saturate and NaN becomes 0. */
    public static long toInt(double value) {
        return (long) value;
    }

    /** Rounds to the given number of decimals, ties away from zero. */
    public static double round(double value, long decimals) {
        if (decimals < 0) {
            throw new IllegalArgumentException("Float.round decimals out of range: " + decimals);
        }
        // A double carries fewer than 400 decimal digits, so more than that changes nothing.
        if (Double.isNaN(value) || Double.isInfinite(value) || decimals > 400) {
            return value;
        }
        return BigDecimal.valueOf(value).setScale((int) decimals, RoundingMode.HALF_UP).doubleValue();
    }

    /** Parses `-`? digits ( `.` digits* )? and nothing else. */
    public static double parse(String text) {
        int length = text.length();
        int index = length > 0 && text.charAt(0) == '-' ? 1 : 0;
        int digits = 0;
        while (index < length && isDigit(text.charAt(index))) {
            index++;
            digits++;
        }
        if (digits > 0 && index < length && text.charAt(index) == '.') {
            index++;
            while (index < length && isDigit(text.charAt(index))) {
                index++;
            }
        }
        if (digits == 0 || index != length) {
            throw new IllegalArgumentException("String.toFloat: not a number: \"" + text + "\"");
        }
        return Double.parseDouble(text);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
