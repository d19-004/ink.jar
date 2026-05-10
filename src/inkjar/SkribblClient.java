package client;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class SkribblClient extends JPanel implements Runnable, Constants {
    private static final int W = 1100;
    private static final int H = 700;
    private static final int CANVAS_W = 680;
    private static final int CANVAS_H = 520;
    private static final int SIDEBAR_W = 170;
    private static final int TOP_H = 60;

    private static final Color[] PALETTE = {
            Color.BLACK, Color.WHITE, Color.RED, Color.BLUE,
            Color.GREEN, Color.ORANGE, Color.YELLOW, Color.PINK,
            Color.MAGENTA, Color.CYAN, new Color(139, 69, 19), new Color(128, 0, 128)
    };

    private final JFrame frame = new JFrame();
    private final String server;
    private final String myName;
    private final DatagramSocket socket;

    private volatile boolean connected = false;
    private volatile String drawerName = "";
    private volatile String wordLabel = "Waiting for game to start...";
    private volatile boolean imDrawing = false;
    private volatile int timerSecs = 0;
    private volatile boolean gameOver = false;
    private volatile String gameOverMsg = "";

    private final List<String> playerList = new ArrayList<>();
    private final Map<String, Integer> scores = new LinkedHashMap<>();

    private final BufferedImage canvas;
    private final Graphics2D canvasG;

    private Color drawColor = Color.BLACK;
    private int drawSize = 8;
    private int prevMouseX = -1;
    private int prevMouseY = -1;
    private boolean mouseDown = false;

    private final JTextField guessField = new JTextField();
    private final JTextArea chatArea = new JTextArea();

    public SkribblClient(String server, String name) throws Exception {
        this.server = server;
        this.myName = name;
        socket = new DatagramSocket();
        socket.setSoTimeout(100);

        canvas = new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_RGB);
        canvasG = canvas.createGraphics();
        canvasG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        clearCanvas();

        buildUI();
        new Thread(this, "recv-thread").start();
    }

    private void buildUI() {
        frame.setTitle("Skribbl - " + myName);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setLayout(new BorderLayout());

        setPreferredSize(new Dimension(W - 260, H));
        setBackground(new Color(240, 240, 250));
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { onMousePress(e); }
            @Override public void mouseReleased(MouseEvent e) {
                mouseDown = false;
                prevMouseX = -1;
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) { onMouseDrag(e); }
        });
        frame.add(this, BorderLayout.CENTER);

        JPanel east = new JPanel(new BorderLayout(4, 4));
        east.setPreferredSize(new Dimension(260, H));
        east.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 8));

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        east.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        guessField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        guessField.addActionListener(e -> submitGuess());
        JButton sendBtn = new JButton("Send");
        sendBtn.addActionListener(e -> submitGuess());
        inputRow.add(guessField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        east.add(inputRow, BorderLayout.SOUTH);

        frame.add(east, BorderLayout.EAST);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int panelWidth = getWidth();
        int panelHeight = getHeight();

        g.setColor(new Color(60, 60, 180));
        g.fillRect(0, 0, panelWidth, TOP_H);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString(wordLabel, 12, 38);

        if (timerSecs > 0) {
            String timerText = timerSecs + "s";
            g.setColor(timerSecs <= 10 ? Color.RED : Color.YELLOW);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(timerText, panelWidth - fm.stringWidth(timerText) - 12, 38);
        }

        g.setColor(new Color(230, 230, 245));
        g.fillRect(0, TOP_H, SIDEBAR_W, panelHeight - TOP_H);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString("Players", 8, TOP_H + 20);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int py = TOP_H + 40;
        synchronized (playerList) {
            for (String playerName : playerList) {
                boolean isDrawer = playerName.equals(drawerName);
                g.setColor(isDrawer ? new Color(0, 100, 200) : Color.DARK_GRAY);
                int score = scores.getOrDefault(playerName, 0);
                String line = (playerName.equals(myName) ? "* " : "  ")
                        + (isDrawer ? "[D] " : "    ")
                        + playerName + "  " + score;
                g.drawString(line, 6, py);
                py += 18;
            }
        }

        g.drawImage(canvas, SIDEBAR_W, TOP_H, null);
        g.setColor(Color.BLACK);
        g.drawRect(SIDEBAR_W - 1, TOP_H - 1, CANVAS_W + 2, CANVAS_H + 2);

        paintToolbar(g);

        if (gameOver) {
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRect(0, 0, panelWidth, panelHeight);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(gameOverMsg, (panelWidth - fm.stringWidth(gameOverMsg)) / 2, panelHeight / 2);
        }
    }

    private void paintToolbar(Graphics g) {
        int palY = TOP_H + CANVAS_H + 8;
        int palX = SIDEBAR_W;
        int swatchSize = 32;
        for (int i = 0; i < PALETTE.length; i++) {
            int x = palX + i * (swatchSize + 4);
            g.setColor(PALETTE[i]);
            g.fillRect(x, palY, swatchSize, swatchSize);
            g.setColor(PALETTE[i].equals(drawColor) ? Color.RED : Color.GRAY);
            g.drawRect(x - 1, palY - 1, swatchSize + 1, swatchSize + 1);
        }

        int szX = palX + PALETTE.length * (swatchSize + 4) + 16;
        int[] sizes = {4, 8, 14, 20};
        for (int i = 0; i < sizes.length; i++) {
            int x = szX + i * 42;
            g.setColor(drawSize == sizes[i] ? new Color(180, 220, 255) : new Color(220, 220, 220));
            g.fillRoundRect(x, palY, 38, swatchSize, 6, 6);
            g.setColor(Color.DARK_GRAY);
            g.drawRoundRect(x, palY, 38, swatchSize, 6, 6);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g.drawString(sizes[i] + "px", x + 4, palY + 20);
        }

        int clearX = szX + sizes.length * 42 + 10;
        g.setColor(new Color(255, 100, 100));
        g.fillRoundRect(clearX, palY, 56, swatchSize, 6, 6);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString("Clear", clearX + 8, palY + 20);
    }

    private void onMousePress(MouseEvent e) {
        int mx = e.getX();
        int my = e.getY();
        int palY = TOP_H + CANVAS_H + 8;
        int swatchSize = 32;

        if (my >= palY && my <= palY + swatchSize) {
            int palX = SIDEBAR_W;
            for (int i = 0; i < PALETTE.length; i++) {
                int x = palX + i * (swatchSize + 4);
                if (mx >= x && mx <= x + swatchSize) {
                    drawColor = PALETTE[i];
                    repaint();
                    return;
                }
            }

            int szX = palX + PALETTE.length * (swatchSize + 4) + 16;
            int[] sizes = {4, 8, 14, 20};
            for (int i = 0; i < sizes.length; i++) {
                int x = szX + i * 42;
                if (mx >= x && mx <= x + 38) {
                    drawSize = sizes[i];
                    repaint();
                    return;
                }
            }

            int clearX = szX + sizes.length * 42 + 10;
            if (mx >= clearX && mx <= clearX + 56 && imDrawing) {
                clearCanvas();
                send(CMD_CLEAR);
                repaint();
                return;
            }
        }

        if (imDrawing && mx >= SIDEBAR_W && mx < SIDEBAR_W + CANVAS_W
                && my >= TOP_H && my < TOP_H + CANVAS_H) {
            mouseDown = true;
            prevMouseX = mx - SIDEBAR_W;
            prevMouseY = my - TOP_H;
        }
    }

    private void onMouseDrag(MouseEvent e) {
        if (!imDrawing || !mouseDown) return;
        int cx = e.getX() - SIDEBAR_W;
        int cy = e.getY() - TOP_H;
        if (cx < 0 || cy < 0 || cx >= CANVAS_W || cy >= CANVAS_H) return;

        if (prevMouseX >= 0) {
            drawLine(prevMouseX, prevMouseY, cx, cy, drawColor, drawSize);
            send(CMD_DRAW + " " + prevMouseX + " " + prevMouseY + " " + cx + " " + cy
                    + " " + drawColor.getRed() + " " + drawColor.getGreen() + " " + drawColor.getBlue()
                    + " " + drawSize);
        }
        prevMouseX = cx;
        prevMouseY = cy;
        repaint();
    }

    private void drawLine(int x1, int y1, int x2, int y2, Color color, int size) {
        canvasG.setColor(color);
        canvasG.setStroke(new BasicStroke(size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        canvasG.drawLine(x1, y1, x2, y2);
    }

    private void clearCanvas() {
        canvasG.setColor(Color.WHITE);
        canvasG.fillRect(0, 0, CANVAS_W, CANVAS_H);
    }

    private void submitGuess() {
        String text = guessField.getText().trim();
        guessField.setText("");
        if (text.isEmpty()) return;
        send(CMD_GUESS + " " + text);
        appendChat(myName + ": " + text);
    }

    private void appendChat(String line) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(line + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private void send(String msg) {
        try {
            byte[] buf = msg.getBytes("UTF-8");
            InetAddress addr = InetAddress.getByName(server);
            DatagramPacket pkt = new DatagramPacket(buf, buf.length, addr, PORT);
            socket.send(pkt);
        } catch (Exception e) {
            System.err.println("[Client] Send error: " + e);
        }
    }

    @Override
    public void run() {
        while (true) {
            byte[] buf = new byte[1400];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(pkt);
            } catch (Exception e) {
                if (!connected) send(CMD_CONNECT + " " + myName);
                continue;
            }

            String raw = new String(buf, 0, pkt.getLength()).trim();
            if (raw.isEmpty()) continue;
            handlePacket(raw);
            repaint();
        }
    }

    private void handlePacket(String raw) {
        String[] tok = raw.split(" ", 5);
        String cmd = tok[0].toUpperCase();

        switch (cmd) {
            case CMD_CONNECTED:
                connected = true;
                appendChat("*** Connected. Waiting for game to start...");
                break;
            case CMD_ERROR:
                appendChat("ERROR: " + afterCommand(raw, cmd));
                break;
            case CMD_PLAYER_LIST:
                updatePlayerList(tok.length > 1 ? tok[1] : "");
                break;
            case CMD_PLAYER_JOIN:
                if (tok.length > 1) addPlayer(tok[1]);
                break;
            case CMD_PLAYER_LEAVE:
                if (tok.length > 1) removePlayer(tok[1]);
                break;
            case CMD_SCORES:
                updateScores(tok.length > 1 ? tok[1] : "");
                break;
            case CMD_ROUND_START:
                handleRoundStart(tok);
                break;
            case CMD_YOUR_WORD:
                wordLabel = "Draw: " + afterCommand(raw, cmd);
                appendChat("Your word: " + afterCommand(raw, cmd));
                break;
            case CMD_HINT:
                if (!imDrawing) {
                    String hint = afterCommand(raw, cmd);
                    wordLabel = drawerName + " is drawing... [" + hint + "]";
                    appendChat("Hint: " + hint);
                }
                break;
            case CMD_TIMER:
                if (tok.length > 1) {
                    try { timerSecs = Integer.parseInt(tok[1]); } catch (NumberFormatException ignored) {}
                }
                break;
            case CMD_DRAW_FWD:
                handleDrawForward(afterCommand(raw, cmd));
                break;
            case CMD_CLEAR_FWD:
                clearCanvas();
                break;
            case CMD_CHAT:
                handleChat(raw, tok);
                break;
            case CMD_CORRECT:
                if (tok.length >= 3) {
                    appendChat("*** " + tok[1] + " guessed correctly! +" + tok[2] + " pts");
                    if (tok[1].equals(myName)) wordLabel = "You got it!";
                }
                break;
            case CMD_ROUND_END:
                appendChat("--- Round over. The word was: " + afterCommand(raw, cmd) + " ---");
                imDrawing = false;
                timerSecs = 0;
                wordLabel = "Round over...";
                break;
            case CMD_GAME_END:
                gameOver = true;
                gameOverMsg = tok.length >= 3
                        ? "Game Over! Winner: " + tok[1] + " (" + tok[2] + " pts)"
                        : "Game Over!";
                appendChat("=== " + gameOverMsg + " ===");
                imDrawing = false;
                timerSecs = 0;
                wordLabel = gameOverMsg;
                break;
            default:
                break;
        }
    }

    private void handleRoundStart(String[] tok) {
        if (tok.length < 5) return;
        drawerName = tok[1];
        int round = parseInt(tok[2]);
        int total = parseInt(tok[3]);
        String hint = tok[4];
        imDrawing = myName.equals(drawerName);
        wordLabel = imDrawing
                ? "Your turn to draw! Round " + round + "/" + total
                : drawerName + " is drawing... Round " + round + "/" + total + " [" + hint + "]";
        clearCanvas();
        timerSecs = ROUND_SECONDS;
        appendChat("--- Round " + round + "/" + total + " Drawer: " + drawerName + " ---");
    }

    private void handleDrawForward(String data) {
        String[] d = data.split(" ");
        if (d.length < 8) return;
        try {
            int x1 = Integer.parseInt(d[0]);
            int y1 = Integer.parseInt(d[1]);
            int x2 = Integer.parseInt(d[2]);
            int y2 = Integer.parseInt(d[3]);
            int r = Integer.parseInt(d[4]);
            int g = Integer.parseInt(d[5]);
            int b = Integer.parseInt(d[6]);
            int size = Integer.parseInt(d[7]);
            drawLine(x1, y1, x2, y2, new Color(r, g, b), size);
        } catch (Exception ignored) {
        }
    }

    private void handleChat(String raw, String[] tok) {
        if (tok.length < 3) return;
        String sender = tok[1];
        String msg = raw.substring(CMD_CHAT.length() + 1 + sender.length() + 1);
        if (!sender.equals(myName)) {
            appendChat(sender + ": " + msg);
        }
    }

    private void updatePlayerList(String csv) {
        synchronized (playerList) {
            playerList.clear();
            if (!csv.isEmpty()) {
                for (String name : csv.split(",")) {
                    if (!name.isBlank()) playerList.add(name.trim());
                }
            }
            if (!playerList.contains(myName)) playerList.add(myName);
        }
    }

    private void addPlayer(String name) {
        synchronized (playerList) {
            if (!playerList.contains(name)) playerList.add(name);
        }
        appendChat("*** " + name + " joined.");
    }

    private void removePlayer(String name) {
        synchronized (playerList) {
            playerList.remove(name);
        }
        appendChat("*** " + name + " left.");
    }

    private void updateScores(String csv) {
        synchronized (playerList) {
            scores.clear();
            for (String pair : csv.split(",")) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    try { scores.put(kv[0], Integer.parseInt(kv[1])); } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    private static String afterCommand(String raw, String cmd) {
        return raw.length() > cmd.length() ? raw.substring(cmd.length() + 1) : "";
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java client.SkribblClient <server> <name>");
            System.exit(1);
        }
        new SkribblClient(args[0], args[1]);
    }
}
