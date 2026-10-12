package kata.rt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** One blocking HTTP exchange, used by the Request component from a background thread. */
public final class Http {
    private Http() {}

    private static final List<String> METHODS = Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final int TIMEOUT_MILLIS = 15000;

    /**
     * @return the response body decoded as UTF-8
     * @throws IOException for a bad address or method, a network failure, or a status outside 200-299
     *     (message `HTTP <status>`)
     */
    public static String send(String method, String url, String body) throws IOException {
        if (!METHODS.contains(method)) {
            throw new IOException("unsupported method: " + method);
        }
        URL address;
        try {
            address = new URL(url);
        } catch (java.net.MalformedURLException e) {
            throw new IOException("bad address: " + url);
        }
        if (!address.getProtocol().equals("http") && !address.getProtocol().equals("https")) {
            throw new IOException("unsupported address: " + url);
        }
        HttpURLConnection connection = (HttpURLConnection) address.openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            if (method.equals("PATCH")) {
                // HttpURLConnection rejects PATCH; servers accept it through this override.
                connection.setRequestMethod("POST");
                connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            } else {
                connection.setRequestMethod(method);
            }
            if (!body.isEmpty()) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(bytes);
                }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status > 299) {
                throw new IOException("HTTP " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                for (int read = in.read(chunk); read >= 0; read = in.read(chunk)) {
                    buffer.write(chunk, 0, read);
                }
                return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
