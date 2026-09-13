import java.util.Hashtable;
import java.util.Vector;

// Parsing layer, current stage: tag-soup HTML -> styled text spans plus
// forced-line-break/list-prefix markers. Still no real block-box layout --
// see docs/adr/0005-staged-text-flow-rendering.md and
// docs/adr/0006-one-file-per-concern.md.
final class HtmlText {
    private HtmlText() {
    }

    static final int SIZE_NORMAL = 0;
    static final int SIZE_MEDIUM = 1;
    static final int SIZE_LARGE = 2;

    // A run of text with a single, uniform style -- BrowserCanvas resolves
    // (bold, italic, sizeTier) to an actual Font; this class stays free of
    // any javax.microedition.lcdui dependency, per ADR-0006's layering.
    static final class Span {
        final String text;
        final boolean bold;
        final boolean italic;
        final int sizeTier;

        Span(String text, boolean bold, boolean italic, int sizeTier) {
            this.text = text;
            this.bold = bold;
            this.italic = italic;
            this.sizeTier = sizeTier;
        }
    }

    static final class Result {
        final Vector spans;
        final String title;

        Result(Vector spans, String title) {
            this.spans = spans;
            this.title = title;
        }
    }

    private static final class StyleFrame {
        final String tagName;
        final boolean bold;
        final boolean italic;
        final int sizeTier;

        StyleFrame(String tagName, boolean bold, boolean italic, int sizeTier) {
            this.tagName = tagName;
            this.bold = bold;
            this.italic = italic;
            this.sizeTier = sizeTier;
        }
    }

    // Converts tag-soup HTML into a sequence of styled Spans plus forced line
    // breaks/list prefixes, tracked the same way as before (embedded '\n' and
    // "- " directly in span text, interpreted downstream by BrowserCanvas).
    // <script>/<style>/<title> content is excluded entirely from the spans;
    // <title> is captured separately as Result.title.
    //
    // <b>/<strong>, <i>/<em>, and <h1>-<h6> (bold + a larger size tier) each
    // start a new span when they open or close, tracked with a small
    // open-tag stack so nesting (e.g. bold containing italic) restores the
    // right state on close. A style tag closing mid-word (no surrounding
    // whitespace, e.g. "<b>Wor</b>ld") is treated as two separate words --
    // real-world markup essentially never splits a word across a style
    // boundary, so this is an accepted simplification rather than tracked
    // precisely.
    static Result parse(String html) {
        Vector spans = new Vector();
        StringBuffer currentText = new StringBuffer();
        StringBuffer title = new StringBuffer();
        boolean bold = false;
        boolean italic = false;
        int sizeTier = SIZE_NORMAL;
        Vector styleStack = new Vector();
        boolean anyOutput = false;

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
                        if (pendingBreaks == 0 && anyOutput) {
                            currentText.append(' ');
                        }
                    } else {
                        while (pendingBreaks > 0) {
                            currentText.append('\n');
                            pendingBreaks--;
                        }
                        if (pendingPrefix != null) {
                            currentText.append(pendingPrefix);
                            pendingPrefix = null;
                        }
                        currentText.append(c);
                        anyOutput = true;
                    }
                } else if (skipUntil.equals("title")) {
                    title.append(c);
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
            } else if (!isClosing && (name.equals("script") || name.equals("style") || name.equals("title"))) {
                skipUntil = name;
            } else {
                if (isBlockTag(name)) {
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
                }

                if (isStyleTag(name)) {
                    if (currentText.length() > 0) {
                        spans.addElement(new Span(decodeEntities(currentText.toString()), bold, italic, sizeTier));
                        currentText.setLength(0);
                    }
                    if (isClosing) {
                        int frameIndex = findFrame(styleStack, name);
                        if (frameIndex >= 0) {
                            StyleFrame frame = (StyleFrame) styleStack.elementAt(frameIndex);
                            while (styleStack.size() > frameIndex) {
                                styleStack.removeElementAt(styleStack.size() - 1);
                            }
                            bold = frame.bold;
                            italic = frame.italic;
                            sizeTier = frame.sizeTier;
                        }
                    } else {
                        styleStack.addElement(new StyleFrame(name, bold, italic, sizeTier));
                        if (isBoldTag(name)) {
                            bold = true;
                        }
                        if (isItalicTag(name)) {
                            italic = true;
                        }
                        int heading = headingSizeTier(name);
                        if (heading >= 0) {
                            bold = true;
                            sizeTier = heading;
                        }
                    }
                } else if (!isBlockTag(name) && !name.equals("li") && !name.equals("br")) {
                    if (pendingBreaks == 0 && anyOutput) {
                        currentText.append(' ');
                    }
                }
            }
            i = close + 1;
        }
        if (currentText.length() > 0) {
            spans.addElement(new Span(decodeEntities(currentText.toString()), bold, italic, sizeTier));
        }
        return new Result(spans, collapseWhitespace(decodeEntities(title.toString())));
    }

    private static int findFrame(Vector styleStack, String name) {
        for (int i = styleStack.size() - 1; i >= 0; i--) {
            if (((StyleFrame) styleStack.elementAt(i)).tagName.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isBoldTag(String name) {
        return name.equals("b") || name.equals("strong");
    }

    private static boolean isItalicTag(String name) {
        return name.equals("i") || name.equals("em");
    }

    private static int headingSizeTier(String name) {
        if (name.equals("h1") || name.equals("h2")) {
            return SIZE_LARGE;
        }
        if (name.equals("h3") || name.equals("h4") || name.equals("h5") || name.equals("h6")) {
            return SIZE_MEDIUM;
        }
        return -1;
    }

    private static boolean isStyleTag(String name) {
        return isBoldTag(name) || isItalicTag(name) || headingSizeTier(name) >= 0;
    }

    // Collapses runs of whitespace (common in hand-formatted <title>...</title>
    // source) to single spaces and trims the ends. decodeEntities() runs
    // first since entities can decode to whitespace (e.g. &nbsp;).
    private static String collapseWhitespace(String text) {
        StringBuffer out = new StringBuffer();
        boolean lastWasSpace = true;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (isSpace(c)) {
                if (!lastWasSpace) {
                    out.append(' ');
                }
                lastWasSpace = true;
            } else {
                out.append(c);
                lastWasSpace = false;
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
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
}
