package core.entities;

import core.adapters.IWaifuAdapter;
import core.utils.Util;
import core.audio.AudioManager;
import core.settings.Settings;
import core.utils.WaifuUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Random;

import static java.awt.Image.SCALE_AREA_AVERAGING;

public class Secretary extends JFrame implements MouseListener, MouseMotionListener,
        MouseWheelListener, KeyListener, WindowListener {

    private static final String MAX_HEIGHT = "waifu.height";
    private static final String MIRRORED = "waifu.mirrored";
    private static final String WELCOME_ENABLED = "waifu.welcome.enabled";
    private static final String WELCOME_DELAY = "waifu.welcome.delay";
    private static final String VOICE_ENABLED = "voice.enabled";
    private static final String VOICE_VOLUME = "voice.volume";
    private static final String DIALOGS_ENABLED = "dialogs.enabled";
    private static final String DIALOGS_ON_CLICK_ENABLED = "dialogs.onClick";
    private static final String DIALOGS_ON_IDLE_ENABLED = "dialogs.onIdle";
    private static final String BALOON_DURATION_NO_VOICE = "dialogs.baloon.noVoiceDuration";
    private static final String ALWAYS_ON_TOP = "waifu.alwaysOnTop";
    private static final String BALOON_TEXT_FORMAT_HTML = "baloon.formatString";

    private final AudioManager audioManager = new AudioManager();

    private final IWaifuAdapter waifuInterface;

    private int xClickPosition;
    private int dragDiff = 0;

    private boolean running;
    private boolean alwaysOnTop = Settings.get(ALWAYS_ON_TOP, false);

    private int skinIndex = 0;

    private Baloon baloon;
    private SecretaryLabel secretaryLabel;

    private BufferedImage buffImage;

    private boolean isManual = false;
    private boolean isDragging = false;

    /**
     * Application start point
     *
     * @param waifu Your initialized waifu
     * @throws Exception Something went wrong while starting the ship
     */
    public Secretary(IWaifuAdapter waifu) throws Exception {

        waifuInterface = waifu;

        swingSetup();

        secretaryLabel.startFloating();

        if (Settings.get(DIALOGS_ON_IDLE_ENABLED, true)) {
            idle();
        }

        if (Settings.get(WELCOME_ENABLED, true)) {
            onLogin(); // Say Hi!
        }

    }

    @Override
    public void paint(Graphics g) {
        g.clearRect(0, 0, this.getWidth(), this.getHeight());
        super.paint(g);
        if (!isManual) {
            new Thread(() -> {
                BufferedImage image = getScreenShot(this);
                Area area = getOutline(image, 0);
                setShape(area);
            }).start();
        }
    }

    public BufferedImage getScreenShot(Component component) {
        BufferedImage image = new BufferedImage(
                component.getWidth(),
                component.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );
        setShape(null);
        isManual = true;
        component.printAll(image.getGraphics());
        isManual = false;

        // Debug screenshot
        // try {
        //     ImageIO.write(image, "PNG", Paths.get("resources", "screen.png").toFile());
        // } catch (IOException e) {
        //     e.printStackTrace();
        // }

        return image;
    }

    public Area getOutline(BufferedImage i, int targetTransp) {

        // construct the GeneralPath
        GeneralPath gp = new GeneralPath();
        gp.moveTo(0, 0);

        boolean drawing = false;
        for (int y = 0; y < i.getHeight(); y++) {
            for (int x = 0; x < i.getWidth(); x++) {

                int rgb = i.getRGB(x, y);
                boolean isTransp = (rgb >>> 24) <= targetTransp;

                if (isTransp) {
                    if (drawing) {
                        gp.closePath();
                    }
                    drawing = false;
                } else {
                    drawing = true;
                    gp.moveTo(x, y);
                    gp.lineTo(x + 1, y);
                    gp.lineTo(x + 1, y + 1);
                    gp.lineTo(x, y + 1);
                    gp.moveTo(x, y);

                }
            }
            gp.closePath();
        }
        gp.closePath();
        // construct the Area from the GP & return it
        return new Area(gp);
    }

    private void idle() {
        new Thread(() -> {
            Util.sleep(5000); // Wait 5 seconds before starting idle loop
            while (running) {
                secretaryLabel.waitIdle();
                speak(waifuInterface.getDialogs(waifuInterface.onIdleEventKey()));
                secretaryLabel.waitSpeak();
            }
        }).start();
    }

    private void onLogin() {
        new Thread(() -> {
            Util.sleep(Settings.get(WELCOME_DELAY, 5000));
            speak(waifuInterface.getDialogs(waifuInterface.onLoginEventKey()));
        }).start();
    }


    private Image loadSkin(int index) throws IOException {

        if (index < 0) {
            index = waifuInterface.getSkinCount() - 1;
        } else if (index >= waifuInterface.getSkinCount()) {
            index = 0;
        }

        skinIndex = index;

        byte[] imgData = WaifuUtils.getShipImage(waifuInterface, index);

        buffImage = ImageIO.read(new ByteArrayInputStream(imgData));
        if (Settings.get(MIRRORED, false)) {
            buffImage = Util.flipImage(buffImage);
        }

        // int lastPixel = Util.getEmptyPixelsFromBottom(buffImage);
        Image i = buffImage.getScaledInstance(-1, Settings.get(MAX_HEIGHT, 800), SCALE_AREA_AVERAGING);

        setSize(i.getWidth(null), i.getHeight(null));
        setLocation(getX(), Util.getYStartPosition(i.getHeight(null)));

        return i;
    }

    public int getStartY() {
        int y = Util.getScreenSize().height - getHeight();
        String settingPos = Settings.get("waifu.startY", "auto").toLowerCase();
        if (!settingPos.equals("auto")) {
            if (settingPos.matches("-?\\d+")) {
                y += Integer.parseInt(settingPos);
            }
        }

        return y;
    }

    private void swingSetup() throws IOException {
        setTitle(waifuInterface.getShowableName());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Listeners
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addKeyListener(this);
        addWindowListener(this);

        setLayout(null);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));

        ImageIcon icn = new ImageIcon(loadSkin(Settings.get("waifu.skinIndex", 0)));
        secretaryLabel = new SecretaryLabel(icn, this);
        secretaryLabel.setBounds(secretaryLabel.getDesiredBounds(icn.getIconWidth(), icn.getIconHeight()));

        baloon = new Baloon(getWidth(), getHeight());
        baloon.setBounds(baloon.getDesiredSize(icn.getIconWidth(), icn.getIconHeight()));

        add(baloon);
        add(secretaryLabel);

        URL ico = this.getClass().getResource("/icon.png");
        if (ico != null) {
            setIconImage(new ImageIcon(ico).getImage());
        }

        setAlwaysOnTop(alwaysOnTop);
        setLocation(waifuInterface.getWaifuData().getPosition(), getStartY());
        setType(Type.POPUP);
        setVisible(true);

        secretaryLabel.onVisible();

        System.out.println("Swing setup done");
        running = true;
    }

    public void toggleAlwaysOnTop() {
        alwaysOnTop = !alwaysOnTop;
        setAlwaysOnTop(alwaysOnTop);
        System.out.println("Always on top: " + alwaysOnTop);
    }

    public void reloadSkin() throws IOException {
        secretaryLabel.setIcon(new ImageIcon(loadSkin(skinIndex)));
        secretaryLabel.setBounds(secretaryLabel.getDesiredBounds(secretaryLabel.getIcon().getIconWidth(), secretaryLabel.getIcon().getIconHeight()));
        baloon.setBounds(baloon.getDesiredSize(secretaryLabel.getIcon().getIconWidth(), secretaryLabel.getIcon().getIconHeight()));
        setLocation(getX(), getStartY());
    }

    public void speak(List<Dialog> dialogs) {
        if (secretaryLabel.isSpeaking() || !Settings.get(DIALOGS_ENABLED, true) || !running) {
            return;
        }

        if (dialogs.isEmpty()) {
            return;
        }

        Dialog dialog = dialogs.get(new Random().nextInt(dialogs.size()));

        SwingUtilities.invokeLater(() -> {
            secretaryLabel.speak(true);

            baloon.setText(
                    Settings.get(BALOON_TEXT_FORMAT_HTML, "[[text]]")
                            .replace("[[text]]", dialog.getDialog() + "<br>&zwnj;")
            );

            // Activate baloon only if there is text to show
            baloon.toggle(!dialog.getDialog().equals(""));
            new Thread(() -> {
                if (Settings.get(VOICE_ENABLED, true) && dialog.getAudio() != null) {
                    audioManager.play(this.waifuInterface, dialog.getAudio(), Settings.get(VOICE_VOLUME, 50));
                } else {
                    Util.sleep(Settings.get(BALOON_DURATION_NO_VOICE, 3) * 1000);
                }

                baloon.toggle(false);
                secretaryLabel.speak(false);
            }).start();
        });
    }

    public boolean isDragging() {
        return isDragging;
    }

    public void close() {
        this.running = false;
        System.out.println("Closed");
        this.dispose();
    }

    private void onClick() {

        if (Settings.get(DIALOGS_ON_CLICK_ENABLED, true)) {
            speak(waifuInterface.getDialogs(waifuInterface.onTouchEventKey()));
        }
    }

    // Region: Swing Mouse Events
    @Override
    public void mousePressed(MouseEvent e) {
        xClickPosition = e.getXOnScreen();
        dragDiff = xClickPosition - getX();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (xClickPosition == e.getXOnScreen()) {
            secretaryLabel.speakJump();
            onClick();
        }
        isDragging = false;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        isDragging = true;
        setLocation(e.getXOnScreen() - dragDiff, getY());
    }

    @Override
    public void keyTyped(KeyEvent e) {
        try {
            switch (e.getKeyChar()) {
                case 'k':
                    skinIndex++;
                    reloadSkin();
                    break;
                case 'j':
                    skinIndex--;
                    reloadSkin();
                    break;
                case 't':
                    toggleAlwaysOnTop();
                    break;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == 2) {
            dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        }

    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void windowOpened(WindowEvent e) {
        long waifuUptime = waifuInterface.getUptime();
        long waifuStartTimeSeconds = (waifuUptime / 1000000);
        long waifuStartTimeMillis = waifuUptime - waifuStartTimeSeconds;
        System.out.println("Secretary up and running in " + waifuStartTimeSeconds + "." + waifuStartTimeMillis + " seconds");
    }

    @Override
    public void windowClosing(WindowEvent e) {
        try {
            waifuInterface.getWaifuData().setPosition(getX());
            IWaifuAdapter.saveDataToFile(waifuInterface);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        close();
    }

    @Override
    public void windowClosed(WindowEvent e) {

    }

    @Override
    public void windowIconified(WindowEvent e) {
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
    }

    @Override
    public void windowActivated(WindowEvent e) {
        //setOpacity(Settings.get("waifu.active.opacity", 100.0f) / 100.0f);
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
        // secretaryLabel.getIcon().
        // setOpacity(Settings.get("waifu.inactive.opacity", 100.0f) / 100.0f);
    }

    // Endregion
}