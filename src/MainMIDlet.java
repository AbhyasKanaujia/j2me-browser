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
import java.util.Vector;

public class MainMIDlet extends MIDlet implements CommandListener {
    private static final String FETCH_URL = "http://info.cern.ch/";

    private final Display display;
    private final BrowserCanvas canvas;
    private final Command exitCommand;
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
        HttpConnection connection = null;
        InputStream in = null;
        String status;
        byte[] responseBytes = null;
        try {
            connection = (HttpConnection) Connector.open(FETCH_URL);
            int responseCode = connection.getResponseCode();
            in = connection.openInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[256];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            responseBytes = buffer.toByteArray();
            status = "HTTP " + responseCode + " - " + responseBytes.length + " bytes from " + FETCH_URL;
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
