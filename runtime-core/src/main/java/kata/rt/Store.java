package kata.rt;

import java.util.HashMap;
import java.util.Map;

/** Small values kept by key across runs of the app (spec 05 #store). */
public final class Store {
    /** Where the values are kept. Everything is stored as text. */
    public interface Backend {
        /** The stored text, or null when the key is absent. */
        String get(String key);

        void put(String key, String value);

        void remove(String key);
    }

    private static Backend backend = new Backend() {
        private final Map<String, String> values = new HashMap<String, String>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }
    };

    /** Replaces the in-memory default; the Android runtime installs one backed by the device. */
    public static void install(Backend replacement) {
        backend = replacement;
    }

    public Store() {}

    public String getString(String key, String fallback) {
        String value = backend.get(key);
        return value == null ? fallback : value;
    }

    public void setString(String key, String value) {
        backend.put(key, value);
    }

    public long getInt(String key, long fallback) {
        String value = backend.get(key);
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void setInt(String key, long value) {
        backend.put(key, Long.toString(value));
    }

    public double getFloat(String key, double fallback) {
        String value = backend.get(key);
        try {
            return value == null ? fallback : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void setFloat(String key, double value) {
        backend.put(key, Double.toString(value));
    }

    public boolean getBool(String key, boolean fallback) {
        String value = backend.get(key);
        return value == null ? fallback : value.equals("true");
    }

    public void setBool(String key, boolean value) {
        backend.put(key, Boolean.toString(value));
    }

    public void remove(String key) {
        backend.remove(key);
    }
}
