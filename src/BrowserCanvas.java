import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import java.util.Vector;

// Rendering/UI layer: word-wrap, paint, and scrolling (both D-pad key events
// and touch drag, for full-touch devices like the Asha UI that have no
// D-pad). See docs/adr/0006-one-file-per-concern.md.
final class BrowserCanvas extends Canvas {
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
    // '\n' is a forced line break (from HtmlText.stripTags' block-tag handling),
    // not just a word separator like the other whitespace classes -- callers of
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

    // Touch-only devices (e.g. the Asha full-touch UI) have no D-pad, so no
    // key event ever fires getGameAction() UP/DOWN. Custom low-level Canvas
    // subclasses don't get scrolling for free the way high-level List/Form
    // components do on those devices, so drag-to-scroll is hand-rolled here.
    private int pointerStartY;
    private int scrollAtPointerStart;

    protected void pointerPressed(int x, int y) {
        pointerStartY = y;
        scrollAtPointerStart = scrollOffset;
    }

    protected void pointerDragged(int x, int y) {
        int lineHeight = bodyFont.getHeight();
        if (lineHeight <= 0) {
            return;
        }
        int deltaLines = (pointerStartY - y) / lineHeight;
        int newOffset = scrollAtPointerStart + deltaLines;
        if (newOffset < 0) {
            newOffset = 0;
        }
        scrollOffset = newOffset;
        repaint();
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
