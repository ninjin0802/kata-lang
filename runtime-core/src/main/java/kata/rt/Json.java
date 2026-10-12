package kata.rt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An immutable JSON value (spec 05 #json). Reading never fails: a missing key, a wrong type or text that
 * is not JSON all give the null value, and the as* methods return their fallback.
 */
public final class Json {
    private static final int MAX_DEPTH = 100;

    /** A Map (object), List (list), String, Long, Double, Boolean, or null. */
    private final Object value;
    private final boolean valid;

    private Json(Object value, boolean valid) {
        this.value = value;
        this.valid = valid;
    }

    /** `Json {}` in kata: an empty object. */
    public Json() {
        this(new LinkedHashMap<String, Json>(), true);
    }

    private static final Json NULL = new Json(null, true);
    private static final Json INVALID = new Json(null, false);

    /** Parses JSON text. Text that is not JSON gives a null value whose isValid() is false. */
    public static Json parse(String text) {
        Parser parser = new Parser(text);
        try {
            parser.skipSpace();
            Json result = parser.value(0);
            parser.skipSpace();
            return parser.pos == text.length() ? result : INVALID;
        } catch (IllegalArgumentException e) {
            return INVALID;
        }
    }

    // ---- reading

    public boolean isValid() {
        return valid;
    }

    public boolean isNull() {
        return value == null;
    }

    public boolean isObject() {
        return value instanceof Map;
    }

    public boolean isList() {
        return value instanceof List;
    }

    public boolean isString() {
        return value instanceof String;
    }

    public boolean isNumber() {
        return value instanceof Long || value instanceof Double;
    }

    public boolean isBool() {
        return value instanceof Boolean;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Json> members() {
        return (Map<String, Json>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Json> elements() {
        return (List<Json>) value;
    }

    /** The member of an object, or the null value. */
    public Json get(String key) {
        Json found = isObject() ? members().get(key) : null;
        return found == null ? NULL : found;
    }

    /** The element of a list, or the null value. */
    public Json at(long index) {
        return isList() && index >= 0 && index < elements().size() ? elements().get((int) index) : NULL;
    }

    /** The number of elements of a list or members of an object; 0 for anything else. */
    public long size() {
        return isList() ? elements().size() : isObject() ? members().size() : 0;
    }

    /** The member names of an object, in their original order. */
    public KList keys() {
        return KList.of(isObject() ? members().keySet().toArray() : new Object[0]);
    }

    /** The elements of a list. */
    public KList items() {
        return KList.of(isList() ? elements().toArray() : new Object[0]);
    }

    public String asString(String fallback) {
        return isString() ? (String) value : fallback;
    }

    public long asInt(long fallback) {
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Double) {
            double number = (Double) value;
            if (number == Math.rint(number) && number >= -9.2e18 && number <= 9.2e18) {
                return (long) number;
            }
        }
        return fallback;
    }

    public double asFloat(double fallback) {
        return value instanceof Long ? (Long) value : value instanceof Double ? (Double) value : fallback;
    }

    public boolean asBool(boolean fallback) {
        return isBool() ? (Boolean) value : fallback;
    }

    // ---- building

    private Json with(String key, Json member) {
        if (!isObject()) {
            return this;
        }
        Map<String, Json> copy = new LinkedHashMap<String, Json>(members());
        copy.put(key, member);
        return new Json(copy, true);
    }

    public Json withString(String key, String value) {
        return with(key, new Json(value, true));
    }

    public Json withInt(String key, long value) {
        return with(key, new Json(value, true));
    }

    public Json withFloat(String key, double value) {
        return with(key, new Json(value, true));
    }

    public Json withBool(String key, boolean value) {
        return with(key, new Json(value, true));
    }

    public Json withNull(String key) {
        return with(key, NULL);
    }

    public Json withJson(String key, Json value) {
        return with(key, value);
    }

    /** A list with the value added at the end. On anything but a list, nothing changes. */
    public Json append(Json item) {
        if (!isList()) {
            return this;
        }
        List<Json> copy = new ArrayList<Json>(elements());
        copy.add(item);
        return new Json(copy, true);
    }

    public Json appendString(String value) {
        return append(new Json(value, true));
    }

    public Json appendInt(long value) {
        return append(new Json(value, true));
    }

    public Json appendFloat(double value) {
        return append(new Json(value, true));
    }

    public Json appendBool(boolean value) {
        return append(new Json(value, true));
    }

    // ---- writing

    /** Compact JSON text. */
    public String toText() {
        StringBuilder out = new StringBuilder();
        write(out);
        return out.toString();
    }

    private void write(StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (isObject()) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Json> member : members().entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(out, member.getKey());
                out.append(':');
                member.getValue().write(out);
            }
            out.append('}');
        } else if (isList()) {
            out.append('[');
            for (int i = 0; i < elements().size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                elements().get(i).write(out);
            }
            out.append(']');
        } else if (isString()) {
            quote(out, (String) value);
        } else if (value instanceof Double) {
            double number = (Double) value;
            out.append(Double.isNaN(number) || Double.isInfinite(number) ? "null" : Floats.toString(number));
        } else {
            out.append(value);
        }
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    /** A strict recursive-descent JSON parser; any deviation throws IllegalArgumentException. */
    private static final class Parser {
        private final String text;
        int pos;

        Parser(String text) {
            this.text = text;
        }

        void skipSpace() {
            while (pos < text.length() && " \t\n\r".indexOf(text.charAt(pos)) >= 0) {
                pos++;
            }
        }

        private IllegalArgumentException bad() {
            return new IllegalArgumentException("not JSON");
        }

        private char peek() {
            if (pos >= text.length()) {
                throw bad();
            }
            return text.charAt(pos);
        }

        private void expect(String word) {
            if (!text.startsWith(word, pos)) {
                throw bad();
            }
            pos += word.length();
        }

        Json value(int depth) {
            if (depth > MAX_DEPTH) {
                throw bad();
            }
            char c = peek();
            if (c == '{') {
                return object(depth);
            }
            if (c == '[') {
                return list(depth);
            }
            if (c == '"') {
                return new Json(string(), true);
            }
            if (c == 't') {
                expect("true");
                return new Json(Boolean.TRUE, true);
            }
            if (c == 'f') {
                expect("false");
                return new Json(Boolean.FALSE, true);
            }
            if (c == 'n') {
                expect("null");
                return NULL;
            }
            return number();
        }

        private Json object(int depth) {
            pos++;
            Map<String, Json> members = new LinkedHashMap<String, Json>();
            skipSpace();
            if (peek() == '}') {
                pos++;
                return new Json(members, true);
            }
            while (true) {
                skipSpace();
                if (peek() != '"') {
                    throw bad();
                }
                String key = string();
                skipSpace();
                if (peek() != ':') {
                    throw bad();
                }
                pos++;
                skipSpace();
                members.put(key, value(depth + 1));
                skipSpace();
                char next = peek();
                pos++;
                if (next == '}') {
                    return new Json(members, true);
                }
                if (next != ',') {
                    throw bad();
                }
            }
        }

        private Json list(int depth) {
            pos++;
            List<Json> elements = new ArrayList<Json>();
            skipSpace();
            if (peek() == ']') {
                pos++;
                return new Json(elements, true);
            }
            while (true) {
                skipSpace();
                elements.add(value(depth + 1));
                skipSpace();
                char next = peek();
                pos++;
                if (next == ']') {
                    return new Json(elements, true);
                }
                if (next != ',') {
                    throw bad();
                }
            }
        }

        private String string() {
            pos++;
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = peek();
                pos++;
                if (c == '"') {
                    return out.toString();
                }
                if (c < 0x20) {
                    throw bad();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = peek();
                pos++;
                switch (escape) {
                    case '"':
                    case '\\':
                    case '/':
                        out.append(escape);
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > text.length()) {
                            throw bad();
                        }
                        try {
                            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException e) {
                            throw bad();
                        }
                        pos += 4;
                        break;
                    default:
                        throw bad();
                }
            }
        }

        private Json number() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            int digits = pos;
            while (pos < text.length() && Character.isDigit(text.charAt(pos)) && text.charAt(pos) < 128) {
                pos++;
            }
            if (pos == digits || (text.charAt(digits) == '0' && pos - digits > 1)) {
                throw bad();
            }
            boolean integral = true;
            if (pos < text.length() && text.charAt(pos) == '.') {
                integral = false;
                pos++;
                int fraction = pos;
                while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
                    pos++;
                }
                if (pos == fraction) {
                    throw bad();
                }
            }
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                integral = false;
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                int exponent = pos;
                while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
                    pos++;
                }
                if (pos == exponent) {
                    throw bad();
                }
            }
            String literal = text.substring(start, pos);
            if (integral) {
                try {
                    return new Json(Long.parseLong(literal), true);
                } catch (NumberFormatException tooLarge) {
                    // falls through to a Float
                }
            }
            return new Json(Double.parseDouble(literal), true);
        }
    }
}
