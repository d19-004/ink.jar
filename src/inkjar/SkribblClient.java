package inkjar;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;

public class SkribblClient extends JPanel implements Runnable, Constants {

    // ── Layout ──────────────────────────────────────────────────────────────────
    private static final int W          = 1200;
    private static final int H          = 740;
    private static final int TOP_H      = 56;
    private static final int TOOLBAR_H  = 64;
    private static final int RIGHT_W    = 300;
    private static final int CANVAS_W   = W - RIGHT_W - 32;
    private static final int CANVAS_H   = H - TOP_H - TOOLBAR_H - 48;

    // ── Dark palette ────────────────────────────────────────────────────────────
    private static final Color BG           = new Color(15,  16,  22);
    private static final Color SURFACE      = new Color(24,  26,  36);
    private static final Color CARD         = new Color(30,  33,  48);
    private static final Color CARD_ALT     = new Color(36,  40,  58);
    private static final Color BORDER       = new Color(50,  54,  78);
    private static final Color ACCENT       = new Color(99,  102, 241);
    private static final Color ACCENT_HOV   = new Color(118, 120, 255);
    private static final Color GOLD         = new Color(196, 148,  48);
    private static final Color GOLD_BG      = new Color(196, 148,  48, 35);
    private static final Color SUCCESS      = new Color(52,  211, 153);
    private static final Color WARNING      = new Color(251, 191,  36);
    private static final Color DANGER       = new Color(248, 113, 113);
    private static final Color TEXT_PRI     = new Color(232, 234, 255);
    private static final Color TEXT_SEC     = new Color(140, 144, 180);
    private static final Color TEXT_DIM     = new Color(80,  84,  120);

    // ── Drawing palette ─────────────────────────────────────────────────────────
    private static final Color[] PALETTE = {
        new Color(15,  15,  15),
        new Color(255, 255, 255),
        new Color(239,  68,  68),
        new Color(59,  130, 246),
        new Color(120,  53,  15),
        new Color(34,  197,  94),
        new Color(234, 179,   8),
        new Color(168,  85, 247),
        new Color(249, 115,  22),
        new Color(107, 114, 128),
    };

    // ── State ────────────────────────────────────────────────────────────────────
    private final JFrame frame = new JFrame();
    private final String server, myName;
    private final DatagramSocket socket;

    private volatile boolean connected    = false;
    private volatile String  drawerName   = "";
    private volatile String  wordLabel    = "Waiting for game…";
    private volatile String  hintText     = "";
    private volatile boolean imDrawing    = false;
    private volatile int     timerSecs    = 0;
    private volatile boolean gameOver     = false;
    private volatile String  gameOverMsg  = "";
    private volatile int     currentRound = 0;
    private volatile int     totalRounds  = 0;

    private final List<String>         playerList = new ArrayList<>();
    private final Map<String, Integer> scores     = new LinkedHashMap<>();

    // ── Canvas ───────────────────────────────────────────────────────────────────
    private final BufferedImage canvas;
    private final Graphics2D    canvasG;
    private Color   drawColor  = new Color(15, 15, 15);
    private int     drawSize   = 8;
    private int     prevMX = -1, prevMY = -1;
    private boolean mouseDown  = false;
    private boolean eraserMode = false;

    // ── Swing ─────────────────────────────────────────────────────────────────────
    private final JTextPane  chatPane   = new JTextPane();
    private final JTextField guessField = new JTextField();
    // canvas origin for hit-testing (set in paintComponent)
    private int canvasOriginX, canvasOriginY;

