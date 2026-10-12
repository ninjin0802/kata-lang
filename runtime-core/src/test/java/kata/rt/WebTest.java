package kata.rt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebTest {
    @Test
    void webPagesAndInlineDocumentsAreAllowed() {
        assertTrue(Web.allowed("https://example.com/a?b=c"));
        assertTrue(Web.allowed("http://example.com"));
        assertTrue(Web.allowed("HTTPS://EXAMPLE.COM"));
        assertTrue(Web.allowed("data:text/html,<h1>x</h1>"));
        assertTrue(Web.allowed("about:blank"));
    }

    @Test
    void addressesThatReachTheDeviceAreRefused() {
        for (String url : new String[] {"file:///data/data/app/shared_prefs/kata.xml", "FILE:///etc/hosts",
            "content://com.android.contacts/contacts", "javascript:alert(1)", "intent://x#Intent;end",
            "  file:///etc/hosts", "\tjavascript:alert(1)", "", "example.com", "ftp://example.com", "about:config"}) {
            assertFalse(Web.allowed(url), url);
        }
    }
}
