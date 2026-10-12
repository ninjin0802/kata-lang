package kata.rt;

import java.util.Locale;

/** Things the app asks of the device: opening links, sharing, the clipboard, the screen (spec 05 #device). */
public final class Device {
    /** How the requests are carried out. The Android runtime installs the real one. */
    public interface Backend {
        void openUrl(String url);

        void share(String text);

        void copy(String text);

        String clipboard();

        long screenWidth();

        long screenHeight();

        boolean isDark();
    }

    /** Used off the device (tests): the clipboard is a field and nothing else has an effect. */
    private static Backend backend = new Backend() {
        private String copied = "";

        @Override
        public void openUrl(String url) {}

        @Override
        public void share(String text) {}

        @Override
        public void copy(String text) {
            copied = text;
        }

        @Override
        public String clipboard() {
            return copied;
        }

        @Override
        public long screenWidth() {
            return 0;
        }

        @Override
        public long screenHeight() {
            return 0;
        }

        @Override
        public boolean isDark() {
            return false;
        }
    };

    /** Replaces the backend and returns the previous one. */
    public static Backend install(Backend replacement) {
        Backend previous = backend;
        backend = replacement;
        return previous;
    }

    /**
     * Only web, mail and phone addresses are handed to other apps. Anything else is ignored: an address
     * taken from outside (a server reply, a pasted text) must not be able to start arbitrary apps or
     * reach files through this app.
     */
    public static boolean canOpen(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:")
                || lower.startsWith("tel:");
    }

    public Device() {}

    public void openUrl(String url) {
        if (canOpen(url)) {
            backend.openUrl(url);
        }
    }

    public void share(String text) {
        backend.share(text);
    }

    public void copy(String text) {
        backend.copy(text);
    }

    public String clipboard() {
        return backend.clipboard();
    }

    /** The width of the app's area in dp. */
    public long screenWidth() {
        return backend.screenWidth();
    }

    /** The height of the app's area in dp. */
    public long screenHeight() {
        return backend.screenHeight();
    }

    public boolean isDark() {
        return backend.isDark();
    }
}
