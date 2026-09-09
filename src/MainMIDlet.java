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
import java.io.InputStream;

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
        try {
            connection = (HttpConnection) Connector.open(FETCH_URL);
            int responseCode = connection.getResponseCode();
            in = connection.openInputStream();
            int total = 0;
            byte[] buffer = new byte[256];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
            }
            status = "HTTP " + responseCode + " - " + total + " bytes from " + FETCH_URL;
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
        private final Font titleFont;
        private final Font bodyFont;
        private String status = "Connecting...";

        BrowserCanvas() {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_LARGE);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }

        void setStatus(String status) {
            this.status = status;
        }

        protected void paint(Graphics graphics) {
            int width = getWidth();
            int height = getHeight();

            graphics.setColor(0xFFFFFF);
            graphics.fillRect(0, 0, width, height);

            graphics.setColor(0x000000);
            graphics.setFont(titleFont);
            graphics.drawString("J2ME Browser", width / 2, height / 2 - titleFont.getHeight(),
                    Graphics.HCENTER | Graphics.BASELINE);

            graphics.setFont(bodyFont);
            graphics.drawString(status, width / 2, height / 2 + 4,
                    Graphics.HCENTER | Graphics.TOP);
        }
    }
}
