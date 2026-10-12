package kata.rt;

/** The built-in methods of String beyond what java.lang.String offers directly (spec 04 #builtin-methods). */
public final class Strings {
    private Strings() {}

    public static boolean contains(String text, String part) {
        return text.contains(part);
    }

    public static boolean startsWith(String text, String prefix) {
        return text.startsWith(prefix);
    }

    public static boolean endsWith(String text, String suffix) {
        return text.endsWith(suffix);
    }

    /** The position of the first occurrence, counted in code points, or -1. */
    public static long indexOf(String text, String part) {
        int index = text.indexOf(part);
        return index < 0 ? -1 : text.codePointCount(0, index);
    }

    /** The characters from start (inclusive) to end (exclusive), counted in code points. */
    public static String substring(String text, long start, long end) {
        long length = text.codePointCount(0, text.length());
        if (start < 0 || end > length || start > end) {
            throw new IndexOutOfBoundsException(
                    "substring " + start + ".." + end + " out of range for string of length " + length);
        }
        int from = text.offsetByCodePoints(0, (int) start);
        int to = text.offsetByCodePoints(from, (int) (end - start));
        return text.substring(from, to);
    }

    /** Splits at every occurrence of the separator, keeping empty pieces. */
    public static KList split(String text, String separator) {
        if (separator.isEmpty()) {
            throw new IllegalArgumentException("String.split: separator is empty");
        }
        java.util.ArrayList<Object> parts = new java.util.ArrayList<Object>();
        int from = 0;
        while (true) {
            int at = text.indexOf(separator, from);
            if (at < 0) {
                parts.add(text.substring(from));
                break;
            }
            parts.add(text.substring(from, at));
            from = at + separator.length();
        }
        return KList.of(parts.toArray());
    }

    public static String trim(String text) {
        return text.trim();
    }

    public static String toUpper(String text) {
        return text.toUpperCase(java.util.Locale.ROOT);
    }

    public static String toLower(String text) {
        return text.toLowerCase(java.util.Locale.ROOT);
    }

    public static String replace(String text, String part, String with) {
        return text.replace(part, with);
    }

    /** Reads the text as JSON; text that is not JSON gives a null value whose isValid() is false. */
    public static Json parseJson(String text) {
        return Json.parse(text);
    }

    /** Parses `-`? digits and nothing else. */
    public static long toInt(String text) {
        int start = text.startsWith("-") ? 1 : 0;
        boolean valid = text.length() > start;
        for (int i = start; valid && i < text.length(); i++) {
            valid = text.charAt(i) >= '0' && text.charAt(i) <= '9';
        }
        if (valid) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                // out of range: falls through to the same error
            }
        }
        throw new IllegalArgumentException("String.toInt: not an integer: \"" + text + "\"");
    }
}
