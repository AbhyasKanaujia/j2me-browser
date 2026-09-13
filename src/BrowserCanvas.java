import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import java.util.Vector;

// Rendering/UI layer: word-wrap, paint, scrolling, and link navigation. See
// docs/adr/0006-one-file-per-concern.md.
//
// Link navigation follows the classic S40-browser model: one link at a time
// has focus (highlighted), UP/DOWN moves focus to the next/previous link if
// it's already visible on screen, and only falls back to scrolling the
// viewport when there's no more focusable link in that direction within the
// current view -- link-jump is priority 1, scroll is priority 2. FIRE
// activates the focused link. Touch-only devices (no D-pad, no fire key) get
// tap-to-activate instead, distinguished from drag-to-scroll by how little
// the touch moved between press and release.
final class BrowserCanvas extends Canvas {
    private final Font bodyFont;
    private String statusLine = "Connecting...";
    private Vector lines = new Vector(); // Vector<Vector<Fragment>>: one inner Vector per visual line
    private Vector linkOccurrences = new Vector(); // Vector<LinkOccurrence>, in document order
    private int focusedLink = -1; // index into linkOccurrences, -1 = none
    private boolean hasContent;
    private int scrollOffset;
    private LinkListener linkListener;

    BrowserCanvas() {
        bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    }

    interface LinkListener {
        void onActivateLink(String href);
    }

    void setLinkListener(LinkListener listener) {
        this.linkListener = listener;
    }

    void setStatus(String status) {
        this.statusLine = status;
    }

    void setPageText(Vector spans) {
        this.lines = wrapSpans(spans, getWidth() - 4);
        this.linkOccurrences = buildLinkOccurrences(this.lines);
        this.focusedLink = linkOccurrences.isEmpty() ? -1 : 0;
        this.scrollOffset = 0;
        this.hasContent = true;
    }