    // ── Constructor ───────────────────────────────────────────────────────────────
    public SkribblClient(String server, String name) throws Exception {
        this.server = server;
        this.myName = name;
        socket = new DatagramSocket();
        socket.setSoTimeout(100);
        canvas  = new BufferedImage(CANVAS_W, CANVAS_H, BufferedImage.TYPE_INT_RGB);
        canvasG = canvas.createGraphics();
        canvasG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        clearCanvas();
        buildUI();
        new Thread(this, "recv").start();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BUILD UI
    // ═══════════════════════════════════════════════════════════════════════════
    private void buildUI() {
        frame.setTitle("ink.jar");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setBackground(BG);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(buildTopBar(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(12, 0));
        body.setBackground(BG);
        body.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Canvas panel (this)
        setPreferredSize(new Dimension(W - RIGHT_W - 32, H - TOP_H - TOOLBAR_H - 48));
        setBackground(BG);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { onCanvasPress(e); }
            @Override public void mouseReleased(MouseEvent e) { mouseDown = false; prevMX = -1; }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) { onCanvasDrag(e); }
        });

        JPanel leftCol = new JPanel(new BorderLayout(0, 8));
        leftCol.setBackground(BG);
        leftCol.add(this, BorderLayout.CENTER);
        leftCol.add(buildToolbarPanel(), BorderLayout.SOUTH);

