package kata.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HttpTest {
    static HttpServer server;
    static String base;

    @BeforeAll
    static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] reply = (exchange.getRequestMethod() + ":" + body + ":日本語").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.createContext("/big", exchange -> {
            // No Content-Length: the size is only known by reading.
            exchange.sendResponseHeaders(200, 0);
            byte[] chunk = new byte[1024];
            try {
                for (int i = 0; i < 64; i++) {
                    exchange.getResponseBody().write(chunk);
                }
            } catch (IOException closedByClient) {
                // expected once the client gives up
            }
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @Test
    void getReturnsTheBodyAsUtf8() throws IOException {
        assertEquals("GET::日本語", Http.send("GET", base + "/echo", ""));
    }

    @Test
    void postSendsTheBody() throws IOException {
        assertEquals("POST:こんにちは:日本語", Http.send("POST", base + "/echo", "こんにちは"));
    }

    @Test
    void responseLargerThanTheLimitIsRefused() {
        IOException e = assertThrows(IOException.class, () -> Http.send("GET", base + "/big", "", 10_000));
        assertEquals("response larger than 10000 bytes", e.getMessage());
    }

    @Test
    void responseWithinTheLimitIsReturned() throws IOException {
        assertEquals(64 * 1024, Http.send("GET", base + "/big", "", 64 * 1024).length());
        assertEquals(8L * 1024 * 1024, Http.MAX_RESPONSE_BYTES);
    }

    @Test
    void putAndDeleteAreSentAsThemselves() throws IOException {
        assertEquals("PUT:x:日本語", Http.send("PUT", base + "/echo", "x"));
        assertEquals("DELETE::日本語", Http.send("DELETE", base + "/echo", ""));
    }

    @Test
    void errorStatusIsAnIOException() {
        IOException e = assertThrows(IOException.class, () -> Http.send("GET", base + "/missing", ""));
        assertEquals("HTTP 404", e.getMessage());
    }

    @Test
    void unsupportedAddressesAndMethodsAreIOExceptions() {
        assertThrows(IOException.class, () -> Http.send("GET", "file:///etc/hostname", ""));
        assertThrows(IOException.class, () -> Http.send("GET", "not a url", ""));
        assertThrows(IOException.class, () -> Http.send("FETCH", base + "/echo", ""));
        IOException patch = assertThrows(IOException.class, () -> Http.send("PATCH", base + "/echo", "x"));
        assertEquals("unsupported method: PATCH", patch.getMessage());
        IOException refused = assertThrows(IOException.class, () -> Http.send("GET", "http://127.0.0.1:1/", ""));
        assertTrue(refused.getMessage() != null);
    }
}
