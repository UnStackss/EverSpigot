package net.minecraft.server.gui;

import com.google.common.collect.Lists;
import com.mojang.logging.LogQueues;
import com.mojang.logging.LogUtils;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.server.dedicated.DedicatedServer;
import org.slf4j.Logger;

public class MinecraftServerGui extends JComponent {

    private static final Font MONOSPACED = new Font("Monospaced", 0, 12);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TITLE = "Minecraft server";
    private static final String SHUTDOWN_TITLE = "Minecraft server - shutting down!";
    private final DedicatedServer server;
    private Thread logAppenderThread;
    private final Collection<Runnable> finalizers = Lists.newArrayList();
    final AtomicBoolean isClosing = new AtomicBoolean();

    public static MinecraftServerGui showFrameFor(final DedicatedServer server) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception exception) {
            ;
        }

        final JFrame jframe = new JFrame("Minecraft server");
        final MinecraftServerGui servergui = new MinecraftServerGui(server);

        jframe.setDefaultCloseOperation(2);
        jframe.add(servergui);
        jframe.pack();
        jframe.setLocationRelativeTo((Component) null);
        jframe.setVisible(true);
        jframe.setName("Minecraft server"); // Paper - Improve ServerGUI

        // Paper start - Improve ServerGUI
        try {
            jframe.setIconImage(javax.imageio.ImageIO.read(Objects.requireNonNull(MinecraftServerGui.class.getClassLoader().getResourceAsStream("logo.png"))));
        } catch (java.io.IOException ignore) {
        }
        // Paper end - Improve ServerGUI

        jframe.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent windowevent) {
                if (!servergui.isClosing.getAndSet(true)) {
                    jframe.setTitle("Minecraft server - shutting down!");
                    server.halt(true);
                    servergui.runFinalizers();
                }

            }
        });
        Objects.requireNonNull(jframe);
        servergui.addFinalizer(jframe::dispose);
        servergui.start();
        return servergui;
    }

    private MinecraftServerGui(DedicatedServer server) {
        this.server = server;
        this.setPreferredSize(new Dimension(854, 480));
        this.setLayout(new BorderLayout());

        try {
            this.add(this.buildOnboardingPanel(), "North"); // Paper - Add onboarding message for initial server start
            this.add(this.buildChatPanel(), "Center");
            this.add(this.buildInfoPanel(), "West");
        } catch (Exception exception) {
            MinecraftServerGui.LOGGER.error("Couldn't build server GUI", exception);
        }

    }

    public void addFinalizer(Runnable task) {
        this.finalizers.add(task);
    }

    private JComponent buildInfoPanel() {
        JPanel jpanel = new JPanel(new BorderLayout());
        com.destroystokyo.paper.gui.GuiStatsComponent guistatscomponent = new com.destroystokyo.paper.gui.GuiStatsComponent(this.server); // Paper - Make GUI graph fancier
        Collection<Runnable> collection = this.finalizers; // CraftBukkit - decompile error

        Objects.requireNonNull(guistatscomponent);
        collection.add(guistatscomponent::close);
        jpanel.add(guistatscomponent, "North");
        jpanel.add(this.buildPlayerPanel(), "Center");
        jpanel.setBorder(new TitledBorder(new EtchedBorder(), "Stats"));
        return jpanel;
    }

    // Paper start - Add onboarding message for initial server start
    private JComponent buildOnboardingPanel() {
        String onboardingLink = "https://docs.papermc.io/paper/next-steps";
        JPanel jPanel = new JPanel();

        javax.swing.JLabel jLabel = new javax.swing.JLabel("If you need help setting up your server you can visit:");
        jLabel.setFont(MinecraftServerGui.MONOSPACED);

        javax.swing.JLabel link = new javax.swing.JLabel("<html><u> " + onboardingLink + "</u></html>");
        link.setFont(MinecraftServerGui.MONOSPACED);
        link.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        link.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(final java.awt.event.MouseEvent e) {
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(onboardingLink));
                } catch (java.io.IOException exception) {
                    LOGGER.error("Unable to find a default browser. Please manually visit the website: " + onboardingLink, exception);
                } catch (UnsupportedOperationException exception) {
                    LOGGER.error("This platform does not support the BROWSE action. Please manually visit the website: " + onboardingLink, exception);
                } catch (SecurityException exception) {
                    LOGGER.error("This action has been denied by the security manager. Please manually visit the website: " + onboardingLink, exception);
                }
            }
        });

        jPanel.add(jLabel);
        jPanel.add(link);

        return jPanel;
    }
    // Paper end - Add onboarding message for initial server start

    private JComponent buildPlayerPanel() {
        JList<?> jlist = new PlayerListComponent(this.server);
        JScrollPane jscrollpane = new JScrollPane(jlist, 22, 30);

        jscrollpane.setBorder(new TitledBorder(new EtchedBorder(), "Players"));
        return jscrollpane;
    }

    private JComponent buildChatPanel() {
        JPanel jpanel = new JPanel(new BorderLayout());
        JTextArea jtextarea = new JTextArea();
        JScrollPane jscrollpane = new JScrollPane(jtextarea, 22, 30);

        jtextarea.setEditable(false);
        jtextarea.setFont(MinecraftServerGui.MONOSPACED);
        JTextField jtextfield = new JTextField();

        jtextfield.addActionListener((actionevent) -> {
            String s = jtextfield.getText().trim();

            if (!s.isEmpty()) {
                this.server.handleConsoleInput(s, this.server.createCommandSourceStack());
            }

            jtextfield.setText("");
        });
        jtextarea.addFocusListener(new FocusAdapter() { // CraftBukkit - decompile error
            public void focusGained(FocusEvent focusevent) {}
        });
        jpanel.add(jscrollpane, "Center");
        jpanel.add(jtextfield, "South");
        jpanel.setBorder(new TitledBorder(new EtchedBorder(), "Log and chat"));
        this.logAppenderThread = new Thread(() -> {
            String s;

            while ((s = LogQueues.getNextLogEvent("ServerGuiConsole")) != null) {
                this.print(jtextarea, jscrollpane, s);
            }

        });
        this.logAppenderThread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(MinecraftServerGui.LOGGER));
        this.logAppenderThread.setDaemon(true);
        return jpanel;
    }

    public void start() {
        this.logAppenderThread.start();
    }

    public void close() {
        if (!this.isClosing.getAndSet(true)) {
            this.runFinalizers();
        }

    }

    void runFinalizers() {
        this.finalizers.forEach(Runnable::run);
    }

    private static final java.util.regex.Pattern ANSI = java.util.regex.Pattern.compile("\\e\\[[\\d;]*[^\\d;]"); // CraftBukkit // Paper
    public void print(JTextArea textArea, JScrollPane scrollPane, String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> {
                this.print(textArea, scrollPane, message);
            });
        } else {
            Document document = textArea.getDocument();
            JScrollBar jscrollbar = scrollPane.getVerticalScrollBar();
            boolean flag = false;

            if (scrollPane.getViewport().getView() == textArea) {
                flag = (double) jscrollbar.getValue() + jscrollbar.getSize().getHeight() + (double) (MinecraftServerGui.MONOSPACED.getSize() * 4) > (double) jscrollbar.getMaximum();
            }

            try {
                document.insertString(document.getLength(), MinecraftServerGui.ANSI.matcher(message).replaceAll(""), (AttributeSet) null); // CraftBukkit
            } catch (BadLocationException badlocationexception) {
                ;
            }

            if (flag) {
                jscrollbar.setValue(Integer.MAX_VALUE);
            }

        }
    }
}
