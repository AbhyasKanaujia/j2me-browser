import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.midlet.MIDlet;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

public class MainMIDlet extends MIDlet implements CommandListener {
    private static final String FETCH_URL = "http://info.cern.ch/";
    private static final int MAX_REDIRECTS = 5;

    private final Display display;
    private final BrowserCanvas canvas;
    private final Command exitCommand;
    private final Hashtable cookieJars = new Hashtable();
    private boolean fetchStarted;

    public MainMIDlet() {
        display = Display.getDisplay(this);
        canvas = new BrowserCanvas();
        exitCommand = new Command("Exit", Command.EXIT, 1);
        canvas.addCommand(exitCommand);
        canvas.setCommandListener(this);
    }

    public void startApp() {
        display.setCurrent(canvas);
        if (!fetchStarted) {
            fetchStarted = true;
            new Thread(new Runnable() {
                public void run() {
                    fetch();
                }
            }).start();
        }
    }

    private void fetch() {
        String url = FETCH_URL;
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
                String redirectNote = (redirects > 0)
                        ? (" (after " + redirects + " redirect" + (redirects == 1 ? "" : "s") + ")")
                        : "";
                status = "HTTP " + responseCode + redirectNote + " - " + responseBytes.length
                        + " bytes from " + url;
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

        canvas.setStatus(status);
        if (responseBytes != null) {
            String pageText;
            try {
                pageText = new String(responseBytes, "UTF-8");
            } catch (Exception e) {
                pageText = new String(responseBytes);
            }
            canvas.setPageText(pageText);
        }
        canvas.repaint();
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
    private void applyCookies(HttpConnection connection, String url) throws java.io.IOException {
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
    private void storeCookie(String url, String setCookieHeader) {
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

    public void pauseApp() {
    }

    public void destroyApp(boolean unconditional) {
    }

    public void commandAction(Command command, Displayable displayable) {
        if (command == exitCommand) {
            notifyDestroyed();
        }
    }

    private static final class BrowserCanvas extends Canvas {
        private final Font bodyFont;
        private String statusLine = "Connecting...";
        private Vector lines = new Vector();
        private boolean hasContent;
        private int scrollOffset;

        BrowserCanvas() {
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }

        void setStatus(String status) {
            this.statusLine = status;
        }

        void setPageText(String text) {
            this.lines = wrap(text, getWidth() - 4, bodyFont);
            this.scrollOffset = 0;
            this.hasContent = true;
        }

        private static boolean isSpace(char c) {
            return c == ' ' || c == '\t' || c == '\r' || c == '\n';
        }

        // CLDC 1.1 has no java.util.StringTokenizer, so words are split by hand.
        private static Vector wrap(String text, int maxWidth, Font font) {
            Vector result = new Vector();
            if (maxWidth <= 0) {
                return result;
            }
            StringBuffer currentLine = new StringBuffer();
            StringBuffer currentWord = new StringBuffer();
            int len = text.length();
            for (int i = 0; i <= len; i++) {
                char c = (i < len) ? text.charAt(i) : ' ';
                if (!isSpace(c)) {
                    currentWord.append(c);
                    continue;
                }
                if (currentWord.length() == 0) {
                    continue;
                }
                currentLine = addWord(result, currentLine, currentWord.toString(), maxWidth, font);
                currentWord = new StringBuffer();
            }
            if (currentLine.length() > 0) {
                result.addElement(currentLine.toString());
            }
            return result;
        }

        // Appends word to currentLine, flushing to result as lines fill up. A single
        // word wider than maxWidth on its own (e.g. a long URL) is hard-broken by
        // character, since the display has no horizontal scroll to fall back on.
        private static StringBuffer addWord(Vector result, StringBuffer currentLine, String word, int maxWidth, Font font) {
            if (currentLine.length() > 0) {
                String candidate = currentLine.toString() + " " + word;
                if (font.stringWidth(candidate) <= maxWidth) {
                    currentLine.append(' ');
                    currentLine.append(word);
                    return currentLine;
                }
                result.addElement(currentLine.toString());
                currentLine = new StringBuffer();
            }
            if (font.stringWidth(word) <= maxWidth) {
                currentLine.append(word);
                return currentLine;
            }
            StringBuffer chunk = new StringBuffer();
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (chunk.length() > 0 && font.stringWidth(chunk.toString() + c) > maxWidth) {
                    result.addElement(chunk.toString());
                    chunk = new StringBuffer();
                }
                chunk.append(c);
            }
            return chunk;
        }

        protected void keyPressed(int keyCode) {
            handleKey(keyCode);
        }

        protected void keyRepeated(int keyCode) {
            handleKey(keyCode);
        }

        private void handleKey(int keyCode) {
            int action;
            try {
                action = getGameAction(keyCode);
            } catch (IllegalArgumentException e) {
                action = 0;
            }
            if (action == UP) {
                scrollOffset--;
                if (scrollOffset < 0) {
                    scrollOffset = 0;
                }
                repaint();
            } else if (action == DOWN) {
                scrollOffset++;
                repaint();
            }
        }

        protected void paint(Graphics graphics) {
            int width = getWidth();
            int height = getHeight();

            graphics.setColor(0xFFFFFF);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(0x000000);
            graphics.setFont(bodyFont);

            int y = 2;
            Vector statusLines = wrap(statusLine, width - 4, bodyFont);
            for (int i = 0; i < statusLines.size(); i++) {
                graphics.drawString((String) statusLines.elementAt(i), 2, y, Graphics.LEFT | Graphics.TOP);
                y += bodyFont.getHeight();
            }
            y += 2;

            if (!hasContent) {
                return;
            }

            int lineHeight = bodyFont.getHeight();
            int visibleLines = (height - y) / lineHeight;
            if (visibleLines < 1) {
                visibleLines = 1;
            }
            int maxOffset = lines.size() - visibleLines;
            if (maxOffset < 0) {
                maxOffset = 0;
            }
            if (scrollOffset > maxOffset) {
                scrollOffset = maxOffset;
            }

            int end = scrollOffset + visibleLines;
            if (end > lines.size()) {
                end = lines.size();
            }
            for (int i = scrollOffset; i < end; i++) {
                graphics.drawString((String) lines.elementAt(i), 2, y, Graphics.LEFT | Graphics.TOP);
                y += lineHeight;
            }
        }
    }
}
