import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Canvas;
import javax.microedition.midlet.MIDlet;

public class MainMIDlet extends MIDlet implements CommandListener {
    private final Display display;
    private final HelloCanvas canvas;
    private final Command exitCommand;

    public MainMIDlet() {
        display = Display.getDisplay(this);
        canvas = new HelloCanvas();
        exitCommand = new Command("Exit", Command.EXIT, 1);
        canvas.addCommand(exitCommand);
        canvas.setCommandListener(this);
    }

    public void startApp() {
        display.setCurrent(canvas);
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

    private static final class HelloCanvas extends Canvas {
        private final Font titleFont;
        private final Font bodyFont;

        HelloCanvas() {
            titleFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_BOLD, Font.SIZE_LARGE);
            bodyFont = Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        }

        protected void paint(Graphics graphics) {
            int width = getWidth();
            int height = getHeight();

            graphics.setColor(0xFFFFFF);
            graphics.fillRect(0, 0, width, height);

            graphics.setColor(0x000000);
            graphics.setFont(titleFont);
            graphics.drawString("Hello, World!", width / 2, height / 2 - titleFont.getHeight(),
                    Graphics.HCENTER | Graphics.BASELINE);

            graphics.setFont(bodyFont);
            graphics.drawString("J2ME Browser", width / 2, height / 2 + 4,
                    Graphics.HCENTER | Graphics.TOP);
            graphics.drawString("for MIDP 2.0 / CLDC 1.1 phones", width / 2, height / 2 + 4 + bodyFont.getHeight(),
                    Graphics.HCENTER | Graphics.TOP);
        }
    }
}
