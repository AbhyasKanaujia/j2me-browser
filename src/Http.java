import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;

// Transport layer: fetch with redirect-following and an in-memory, per-host
// cookie jar. See docs/adr/0006-one-file-per-concern.md for why this is its
// own file.
final class Http {
    private Http() {
    }

    private static final int MAX_REDIRECTS = 5;
    private static final Hashtable cookieJars = new Hashtable();

    static final class Result {
        final String status;
        final byte[] body;
        final String finalUrl;

        Result(String status, byte[] body, String finalUrl) {
            this.status = status;
            this.body = body;
            this.finalUrl = finalUrl;
        }
    }

    static Result fetch(String startUrl) {
        String url = startUrl;
        String status;
        byte[] responseBytes = null;
        int redirects = 0;

        fetchLoop:
        while (true) {
            HttpConnection connection = null;
            InputStream in = null;
            try {
                connection = (HttpConnection) Connector.open(url);
                applyCookies(connection, url);
                int responseCode = connection.getResponseCode();
                storeCookie(url, connection.getHeaderField("Set-Cookie"));

                if (isRedirect(responseCode)) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.length() == 0) {
                        status = "HTTP " + responseCode + " redirect with no Location header from " + url;
                        break fetchLoop;
                    }
                    if (redirects >= MAX_REDIRECTS) {
                        status = "Too many redirects (stopped after " + redirects + " at " + url + ")";
                        break fetchLoop;
                    }
                    String nextUrl = resolveUrl(url, location);
                    if (nextUrl == null) {
                        status = "Redirected to unsupported URL: " + location;
                        break fetchLoop;
                    }
                    redirects++;
                    url = nextUrl;
                    continue fetchLoop;
                }

                in = connection.openInputStream();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[256];
                int read;
                while ((read = in.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                responseBytes = buffer.toByteArray();
                if (responseCode >= 200 && responseCode < 300) {
                    status = "";
                } else {
                    String redirectNote = (redirects > 0)
                            ? (" (after " + redirects + " redirect" + (redirects == 1 ? "" : "s") + ")")
                            : "";
                    status = "HTTP " + responseCode + redirectNote + " from " + url;
                }
            } catch (Exception e) {
                status = "Fetch failed: " + e;
            } finally {
                try {
                    if (in != null) in.close();
                } catch (Exception ignored) {
                }
                try {
                    if (connection != null) connection.close();
                } catch (Exception ignored) {
                }
            }
            break fetchLoop;
        }

        return new Result(status, responseBytes, url);
    }

    private static boolean isRedirect(int responseCode) {
        return responseCode == 301 || responseCode == 302 || responseCode == 303
                || responseCode == 307 || responseCode == 308;
    }

    // CLDC 1.1 has no java.net.URL, so relative Location headers are resolved by hand.
    // Handles absolute URLs, protocol-relative ("//host/path"), and absolute-path
    // ("/path") locations -- the common cases for server-issued redirects. Locations
    // relative to the current path (e.g. "next.html") are resolved against the
    // current path's directory.
    private static String resolveUrl(String base, String location) {
        if (location.indexOf("://") >= 0) {
            return location;
        }
        int schemeEnd = base.indexOf("://");
        if (schemeEnd < 0) {
            return null;
        }
        String scheme = base.substring(0, schemeEnd);
        int authorityStart = schemeEnd + 3;
        int pathStart = base.indexOf('/', authorityStart);
        String authority = (pathStart < 0) ? base.substring(authorityStart) : base.substring(authorityStart, pathStart);
        String basePath = (pathStart < 0) ? "/" : base.substring(pathStart);

        if (location.startsWith("//")) {
            return scheme + ":" + location;
        }
        if (location.startsWith("/")) {
            return scheme + "://" + authority + location;
        }
        int lastSlash = basePath.lastIndexOf('/');
        String baseDir = (lastSlash >= 0) ? basePath.substring(0, lastSlash + 1) : "/";
        return scheme + "://" + authority + baseDir + location;
    }

    private static String authorityOf(String url) {
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return url;
        }
        int authorityStart = schemeEnd + 3;
        int pathStart = url.indexOf('/', authorityStart);
        return (pathStart < 0) ? url.substring(authorityStart) : url.substring(authorityStart, pathStart);
    }

    // In-memory, per-host, session-only cookie jar: just name=value pairs, no
    // Domain/Path/Expires/Secure attribute handling and no persistence across runs.
    // Keeping it this narrow is deliberate -- full RFC 6265 semantics are out of
    // scope for what this milestone needs.
    private static void applyCookies(HttpConnection connection, String url) throws IOException {
        Hashtable jar = (Hashtable) cookieJars.get(authorityOf(url));
        if (jar == null || jar.isEmpty()) {
            return;
        }
        StringBuffer header = new StringBuffer();
        Enumeration names = jar.keys();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            if (header.length() > 0) {
                header.append("; ");
            }
            header.append(name).append('=').append((String) jar.get(name));
        }
        connection.setRequestProperty("Cookie", header.toString());
    }

    // Only reads the first Set-Cookie header -- servers that set multiple cookies
    // on one response beyond the first are outside this milestone's scope.
    private static void storeCookie(String url, String setCookieHeader) {
        if (setCookieHeader == null || setCookieHeader.length() == 0) {
            return;
        }
        int semicolon = setCookieHeader.indexOf(';');
        String pair = (semicolon < 0) ? setCookieHeader : setCookieHeader.substring(0, semicolon);
        int equals = pair.indexOf('=');
        if (equals <= 0) {
            return;
        }
        String name = pair.substring(0, equals).trim();
        String value = pair.substring(equals + 1).trim();
        if (name.length() == 0) {
            return;
        }
        String host = authorityOf(url);
        Hashtable jar = (Hashtable) cookieJars.get(host);
        if (jar == null) {
            jar = new Hashtable();
            cookieJars.put(host, jar);
        }
        jar.put(name, value);
    }
}
