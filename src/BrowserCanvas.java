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
    private Vector lines = new Vector(); // Vector<Vector<Fragment>>: one inner Vector per visual line
    private boolean hasContent;
    private int scrollOffset;

    BrowserCanvas() {
        bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    void setStatus(String status) {
        this.statusLine = status;
    }

    void setPageText(Vector spans) {
        this.lines = wrapSpans(spans, getWidth() - 4);
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

    // One piece of a visual line: a run of text drawn in a single Font.
    private static final class Fragment {
        final String text;
        final Font font;

        Fragment(String text, Font font) {
            this.text = text;
            this.font = font;
        }
    }

    private static Font resolveFont(boolean bold, boolean italic, int sizeTier) {
        int style = Font.STYLE_PLAIN;
        if (bold) {
            style |= Font.STYLE_BOLD;
        }
        if (italic) {
            style |= Font.STYLE_ITALIC;
        }
        int size;
        if (sizeTier == HtmlText.SIZE_LARGE) {
            size = Font.SIZE_LARGE;
        } else if (sizeTier == HtmlText.SIZE_MEDIUM) {
            size = Font.SIZE_MEDIUM;
        } else {
            size = Font.SIZE_SMALL;
        }
        return Font.getFont(Font.FACE_PROPORTIONAL, style, size);
    }

    // Tallest fragment in a line, used both to advance the draw cursor and to
    // approximate paging math below (see wrapSpans/paint for why scroll-limit
    // clamping only approximates this rather than tracking it exactly).
    private static int lineHeight(Vector line, int fallback) {
        int max = fallback;
        for (int i = 0; i < line.size(); i++) {
            int h = ((Fragment) line.elementAt(i)).font.getHeight();
            if (h > max) {
                max = h;
            }
        }
        return max;
    }

    // CLDC 1.1 has no java.util.StringTokenizer, so words are split by hand.
    // '\n' is a forced line break (from HtmlText.parse's block-tag handling),
    // not just a word separator like the other whitespace classes -- HtmlText
    // has already normalized incidental source whitespace to plain spaces, so
    // any '\n' reaching here is intentional.
    private static Vector wrapSpans(Vector spans, int maxWidth) {
        Vector result = new Vector();
        if (maxWidth <= 0) {
            return result;
        }
        Vector currentLine = new Vector();
        int[] currentWidth = {0};
        for (int s = 0; s < spans.size(); s++) {
            HtmlText.Span span = (HtmlText.Span) spans.elementAt(s);
            Font font = resolveFont(span.bold, span.italic, span.sizeTier);
            String text = span.text;
            StringBuffer currentWord = new StringBuffer();
            int len = text.length();
            for (int i = 0; i <= len; i++) {
                char c = (i < len) ? text.charAt(i) : ' ';
                if (!isSpace(c)) {
                    currentWord.append(c);
                    continue;
                }
                if (currentWord.length() > 0) {
                    currentLine = addWord(result, currentLine, currentWidth, currentWord.toString(), font, maxWidth);
                    currentWord = new StringBuffer();
                }
                if (c == '\n') {
                    result.addElement(currentLine);
                    currentLine = new Vector();
                    currentWidth[0] = 0;
                }
            }
        }
        if (!currentLine.isEmpty()) {
            result.addElement(currentLine);
        }
        return result;
    }

    // Appends word (in the given font) to currentLine, flushing to result as
    // lines fill up. A single word wider than maxWidth on its own (e.g. a long
    // URL) is hard-broken by character, since the display has no horizontal
    // scroll to fall back on.
    private static Vector addWord(Vector result, Vector currentLine, int[] currentWidth, String word, Font font, int maxWidth) {
        int wordWidth = font.stringWidth(word);
        if (!currentLine.isEmpty()) {
            int spaceWidth = font.stringWidth(" ");
            if (currentWidth[0] + spaceWidth + wordWidth <= maxWidth) {
                currentLine.addElement(new Fragment(" " + word, font));
                currentWidth[0] += spaceWidth + wordWidth;
                return currentLine;
            }
            result.addElement(currentLine);
            currentLine = new Vector();
            currentWidth[0] = 0;
        }
        if (wordWidth <= maxWidth) {
            currentLine.addElement(new Fragment(word, font));
            currentWidth[0] = wordWidth;
            return currentLine;
        }
        StringBuffer chunk = new StringBuffer();
        int chunkWidth = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            int cWidth = font.stringWidth(String.valueOf(c));
            if (chunk.length() > 0 && chunkWidth + cWidth > maxWidth) {
                currentLine.addElement(new Fragment(chunk.toString(), font));
                result.addElement(currentLine);
                currentLine = new Vector();
                chunk = new StringBuffer();
                chunkWidth = 0;
            }
            chunk.append(c);
            chunkWidth += cWidth;
        }
        if (chunk.length() > 0) {
            currentLine.addElement(new Fragment(chunk.toString(), font));
            currentWidth[0] = chunkWidth;
        }
        return currentLine;
    }

    // Plain single-font wrap, used only for the status line (never styled).
    // A single word wider than maxWidth on its own (e.g. a long URL in an
    // error message) is hard-broken by character, same as addWord above.
    private static Vector wrapPlain(String text, int maxWidth, Font font) {
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
                currentLine = addPlainWord(result, currentLine, currentWord.toString(), maxWidth, font);
                currentWord = new StringBuffer();
            }
        }
        if (currentLine.length() > 0) {
            result.addElement(currentLine.toString());
        }
        return result;
    }

    private static StringBuffer addPlainWord(Vector result, StringBuffer currentLine, String word, int maxWidth, Font font) {
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
        Vector statusLines = wrapPlain(statusLine, width - 4, bodyFont);
        for (int i = 0; i < statusLines.size(); i++) {
            graphics.drawString((String) statusLines.elementAt(i), 2, y, Graphics.LEFT | Graphics.TOP);
            y += bodyFont.getHeight();
        }
        y += 2;

        if (!hasContent) {
            return;
        }

        // visibleLines/maxOffset use a fixed lineHeight as an approximation for
        // scroll-limit clamping -- exact accounting would need to know which
        // lines are visible to know their heights, which depends on where you
        // scroll to. The actual draw loop below is exact regardless (it stops
        // a line early rather than clipping it), so this approximation can only
        // affect how far you can scroll past the last line, not what's drawn.
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

        int index = scrollOffset;
        while (index < lines.size()) {
            Vector line = (Vector) lines.elementAt(index);
            int h = lineHeight(line, lineHeight);
            if (y + h > height) {
                break;
            }
            int x = 2;
            for (int f = 0; f < line.size(); f++) {
                Fragment fragment = (Fragment) line.elementAt(f);
                graphics.setFont(fragment.font);
                graphics.drawString(fragment.text, x, y, Graphics.LEFT | Graphics.TOP);
                x += fragment.font.stringWidth(fragment.text);
            }
            y += h;
            index++;
        }
    }
}
