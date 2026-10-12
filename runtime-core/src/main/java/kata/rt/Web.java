package kata.rt;

import java.util.Locale;

/** Which addresses a WebView may show (spec 05 #effects). */
public final class Web {
    private Web() {}

    /**
     * Only web pages (http, https), inline documents (data:) and the empty page are allowed. Everything
     * else is refused, in particular file:, content: and javascript: addresses, which would let a page
     * or an address taken from outside reach files and data on the device.
     */
    public static boolean allowed(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("data:")
                || lower.equals("about:blank");
    }
}