    void clearContent() {
        this.lines = new Vector();
        this.linkOccurrences = new Vector();
        this.focusedLink = -1;
        this.scrollOffset = 0;
        this.hasContent = false;
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    // One piece of a visual line: a run of text drawn in a single Font.
    // screenX/screenY/screenWidth/screenHeight are cached during paint() so
    // pointerReleased can hit-test a tap against them; -1 until first painted.
    private static final class Fragment {
        final String text;
        final Font font;
        final String href;
        int linkIndex = -1; // set by buildLinkOccurrences; -1 = not part of a link
        int screenX = -1;
        int screenY = -1;
        int screenWidth;
        int screenHeight;

        Fragment(String text, Font font, String href) {
            this.text = text;
            this.font = font;
            this.href = href;
        }
    }

    // A run of consecutive same-href fragments -- one occurrence of a link on
    // the page, in document order. firstLine/lastLine (visual line indices,
    // after wrapping) are enough to decide whether it's currently visible.
    private static final class LinkOccurrence {
        final String href;
        int firstLine;
        int lastLine;

        LinkOccurrence(String href, int firstLine) {
            this.href = href;
            this.firstLine = firstLine;
            this.lastLine = firstLine;
        }
    }

    private static Vector buildLinkOccurrences(Vector lines) {
        Vector occurrences = new Vector();
        LinkOccurrence current = null;
        String lastHref = null;
        for (int l = 0; l < lines.size(); l++) {
            Vector line = (Vector) lines.elementAt(l);
            for (int f = 0; f < line.size(); f++) {
                Fragment fragment = (Fragment) line.elementAt(f);
                if (fragment.href == null) {
                    current = null;
                    lastHref = null;
                    continue;
                }
                if (current == null || !fragment.href.equals(lastHref)) {
                    current = new LinkOccurrence(fragment.href, l);
                    occurrences.addElement(current);
                }
                current.lastLine = l;
                fragment.linkIndex = occurrences.size() - 1;
                lastHref = fragment.href;
            }
        }
        return occurrences;
    }

    private static Font resolveFont(boolean bold, boolean italic, boolean underline, int sizeTier) {
        int style = Font.STYLE_PLAIN;
        if (bold) {
            style |= Font.STYLE_BOLD;
        }
        if (italic) {
            style |= Font.STYLE_ITALIC;
        }
        if (underline) {
            style |= Font.STYLE_UNDERLINED;
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
            Font font = resolveFont(span.bold, span.italic, span.href != null, span.sizeTier);
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
                    currentLine = addWord(result, currentLine, currentWidth, currentWord.toString(), font, span.href, maxWidth);
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

    // Appends word (in the given font/href) to currentLine, flushing to result
    // as lines fill up. A single word wider than maxWidth on its own (e.g. a
    // long URL) is hard-broken by character, since the display has no
    // horizontal scroll to fall back on.
    private static Vector addWord(Vector result, Vector currentLine, int[] currentWidth, String word, Font font, String href, int maxWidth) {
        int wordWidth = font.stringWidth(word);
        if (!currentLine.isEmpty()) {
            int spaceWidth = font.stringWidth(" ");
            if (currentWidth[0] + spaceWidth + wordWidth <= maxWidth) {
                currentLine.addElement(new Fragment(" " + word, font, href));
                currentWidth[0] += spaceWidth + wordWidth;
                return currentLine;
            }
            result.addElement(currentLine);
            currentLine = new Vector();
            currentWidth[0] = 0;
        }
        if (wordWidth <= maxWidth) {
            currentLine.addElement(new Fragment(word, font, href));
            currentWidth[0] = wordWidth;
            return currentLine;
        }
        StringBuffer chunk = new StringBuffer();
        int chunkWidth = 0;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            int cWidth = font.stringWidth(String.valueOf(c));
            if (chunk.length() > 0 && chunkWidth + cWidth > maxWidth) {
                currentLine.addElement(new Fragment(chunk.toString(), font, href));
                result.addElement(currentLine);
                currentLine = new Vector();
                chunk = new StringBuffer();
                chunkWidth = 0;
            }
            chunk.append(c);
            chunkWidth += cWidth;
        }
        if (chunk.length() > 0) {
            currentLine.addElement(new Fragment(chunk.toString(), font, href));
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

    // Y position where page content starts, below the (possibly multi-line)
    // status line. Shared by paint() and the link-navigation scroll math so
    // they always agree on how much vertical space the status line takes.
    private int contentTopY() {
        Vector statusLines = wrapPlain(statusLine, getWidth() - 4, bodyFont);
        return 2 + statusLines.size() * bodyFont.getHeight() + 2;
    }

    private int visibleLineCount() {
        int visible = (getHeight() - contentTopY()) / bodyFont.getHeight();
        return (visible < 1) ? 1 : visible;
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
            moveFocus(-1);
        } else if (action == DOWN) {
            moveFocus(1);
        } else if (action == FIRE) {
            activateFocusedLink();
        }
    }

    // Link-jump is priority 1, scroll is priority 2: if the next/previous
    // link is already visible, focus jumps to it with no scroll at all. If
    // it's not visible yet, this never teleports the viewport there --
    // that's disorienting, you lose what you were reading. Instead it
    // scrolls exactly one line (identical to plain scrolling, including
    // under keyRepeated for a held key), then re-checks visibility; focus
    // only lands on the link once it has naturally scrolled into view on
    // its own, so approaching a distant link feels like normal reading,
    // not a jump-cut. Only once there's no more link in that direction at
    // all does this fall back to plain line-scroll unconditionally, same
    // as before link navigation existed, so the rest of the page stays
    // readable past the first/last link.
    private void moveFocus(int direction) {
        if (linkOccurrences.isEmpty()) {
            scrollBy(direction);
            return;
        }
        int next = focusedLink + direction;
        if (next < 0 || next >= linkOccurrences.size()) {
            scrollBy(direction);
            return;
        }
        LinkOccurrence occurrence = (LinkOccurrence) linkOccurrences.elementAt(next);
        if (!isVisible(occurrence)) {
            scrollOffset += direction;
            if (scrollOffset < 0) {
                scrollOffset = 0;
            }
        }
        if (isVisible(occurrence)) {
            focusedLink = next;
        }
        repaint();
    }

    private boolean isVisible(LinkOccurrence occurrence) {
        int visibleLines = visibleLineCount();
        return occurrence.firstLine >= scrollOffset && occurrence.lastLine < scrollOffset + visibleLines;
    }

    private void scrollBy(int direction) {
        scrollOffset += direction;
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
        repaint();
    }

    private void activateFocusedLink() {
        if (focusedLink < 0 || focusedLink >= linkOccurrences.size() || linkListener == null) {
            return;
        }
        linkListener.onActivateLink(((LinkOccurrence) linkOccurrences.elementAt(focusedLink)).href);
    }

    // Touch-only devices (e.g. the Asha full-touch UI) have no D-pad and no
    // fire key, so link activation there is a tap (as opposed to a drag,
    // which scrolls) directly on the link's rendered text.
    private static final int TAP_THRESHOLD = 10;
    private int pointerStartX;
    private int pointerStartY;
    private int scrollAtPointerStart;

    protected void pointerPressed(int x, int y) {
        pointerStartX = x;
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

    protected void pointerReleased(int x, int y) {
        int movedX = x - pointerStartX;
        int movedY = y - pointerStartY;
        if (movedX < 0) {
            movedX = -movedX;
        }
        if (movedY < 0) {
            movedY = -movedY;
        }
        if (movedX > TAP_THRESHOLD || movedY > TAP_THRESHOLD) {
            return; // was a drag/scroll, not a tap
        }
        Fragment hit = fragmentAt(x, y);
        if (hit != null && hit.linkIndex >= 0 && linkListener != null) {
            focusedLink = hit.linkIndex;
            repaint();
            linkListener.onActivateLink(((LinkOccurrence) linkOccurrences.elementAt(hit.linkIndex)).href);
        }
    }

    private Fragment fragmentAt(int x, int y) {
        for (int l = 0; l < lines.size(); l++) {
            Vector line = (Vector) lines.elementAt(l);
            for (int f = 0; f < line.size(); f++) {
                Fragment fragment = (Fragment) line.elementAt(f);
                if (fragment.screenX < 0) {
                    continue; // not painted (offscreen or never rendered)
                }
                if (x >= fragment.screenX && x < fragment.screenX + fragment.screenWidth
                        && y >= fragment.screenY && y < fragment.screenY + fragment.screenHeight) {
                    return fragment;
                }
            }
        }
        return null;
    }

    protected void paint(Graphics graphics) {
        int width = getWidth();
        int height = getHeight();

        graphics.setColor(Theme.BACKGROUND_COLOR);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Theme.TEXT_COLOR);
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
            // Two passes: a link's fragments (e.g. "relative" + " link" as
            // separate word-fragments) are always contiguous within a line
            // (nothing else can be grouped between same-occurrence fragments,
            // see buildLinkOccurrences), so the highlight is one seamless box
            // spanning first-fragment-start to last-fragment-end, drawn before
            // the text rather than one box per fragment with gaps between.
            int x = 2;
            int highlightStartX = -1;
            int highlightEndX = -1;
            for (int f = 0; f < line.size(); f++) {
                Fragment fragment = (Fragment) line.elementAt(f);
                graphics.setFont(fragment.font);
                int textWidth = fragment.font.stringWidth(fragment.text);
                fragment.screenX = x;
                fragment.screenY = y;
                fragment.screenWidth = textWidth;
                fragment.screenHeight = h;
                if (fragment.linkIndex >= 0 && fragment.linkIndex == focusedLink) {
                    if (highlightStartX < 0) {
                        highlightStartX = x;
                    }
                    highlightEndX = x + textWidth;
                }
                x += textWidth;
            }
            if (highlightStartX >= 0) {
                graphics.setColor(Theme.LINK_HIGHLIGHT_BACKGROUND);
                graphics.fillRect(highlightStartX, y, highlightEndX - highlightStartX, h);
                graphics.setColor(Theme.LINK_COLOR);
                graphics.drawRect(highlightStartX, y, highlightEndX - highlightStartX - 1, h - 1);
            }
            for (int f = 0; f < line.size(); f++) {
                Fragment fragment = (Fragment) line.elementAt(f);
                graphics.setFont(fragment.font);
                graphics.setColor((fragment.href != null) ? Theme.LINK_COLOR : Theme.TEXT_COLOR);
                graphics.drawString(fragment.text, fragment.screenX, fragment.screenY, Graphics.LEFT | Graphics.TOP);
            }
            y += h;
            index++;
        }
    }
}