        body.add(leftCol, BorderLayout.CENTER);
        body.add(buildRightPanel(), BorderLayout.EAST);
        root.add(body, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.setPreferredSize(new Dimension(W, H));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ── Top bar ──────────────────────────────────────────────────────────────────
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                paintTopBar(g2);
            }
        };
        bar.setPreferredSize(new Dimension(W, TOP_H));
        bar.setBackground(SURFACE);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        return bar;
    }

    private void paintTopBar(Graphics2D g) {
        // Logo
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.setColor(ACCENT);
        g.drawString("ink", 18, 36);
        g.setColor(TEXT_SEC);
        g.drawString(".jar", 18 + g.getFontMetrics().stringWidth("ink"), 36);

        int cx = W / 2;

        // Timer pill (left of center)
        String timerStr = String.format("%d:%02d", timerSecs / 60, timerSecs % 60);
        Color  timerCol = timerSecs <= 10 ? DANGER : timerSecs <= 20 ? WARNING : SUCCESS;
        int tpW = 90, tpH = 34, tpX = cx - 250, tpY = (TOP_H - tpH) / 2;
        drawPill(g, tpX, tpY, tpW, tpH, CARD, BORDER);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(TEXT_DIM);
        g.drawString("⏱", tpX + 10, tpY + 23);
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(timerCol);
        g.drawString(timerStr, tpX + 32, tpY + 23);

        // Word center pill
        int wpW = 230, wpH = 42, wpX = cx - wpW / 2, wpY = (TOP_H - wpH) / 2;
        drawPill(g, wpX, wpY, wpW, wpH, CARD, BORDER);
        g.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g.setColor(TEXT_DIM);
        String topLabel = imDrawing ? "YOUR WORD" : "CURRENT WORD";
        FontMetrics ftm = g.getFontMetrics();
        g.drawString(topLabel, wpX + (wpW - ftm.stringWidth(topLabel)) / 2, wpY + 14);
        String wordDisplay = imDrawing
                ? wordLabel.replace("Draw: ", "").toUpperCase()
                : hintText.isEmpty() ? wordLabel.toUpperCase() : hintText.toUpperCase();
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(TEXT_PRI);
        FontMetrics fwm = g.getFontMetrics();
        g.drawString(wordDisplay, wpX + (wpW - fwm.stringWidth(wordDisplay)) / 2, wpY + 31);

        // Round pill (right of center)
        if (totalRounds > 0) {
            String rStr = "Round " + currentRound + "/" + totalRounds;
            int rpW = 120, rpH = 34, rpX = cx + 148, rpY = (TOP_H - rpH) / 2;
            drawPill(g, rpX, rpY, rpW, rpH, GOLD_BG, GOLD);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(GOLD);
            FontMetrics frm = g.getFontMetrics();
            g.drawString(rStr, rpX + (rpW - frm.stringWidth(rStr)) / 2, rpY + 22);
        }
    }

    // ── Toolbar panel ─────────────────────────────────────────────────────────────
    private JPanel buildToolbarPanel() {
        JPanel bar = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                paintToolbar(g2, getWidth());
            }
            @Override public boolean isOpaque() { return false; }
        };
        bar.setPreferredSize(new Dimension(0, TOOLBAR_H));
        bar.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                onToolbarClick(e, ((JPanel) e.getSource()).getWidth());
            }
        });
        return bar;
    }

    private void paintToolbar(Graphics2D g, int tw) {
        int mcy = TOOLBAR_H / 2;

        if (!imDrawing) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.setColor(TEXT_DIM);
            String msg = "Drawing tools available on your turn";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(msg, (tw - fm.stringWidth(msg)) / 2, mcy + 5);
            return;
        }

        // Tool buttons
        int bh = 34, bw = 36, by = mcy - bh / 2, bx = 12;
        paintToolBtn(g, bx, by, bw, bh, "✏", !eraserMode);
        paintToolBtn(g, bx + bw + 6, by, bw, bh, "⬜", eraserMode);

        // Divider
        int d1 = bx + bw * 2 + 20;
        g.setColor(BORDER);
        g.fillRect(d1, 12, 1, TOOLBAR_H - 24);

        // Brush sizes (circles)
        int[] sizes = {4, 8, 14, 22};
        int szX = d1 + 14;
        for (int i = 0; i < sizes.length; i++) {
            int r  = sizes[i] / 2 + 2;
            int cx = szX + i * 46 + 22;
            boolean sel = drawSize == sizes[i];
            g.setColor(sel ? TEXT_PRI : TEXT_DIM);
            g.fillOval(cx - r, mcy - r, r * 2, r * 2);
            if (sel) {
                g.setColor(ACCENT);
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval(cx - r - 3, mcy - r - 3, (r + 3) * 2, (r + 3) * 2);
                g.setStroke(new BasicStroke(1f));
            }
        }

        // Divider 2
        int d2 = szX + sizes.length * 46 + 6;
        g.setColor(BORDER);
        g.fillRect(d2, 12, 1, TOOLBAR_H - 24);

        // Color palette (circles)
        int sw = 26, swX = d2 + 12;
        for (int i = 0; i < PALETTE.length; i++) {
            int px = swX + i * (sw + 6) + sw / 2;
            boolean sel = !eraserMode && PALETTE[i].getRGB() == drawColor.getRGB();
            if (sel) {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2f));
                g.drawOval(px - sw / 2 - 3, mcy - sw / 2 - 3, sw + 6, sw + 6);
                g.setStroke(new BasicStroke(1f));
            }
            g.setColor(PALETTE[i]);
            g.fillOval(px - sw / 2, mcy - sw / 2, sw, sw);
            if (PALETTE[i].equals(Color.WHITE)) {
                g.setColor(BORDER);
                g.setStroke(new BasicStroke(0.8f));
                g.drawOval(px - sw / 2, mcy - sw / 2, sw, sw);
                g.setStroke(new BasicStroke(1f));
            }
        }

        // Trash button
        int trX = tw - 52, trY = mcy - bh / 2;
        g.setColor(new Color(248, 113, 113, 35));
        g.fillRoundRect(trX, trY, 40, bh, 8, 8);
        g.setColor(DANGER);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(trX, trY, 40, bh, 8, 8);
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        FontMetrics fm = g.getFontMetrics();
        g.drawString("🗑", trX + (40 - fm.stringWidth("🗑")) / 2, trY + 23);
    }

    private void paintToolBtn(Graphics2D g, int x, int y, int w, int h, String icon, boolean active) {
        g.setColor(active ? ACCENT : CARD_ALT);
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(active ? ACCENT_HOV : BORDER);
        g.setStroke(new BasicStroke(active ? 1.5f : 0.8f));
        g.drawRoundRect(x, y, w, h, 8, 8);
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(active ? Color.WHITE : TEXT_SEC);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(icon, x + (w - fm.stringWidth(icon)) / 2,
                     y + (h + fm.getAscent() - fm.getDescent()) / 2);
    }

    // ── Right panel ──────────────────────────────────────────────────────────────
    private JPanel buildRightPanel() {
        JPanel right = new JPanel(new BorderLayout(0, 10));
        right.setPreferredSize(new Dimension(RIGHT_W, 0));
        right.setBackground(BG);
        right.add(buildPlayersCard(), BorderLayout.NORTH);
        right.add(buildGuessesCard(), BorderLayout.CENTER);
        return right;
    }

    private JPanel buildPlayersCard() {
        JPanel card = makeCard();

        JLabel header = makeCardHeader("Players");
        card.add(header, BorderLayout.NORTH);

        JPanel rows = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                paintPlayers(g2);
            }
            @Override public boolean isOpaque() { return false; }
        };
        rows.setPreferredSize(new Dimension(RIGHT_W, 52 * 4 + 10));
        card.add(rows, BorderLayout.CENTER);
        return card;
    }

    private void paintPlayers(Graphics2D g) {
        int rh = 52, y = 6, pw = RIGHT_W;
        synchronized (playerList) {
            List<String> sorted = new ArrayList<>(playerList);
            sorted.sort((a, b) -> scores.getOrDefault(b, 0) - scores.getOrDefault(a, 0));
            for (String pName : sorted) {
                boolean isMe     = pName.equals(myName);
                boolean isDrawer = pName.equals(drawerName);
                int     score    = scores.getOrDefault(pName, 0);

                // Row bg
                g.setColor(isDrawer ? GOLD_BG : isMe ? new Color(99, 102, 241, 28) : CARD_ALT);
                g.fillRect(1, y, pw - 2, rh - 4);
                if (isDrawer || isMe) {
                    g.setColor(isDrawer ? GOLD : ACCENT);
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawRect(1, y, pw - 3, rh - 5);
                    g.setStroke(new BasicStroke(1f));
                }

                // Avatar
                int av = 34, avX = 10, avY = y + (rh - 4 - av) / 2;
                g.setColor(isDrawer ? new Color(80, 55, 10) : isMe ? new Color(50, 52, 120) : CARD);
                g.fillOval(avX, avY, av, av);
                g.setColor(isDrawer ? GOLD : isMe ? ACCENT : BORDER);
                g.drawOval(avX, avY, av, av);
                String initials = pName.length() >= 2 ? pName.substring(0, 2).toUpperCase() : pName.toUpperCase();
                g.setFont(new Font("SansSerif", Font.BOLD, 13));
                g.setColor(isDrawer ? new Color(255, 220, 100) : isMe ? new Color(180, 185, 255) : TEXT_SEC);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(initials, avX + (av - fm.stringWidth(initials)) / 2,
                             avY + (av + fm.getAscent() - fm.getDescent()) / 2);

                // Name
                String dispName = isMe ? pName + " (You)" : pName;
                g.setFont(new Font("SansSerif", Font.BOLD, 13));
                g.setColor(isDrawer ? new Color(255, 220, 100) : TEXT_PRI);
                g.drawString(dispName, avX + av + 10, y + 22);
                if (isDrawer) {
                    g.setFont(new Font("SansSerif", Font.PLAIN, 11));
                    g.setColor(GOLD);
                    g.drawString("Drawing…", avX + av + 10, y + 36);
                }

                // Score
                String scoreStr = String.format("%,d", score);
                g.setFont(new Font("SansSerif", Font.BOLD, 18));
                g.setColor(isDrawer ? GOLD : TEXT_PRI);
                FontMetrics sfm = g.getFontMetrics();
                g.drawString(scoreStr, pw - sfm.stringWidth(scoreStr) - 12, y + 28);

                y += rh;
            }
        }
    }

    private JPanel buildGuessesCard() {
        JPanel card = makeCard();
        card.add(makeCardHeader("Guesses"), BorderLayout.NORTH);

        chatPane.setEditable(false);
        chatPane.setBackground(CARD);
        chatPane.setForeground(TEXT_PRI);
        chatPane.setFont(new Font("SansSerif", Font.PLAIN, 13));
        chatPane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(chatPane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setBackground(CARD);
        card.add(scroll, BorderLayout.CENTER);

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setOpaque(false);
        inputRow.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            BorderFactory.createEmptyBorder(8, 10, 10, 10)
        ));
        guessField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        guessField.setBackground(SURFACE);
        guessField.setForeground(TEXT_PRI);
        guessField.setCaretColor(TEXT_PRI);
        guessField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            BorderFactory.createEmptyBorder(7, 10, 7, 10)
        ));
        guessField.addActionListener(e -> submitGuess());
        inputRow.add(guessField, BorderLayout.CENTER);
        inputRow.add(buildSendBtn(), BorderLayout.EAST);
        card.add(inputRow, BorderLayout.SOUTH);
        return card;
    }

    private JPanel makeCard() {
        return new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            }
            @Override public boolean isOpaque() { return false; }
        };
    }

    private JLabel makeCardHeader(String title) {
        JLabel lbl = new JLabel("  " + title) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_ALT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight() + 12, 12, 12);
                g2.fillRect(0, getHeight() - 12, getWidth(), 12);
                g2.setColor(BORDER);
                g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                super.paintComponent(g);
            }
            @Override public boolean isOpaque() { return false; }
        };
        lbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        lbl.setForeground(TEXT_PRI);
        lbl.setPreferredSize(new Dimension(0, 38));
        return lbl;
    }

    private JButton buildSendBtn() {
        JButton btn = new JButton("▶") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed() ? ACCENT.darker() : ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                              (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(38, 34));
        btn.setOpaque(false); btn.setContentAreaFilled(false);
        btn.setBorderPainted(false); btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> submitGuess());
        return btn;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CANVAS PAINT
    // ═══════════════════════════════════════════════════════════════════════════
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int pw = getWidth(), ph = getHeight();

        // Card bg
        g2.setColor(CARD);
        g2.fillRoundRect(0, 0, pw, ph, 12, 12);
        g2.setColor(BORDER);
        g2.drawRoundRect(0, 0, pw - 1, ph - 1, 12, 12);

        // Canvas centered inside card
        canvasOriginX = (pw - CANVAS_W) / 2;
        canvasOriginY = (ph - CANVAS_H) / 2;

        // Dotted background
        g2.setColor(SURFACE);
        g2.fillRect(canvasOriginX, canvasOriginY, CANVAS_W, CANVAS_H);
        g2.setColor(new Color(55, 60, 88));
        for (int dx = 0; dx < CANVAS_W; dx += 20)
            for (int dy = 0; dy < CANVAS_H; dy += 20)
                g2.fillOval(canvasOriginX + dx, canvasOriginY + dy, 2, 2);

        g2.drawImage(canvas, canvasOriginX, canvasOriginY, null);
        g2.setColor(BORDER);
        g2.drawRect(canvasOriginX - 1, canvasOriginY - 1, CANVAS_W + 1, CANVAS_H + 1);

        // "Artist Drawing" badge
        if (!imDrawing && !drawerName.isEmpty()) {
            int bx = canvasOriginX + 10, by = canvasOriginY + 10;
            g2.setColor(new Color(15, 16, 22, 200));
            g2.fillRoundRect(bx, by, 148, 26, 20, 20);
            g2.setColor(SUCCESS);
            g2.fillOval(bx + 8, by + 8, 10, 10);
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.setColor(TEXT_PRI);
            g2.drawString(drawerName + " Drawing…", bx + 24, by + 18);
        }

        if (gameOver) paintGameOver(g2, pw, ph);
    }

    private void paintGameOver(Graphics2D g, int pw, int ph) {
        g.setColor(new Color(10, 10, 20, 215));
        g.fillRoundRect(0, 0, pw, ph, 12, 12);
        int cw = 360, ch = 110, cx = (pw - cw) / 2, cy = (ph - ch) / 2;
        g.setColor(CARD);
        g.fillRoundRect(cx, cy, cw, ch, 14, 14);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(cx, cy, cw, ch, 14, 14);
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(GOLD);
        FontMetrics fs = g.getFontMetrics();
        g.drawString("🏆  GAME OVER", cx + (cw - fs.stringWidth("🏆  GAME OVER")) / 2, cy + 30);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(TEXT_PRI);
        FontMetrics fb = g.getFontMetrics();
        g.drawString(gameOverMsg, cx + (cw - fb.stringWidth(gameOverMsg)) / 2, cy + 68);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MOUSE
    // ═══════════════════════════════════════════════════════════════════════════
    private void onCanvasPress(MouseEvent e) {
        int mx = e.getX(), my = e.getY();
        if (imDrawing && mx >= canvasOriginX && mx < canvasOriginX + CANVAS_W
                      && my >= canvasOriginY && my < canvasOriginY + CANVAS_H) {
            mouseDown = true;
            prevMX = mx - canvasOriginX;
            prevMY = my - canvasOriginY;
        }
    }

    private void onCanvasDrag(MouseEvent e) {
        if (!imDrawing || !mouseDown) return;
        int cx = e.getX() - canvasOriginX, cy = e.getY() - canvasOriginY;
        if (cx < 0 || cy < 0 || cx >= CANVAS_W || cy >= CANVAS_H) return;
        if (prevMX >= 0) {
            Color c = eraserMode ? Color.WHITE : drawColor;
            int   s = eraserMode ? drawSize * 2 : drawSize;
            drawLine(prevMX, prevMY, cx, cy, c, s);
            if (!eraserMode)
                send(CMD_DRAW + " " + prevMX + " " + prevMY + " " + cx + " " + cy
                     + " " + c.getRed() + " " + c.getGreen() + " " + c.getBlue() + " " + s);
        }
        prevMX = cx; prevMY = cy;
        repaint();
    }

    private void onToolbarClick(MouseEvent e, int tw) {
        if (!imDrawing) return;
        int mx = e.getX(), my = e.getY();
        int mcy = TOOLBAR_H / 2, bh = 34, bw = 36, by = mcy - bh / 2, bx = 12;

        // Brush
        if (mx >= bx && mx <= bx + bw && my >= by && my <= by + bh) { eraserMode = false; repaint(); return; }
        // Eraser
        int erX = bx + bw + 6;
        if (mx >= erX && mx <= erX + bw && my >= by && my <= by + bh) { eraserMode = true; repaint(); return; }

        int d1 = bx + bw * 2 + 20;
        int[] sizes = {4, 8, 14, 22};
        int szX = d1 + 14;
        for (int i = 0; i < sizes.length; i++) {
            int cx = szX + i * 46 + 22;
            int r  = sizes[i] / 2 + 6;
            if (Math.abs(mx - cx) <= r && Math.abs(my - mcy) <= r) { drawSize = sizes[i]; repaint(); return; }
        }

        int d2 = szX + sizes.length * 46 + 6;
        int sw = 26, swX = d2 + 12;
        for (int i = 0; i < PALETTE.length; i++) {
            int px = swX + i * (sw + 6) + sw / 2;
            if (Math.abs(mx - px) <= sw / 2 + 2 && Math.abs(my - mcy) <= sw / 2 + 2) {
                drawColor = PALETTE[i]; eraserMode = false; repaint(); return;
            }
        }

        // Trash
        int trX = tw - 52, trY = mcy - bh / 2;
        if (mx >= trX && mx <= trX + 40 && my >= trY && my <= trY + bh) {
            clearCanvas(); send(CMD_CLEAR); repaint();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  DRAW / CANVAS HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    private void drawLine(int x1, int y1, int x2, int y2, Color c, int size) {
        canvasG.setColor(c);
        canvasG.setStroke(new BasicStroke(size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        canvasG.drawLine(x1, y1, x2, y2);
    }

    private void clearCanvas() {
        canvasG.setColor(Color.WHITE);
        canvasG.fillRect(0, 0, CANVAS_W, CANVAS_H);
    }

    private void drawPill(Graphics2D g, int x, int y, int w, int h, Color fill, Color stroke) {
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(stroke);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, w, h, h, h);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CHAT
    // ═══════════════════════════════════════════════════════════════════════════
    private void submitGuess() {
        String text = guessField.getText().trim();
        guessField.setText("");
        if (text.isEmpty()) return;
        send(CMD_GUESS + " " + text);
        appendChat(myName, text, ACCENT, TEXT_SEC, false);
    }

    private void appendChat(String sender, String message, Color nameColor, Color msgColor, boolean bold) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = chatPane.getStyledDocument();
            try {
                if (sender != null && !sender.isEmpty()) {
                    Style ns = chatPane.addStyle("n", null);
                    StyleConstants.setForeground(ns, nameColor);
                    StyleConstants.setBold(ns, true);
                    StyleConstants.setFontSize(ns, 13);
                    doc.insertString(doc.getLength(), sender + "\n", ns);
                }
                Style ms = chatPane.addStyle("m", null);
                StyleConstants.setForeground(ms, msgColor);
                StyleConstants.setBold(ms, bold);
                StyleConstants.setFontSize(ms, 13);
                doc.insertString(doc.getLength(), message + "\n\n", ms);
            } catch (BadLocationException ignored) {}
            chatPane.setCaretPosition(doc.getLength());
        });
    }

    private void appendSystem(String msg, Color color) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = chatPane.getStyledDocument();
            try {
                Style s = chatPane.addStyle("s", null);
                StyleConstants.setForeground(s, color);
                StyleConstants.setBold(s, true);
                StyleConstants.setItalic(s, true);
                StyleConstants.setFontSize(s, 12);
                doc.insertString(doc.getLength(), msg + "\n\n", s);
            } catch (BadLocationException ignored) {}
            chatPane.setCaretPosition(doc.getLength());
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  NETWORK
    // ═══════════════════════════════════════════════════════════════════════════
    private void send(String msg) {
        try {
            byte[] buf = msg.getBytes("UTF-8");
            socket.send(new DatagramPacket(buf, buf.length, InetAddress.getByName(server), PORT));
        } catch (Exception e) { System.err.println("[Client] " + e); }
    }

    @Override
    public void run() {
        while (true) {
            byte[] buf = new byte[1400];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            try { socket.receive(pkt); }
            catch (Exception e) { if (!connected) send(CMD_CONNECT + " " + myName); continue; }
            String raw = new String(buf, 0, pkt.getLength()).trim();
            if (!raw.isEmpty()) { handlePacket(raw); repaint(); }
        }
    }

    private void handlePacket(String raw) {
        String[] tok = raw.split(" ", 5);
        String cmd = tok[0].toUpperCase();
        switch (cmd) {
            case CMD_CONNECTED:
                connected = true;
                appendSystem("Connected as " + myName + ". Waiting for players…", SUCCESS);
                break;
            case CMD_ERROR:
                appendSystem("Error: " + afterCmd(raw, cmd), DANGER); break;
            case CMD_PLAYER_LIST:
                updatePlayerList(tok.length > 1 ? tok[1] : ""); break;
            case CMD_PLAYER_JOIN:
                if (tok.length > 1) addPlayer(tok[1]); break;
            case CMD_PLAYER_LEAVE:
                if (tok.length > 1) removePlayer(tok[1]); break;
            case CMD_SCORES:
                updateScores(tok.length > 1 ? tok[1] : ""); break;
            case CMD_ROUND_START:
                handleRoundStart(tok); break;
            case CMD_YOUR_WORD:
                String w = afterCmd(raw, cmd);
                wordLabel = "Draw: " + w; hintText = "";
                appendSystem(drawerName + " is drawing \"" + w.toUpperCase() + "\"", TEXT_SEC);
                break;
            case CMD_HINT:
                if (!imDrawing) { hintText = afterCmd(raw, cmd); appendSystem("Hint: " + hintText, WARNING); }
                break;
            case CMD_TIMER:
                if (tok.length > 1) try { timerSecs = Integer.parseInt(tok[1]); } catch (Exception ignored) {}
                break;
            case CMD_DRAW_FWD:  handleDrawFwd(afterCmd(raw, cmd)); break;
            case CMD_CLEAR_FWD: clearCanvas(); break;
            case CMD_CHAT:      handleChat(raw, tok); break;
            case CMD_CORRECT:
                if (tok.length >= 3) {
                    boolean me = tok[1].equals(myName);
                    appendSystem("✨ " + tok[1] + " GUESSED THE WORD! ✨", me ? SUCCESS : WARNING);
                    if (me) wordLabel = "You got it!";
                }
                break;
            case CMD_ROUND_END:
                appendSystem("Round over. Word was: " + afterCmd(raw, cmd), TEXT_SEC);
                imDrawing = false; timerSecs = 0; hintText = ""; wordLabel = "Round over…"; break;
            case CMD_GAME_END:
                gameOver    = true;
                gameOverMsg = tok.length >= 3 ? tok[1] + " wins (" + tok[2] + " pts)" : "Game Over!";
                appendSystem("🏆 " + gameOverMsg, GOLD);
                imDrawing = false; timerSecs = 0; break;
        }
    }

    private void handleRoundStart(String[] tok) {
        if (tok.length < 5) return;
        drawerName   = tok[1];
        currentRound = parseInt(tok[2]);
        totalRounds  = parseInt(tok[3]);
        String hint  = tok[4];
        imDrawing    = myName.equals(drawerName);
        hintText     = imDrawing ? "" : hint;
        wordLabel    = imDrawing ? "Draw: (word incoming)" : drawerName + " is drawing…";
        clearCanvas(); timerSecs = ROUND_SECONDS; eraserMode = false;
        appendSystem("— Round " + currentRound + "/" + totalRounds + "  •  " + drawerName + " draws —", ACCENT);
    }

    private void handleDrawFwd(String data) {
        String[] d = data.split(" ");
        if (d.length < 8) return;
        try {
            drawLine(Integer.parseInt(d[0]), Integer.parseInt(d[1]),
                     Integer.parseInt(d[2]), Integer.parseInt(d[3]),
                     new Color(Integer.parseInt(d[4]), Integer.parseInt(d[5]), Integer.parseInt(d[6])),
                     Integer.parseInt(d[7]));
        } catch (Exception ignored) {}
    }

    private void handleChat(String raw, String[] tok) {
        if (tok.length < 3) return;
        String sender = tok[1];
        String msg    = raw.substring(CMD_CHAT.length() + 1 + sender.length() + 1);
        if (!sender.equals(myName))
            appendChat(sender, msg, sender.equals(drawerName) ? GOLD : ACCENT, TEXT_SEC, false);
    }

    private void updatePlayerList(String csv) {
        synchronized (playerList) {
            playerList.clear();
            for (String n : csv.split(",")) if (!n.isBlank()) playerList.add(n.trim());
            if (!playerList.contains(myName)) playerList.add(myName);
        }
    }
    private void addPlayer(String n) {
        synchronized (playerList) { if (!playerList.contains(n)) playerList.add(n); }
        appendSystem("→ " + n + " joined", TEXT_DIM);
    }
    private void removePlayer(String n) {
        synchronized (playerList) { playerList.remove(n); }
        appendSystem("← " + n + " left", TEXT_DIM);
    }
    private void updateScores(String csv) {
        synchronized (playerList) {
            scores.clear();
            for (String pair : csv.split(",")) {
                String[] kv = pair.split(":");
                if (kv.length == 2) try { scores.put(kv[0], Integer.parseInt(kv[1])); } catch (Exception ignored) {}
            }
        }
    }

    private static String afterCmd(String raw, String cmd) {
        return raw.length() > cmd.length() ? raw.substring(cmd.length() + 1) : "";
    }
    private static int parseInt(String v) {
        try { return Integer.parseInt(v); } catch (Exception e) { return 0; } 
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) { System.out.println("Usage: java inkjar.SkribblClient <server> <name>"); System.exit(1); }
        SwingUtilities.invokeLater(() -> {
            try { new SkribblClient(args[0], args[1]); }
            catch (Exception e) { e.printStackTrace(); }
        });
    }
}