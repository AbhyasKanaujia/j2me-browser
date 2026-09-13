// Centralized color configuration for BrowserCanvas's rendering. Currently
// just fixed constants -- a future roadmap item is a user-configurable
// theme (see README), which would source these from settings instead of
// hardcoding them here, without BrowserCanvas's rendering logic needing to
// change at all. See docs/adr/0006-one-file-per-concern.md.
final class Theme {
    private Theme() {
    }

    static final int TEXT_COLOR = 0x000000;
    static final int BACKGROUND_COLOR = 0xFFFFFF;
    static final int LINK_COLOR = 0x0000CC;
    static final int LINK_HIGHLIGHT_BACKGROUND = 0xCCE5FF;
}
