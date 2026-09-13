import java.util.Hashtable;

// Parsing layer, current stage: tag-soup HTML -> readable plain text. No
// block/paragraph structure preserved beyond forced line breaks -- real
// layout is a later stage. See docs/adr/0005-staged-text-flow-rendering.md
// and docs/adr/0006-one-file-per-concern.md.
final class HtmlText {
    private HtmlText() {
    }

    static final class Result {
        final String text;
        final String title;

        Result(String text, String title) {
            this.text = text;
            this.title = title;
        }
    }

    // Strips tags down to plain text: no block/paragraph structure preserved yet
    // (that's layout, a later stage), just tag-soup -> readable text. <script> and
    // <style> element content is dropped entirely rather than shown as text.
    // <title> content is also excluded from the body -- it's captured separately
    // as Result.title instead (empty string if the page has none).
    //
    // Block-level tags queue a forced line break (rendered by BrowserCanvas.wrap,
    // which treats '\n' specially) instead of the plain space used for inline
    // tags, so paragraphs/headings/list items land on their own line rather than
    // running together. <li> also queues a "- " prefix -- ordered and unordered
    // lists both just get a dash, no item numbering yet.
    static Result parse(String html) {
        StringBuffer out = new StringBuffer();
        StringBuffer title = new StringBuffer();
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
        return new Result(decodeEntities(out.toString()), collapseWhitespace(decodeEntities(title.toString())));
    }

    // Collapses runs of whitespace (common in hand-formatted <title>...</title>
    // source) to single spaces and trims the ends.
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
