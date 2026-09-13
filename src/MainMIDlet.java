import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
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
    private final Command goToCommand;
    private final Command goCommand;
    private final Command cancelCommand;
    private final Command backCommand;
    private final Command forwardCommand;
    private final Hashtable cookieJars = new Hashtable();
    private final Vector backStack = new Vector();
    private final Vector forwardStack = new Vector();
    private boolean fetchStarted;
    private String currentUrl = FETCH_URL;

    public MainMIDlet() {
        display = Display.getDisplay(this);
        canvas = new BrowserCanvas();
        exitCommand = new Command("Exit", Command.EXIT, 1);
        goToCommand = new Command("Go to...", Command.SCREEN, 1);
        goCommand = new Command("Go", Command.OK, 1);
        cancelCommand = new Command("Cancel", Command.CANCEL, 1);
        backCommand = new Command("Back", Command.BACK, 1);
        forwardCommand = new Command("Forward", Command.SCREEN, 1);
        canvas.addCommand(exitCommand);
        canvas.addCommand(goToCommand);
        canvas.setCommandListener(this);
    }

    public void startApp() {
        display.setCurrent(canvas);
        if (!fetchStarted) {
            fetchStarted = true;
            startFetch(FETCH_URL);
        }
    }

    private void openAddressBar() {
        TextBox box = new TextBox("Go to address", currentUrl, 256, TextField.URL);
        box.addCommand(goCommand);
        box.addCommand(cancelCommand);
        box.setCommandListener(this);
        display.setCurrent(box);
    }

    private static String normalizeUrl(String input) {
        String trimmed = input.trim();
        if (trimmed.length() == 0) {
            return null;
        }
        if (trimmed.indexOf("://") < 0) {
            return "http://" + trimmed;
        }
        return trimmed;
    }

    private void startFetch(final String url) {
        new Thread(new Runnable() {
            public void run() {
                fetch(url);
            }
        }).start();
    }

    private void fetch(String startUrl) {
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

        currentUrl = url;
        canvas.setStatus(status);
        if (responseBytes != null) {
            String pageText;
            try {
                pageText = new String(responseBytes, "UTF-8");
            } catch (Exception e) {
                pageText = new String(responseBytes);
            }
            canvas.setPageText(stripTags(pageText));
        } else {
            canvas.clearContent();
        }
        canvas.repaint();
    }

    // Strips tags down to plain text: no block/paragraph structure preserved yet
    // (that's layout, a later stage), just tag-soup -> readable text. <script> and
    // <style> element content is dropped entirely rather than shown as text.
    //
    // Block-level tags queue a forced line break (rendered by BrowserCanvas.wrap,
    // which treats '\n' specially) instead of the plain space used for inline
    // tags, so paragraphs/headings/list items land on their own line rather than
    // running together. <li> also queues a "- " prefix -- ordered and unordered
    // lists both just get a dash, no item numbering yet.
    private static String stripTags(String html) {
        StringBuffer out = new StringBuffer();
        int len = html.length();
        int i = 0;
        String skipUntil = null;
        int pendingBreaks = 0;
        String pendingPrefix = null;
        while (i < len) {
            char c = html.charAt(i);
            if (c != '<') {
                if (skipUntil == null) {
                    if (isSpace(c)) {
                        if (pendingBreaks == 0 && out.length() > 0) {
                            out.append(' ');
                        }
                    } else {
                        while (pendingBreaks > 0) {
                            out.append('\n');
                            pendingBreaks--;
                        }
                        if (pendingPrefix != null) {
                            out.append(pendingPrefix);
                            pendingPrefix = null;
                        }
                        out.append(c);
                    }
                }
                i++;
                continue;
            }
            if (html.regionMatches(true, i, "<!--", 0, 4)) {
                int end = html.indexOf("-->", i + 4);
                i = (end < 0) ? len : end + 3;
                continue;
            }
            int close = html.indexOf('>', i + 1);
            if (close < 0) {
                break;
            }
            String tagContent = html.substring(i + 1, close);
            boolean isClosing = tagContent.startsWith("/");
            String name = tagName(isClosing ? tagContent.substring(1) : tagContent);
            if (skipUntil != null) {
                if (isClosing && name.equals(skipUntil)) {
                    skipUntil = null;
                }
            } else if (!isClosing && (name.equals("script") || name.equals("style"))) {
                skipUntil = name;
            } else if (isBlockTag(name)) {
                if (pendingBreaks < 2) {
                    pendingBreaks = 2;
                }
            } else if (name.equals("li")) {
                if (!isClosing) {
                    if (pendingBreaks < 1) {
                        pendingBreaks = 1;
                    }
                    pendingPrefix = "- ";
                }
            } else if (name.equals("br")) {
                if (pendingBreaks < 1) {
                    pendingBreaks = 1;
                }
            } else {
                out.append(' ');
            }
            i = close + 1;
        }
        return decodeEntities(out.toString());
    }

    private static boolean isBlockTag(String name) {
        return name.equals("p") || name.equals("div") || name.equals("tr")
                || name.equals("table") || name.equals("ul") || name.equals("ol")
                || name.equals("blockquote") || name.equals("hr")
                || name.equals("h1") || name.equals("h2") || name.equals("h3")
                || name.equals("h4") || name.equals("h5") || name.equals("h6");
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    private static String tagName(String tagContent) {
        int i = 0;
        int len = tagContent.length();
        while (i < len) {
            char c = tagContent.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '/' || c == '>') {
                break;
            }
            i++;
        }
        return tagContent.substring(0, i).toLowerCase();
    }

    private static String decodeEntities(String text) {
        if (text.indexOf('&') < 0) {
            return text;
        }
        StringBuffer out = new StringBuffer();
        int len = text.length();
        int i = 0;
        while (i < len) {
            char c = text.charAt(i);
            if (c != '&') {
                out.append(c);
                i++;
                continue;
            }
            int semi = text.indexOf(';', i + 1);
            String replacement = (semi < 0 || semi - i > 10) ? null : decodeEntity(text.substring(i + 1, semi));
            if (replacement == null) {
                out.append(c);
                i++;
            } else {
                out.append(replacement);
                i = semi + 1;
            }
        }
        return out.toString();
    }

    private static final Hashtable NAMED_ENTITIES = buildNamedEntities();

    private static Hashtable buildNamedEntities() {
        Hashtable table = new Hashtable();
        table.put("amp", "&");
        table.put("lt", "<");
        table.put("gt", ">");
        table.put("quot", "\"");
        table.put("apos", "'");
        table.put("nbsp", " ");
        table.put("copy", String.valueOf((char) 0x00A9));
        table.put("reg", String.valueOf((char) 0x00AE));
        table.put("trade", String.valueOf((char) 0x2122));
        table.put("mdash", String.valueOf((char) 0x2014));
        table.put("ndash", String.valueOf((char) 0x2013));
        table.put("hellip", String.valueOf((char) 0x2026));
        table.put("lsquo", String.valueOf((char) 0x2018));
        table.put("rsquo", String.valueOf((char) 0x2019));
        table.put("ldquo", String.valueOf((char) 0x201C));
        table.put("rdquo", String.valueOf((char) 0x201D));
        table.put("middot", String.valueOf((char) 0x00B7));
        table.put("bull", String.valueOf((char) 0x2022));
        table.put("deg", String.valueOf((char) 0x00B0));
        table.put("euro", String.valueOf((char) 0x20AC));
        table.put("pound", String.valueOf((char) 0x00A3));
        table.put("yen", String.valueOf((char) 0x00A5));
        table.put("cent", String.valueOf((char) 0x00A2));
        table.put("times", String.valueOf((char) 0x00D7));
        table.put("divide", String.valueOf((char) 0x00F7));
        table.put("shy", "");
        return table;
    }

    private static String decodeEntity(String entity) {
        String named = (String) NAMED_ENTITIES.get(entity);
        if (named != null) {
            return named;
        }
        if (entity.length() > 1 && entity.charAt(0) == '#') {
            try {
                int codepoint;
                if (entity.length() > 2 && (entity.charAt(1) == 'x' || entity.charAt(1) == 'X')) {
                    codepoint = Integer.parseInt(entity.substring(2), 16);
                } else {
                    codepoint = Integer.parseInt(entity.substring(1));
                }
                if (codepoint > 0 && codepoint < 0x10000) {
                    return String.valueOf((char) codepoint);
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
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
        } else if (command == goToCommand) {
            openAddressBar();
        } else if (command == goCommand) {
            String url = normalizeUrl(((TextBox) displayable).getString());
            display.setCurrent(canvas);
            if (url != null) {
                pushBack(currentUrl);
                clearForward();
                startFetch(url);
            }
        } else if (command == cancelCommand) {
            display.setCurrent(canvas);
        } else if (command == backCommand) {
            if (!backStack.isEmpty()) {
                String previous = popBack();
                pushForward(currentUrl);
                startFetch(previous);
            }
        } else if (command == forwardCommand) {
            if (!forwardStack.isEmpty()) {
                String next = popForward();
                pushBack(currentUrl);
                startFetch(next);
            }
        }
    }

    private void pushBack(String url) {
        boolean wasEmpty = backStack.isEmpty();
        backStack.addElement(url);
        if (wasEmpty) {
            canvas.addCommand(backCommand);
        }
    }

    private String popBack() {
        String url = (String) backStack.lastElement();
        backStack.removeElementAt(backStack.size() - 1);
        if (backStack.isEmpty()) {
            canvas.removeCommand(backCommand);
        }
        return url;
    }

    private void pushForward(String url) {
        boolean wasEmpty = forwardStack.isEmpty();
        forwardStack.addElement(url);
        if (wasEmpty) {
            canvas.addCommand(forwardCommand);
        }
    }

    private String popForward() {
        String url = (String) forwardStack.lastElement();
        forwardStack.removeElementAt(forwardStack.size() - 1);
        if (forwardStack.isEmpty()) {
            canvas.removeCommand(forwardCommand);
        }
        return url;
    }

    private void clearForward() {
        if (!forwardStack.isEmpty()) {
            forwardStack.removeAllElements();
            canvas.removeCommand(forwardCommand);
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

        void clearContent() {
            this.lines = new Vector();
            this.scrollOffset = 0;
            this.hasContent = false;
        }

        private static boolean isSpace(char c) {
            return c == ' ' || c == '\t' || c == '\r' || c == '\n';
        }

        // CLDC 1.1 has no java.util.StringTokenizer, so words are split by hand.
        // '\n' is a forced line break (from stripTags' block-tag handling), not
        // just a word separator like the other whitespace classes -- callers of
        // stripTags have already normalized incidental source whitespace to plain
        // spaces, so any '\n' reaching here is intentional.
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
                if (currentWord.length() > 0) {
                    currentLine = addWord(result, currentLine, currentWord.toString(), maxWidth, font);
                    currentWord = new StringBuffer();
                }
                if (c == '\n') {
                    result.addElement(currentLine.toString());
                    currentLine = new StringBuffer();
                }
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
