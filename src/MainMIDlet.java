import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.TextField;
import javax.microedition.midlet.MIDlet;
import java.util.Vector;

// MIDlet lifecycle, command wiring, and navigation (back/forward) stack.
// Orchestration only -- delegates fetching to Http, HTML-to-text conversion
// to HtmlText, and rendering/scrolling to BrowserCanvas. See
// docs/adr/0006-one-file-per-concern.md for why this file stays this small.
public class MainMIDlet extends MIDlet implements CommandListener {
    private static final String FETCH_URL = "http://info.cern.ch/";

    private final Display display;
    private final BrowserCanvas canvas;
    private final Command exitCommand;
    private final Command goToCommand;
    private final Command goCommand;
    private final Command cancelCommand;
    private final Command backCommand;
    private final Command forwardCommand;
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
        Http.Result result = Http.fetch(startUrl);
        currentUrl = result.finalUrl;
        canvas.setStatus(result.status);
        if (result.body != null) {
            String pageText;
            try {
                pageText = new String(result.body, "UTF-8");
            } catch (Exception e) {
                pageText = new String(result.body);
            }
            canvas.setPageText(HtmlText.stripTags(pageText));
        } else {
            canvas.clearContent();
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
}
