package kata.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeviceTest {
    @Test
    void onlyWebMailAndPhoneAddressesCanBeOpened() {
        for (String url : new String[] {"https://example.com", "http://example.com/a", "mailto:a@example.com",
            "tel:+81312345678", "HTTPS://EXAMPLE.COM"}) {
            assertTrue(Device.canOpen(url), url);
        }
        for (String url : new String[] {"file:///sdcard/a.txt", "content://contacts", "intent://x#Intent;end",
            "javascript:alert(1)", "market://details?id=x", "", "example.com", " https://example.com",
            "android-app://com.example", "geo:0,0"}) {
            assertFalse(Device.canOpen(url), url);
        }
    }

    @Test
    void addressesAreParsedNotJustPrefixMatched() {
        // What is handed on is the parsed, re-written address, so the check and the use cannot disagree.
        assertEquals("https://EXAMPLE.COM/a?b=c", Device.normalize("HTTPS://EXAMPLE.COM/a?b=c"));
        assertEquals("mailto:a@example.com", Device.normalize("MAILTO:a@example.com"));
        assertEquals("tel:+81312345678", Device.normalize("tel:+81312345678"));
        for (String url : new String[] {"https://", "http:///path", "https:example.com", "https:\\\\example.com",
            "https://exa mple.com", "https://example.com\nfile:///etc/hosts", "http://\u0000", "mailto:", "tel:",
            "https://[bad", "HtTp:/x"}) {
            assertEquals(null, Device.normalize(url), url);
            assertFalse(Device.canOpen(url), url);
        }
    }

    @Test
    void refusedAddressesNeverReachTheBackend() {
        final List<String> opened = new ArrayList<>();
        Device.Backend previous = Device.install(new Device.Backend() {
            @Override
            public void openUrl(String url) {
                opened.add(url);
            }

            @Override
            public void share(String text) {}

            @Override
            public void copy(String text) {}

            @Override
            public String clipboard() {
                return "";
            }

            @Override
            public long screenWidth() {
                return 360;
            }

            @Override
            public long screenHeight() {
                return 640;
            }

            @Override
            public boolean isDark() {
                return false;
            }
        });
        try {
            Device device = new Device();
            device.openUrl("file:///etc/hosts");
            device.openUrl("https://");
            device.openUrl("HTTPS://example.com");
            assertEquals(List.of("https://example.com"), opened, "the backend gets the normalized address");
            assertEquals(360L, device.screenWidth());
        } finally {
            Device.install(previous);
        }
    }

    @Test
    void theDefaultBackendKeepsTheClipboardInMemory() {
        Device device = new Device();
        device.copy("kata");
        assertEquals("kata", device.clipboard());
        assertFalse(device.isDark());
    }
}
