package client;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.swing.*;

/**
 * Drawing canvas + colour toolbar.
 * All network calls have been removed; the board simply draws locally.
 */
public class Board extends JPanel {

    private Color currentColor = Color.BLACK;
    private final int strokeSize = 10;

    private final Canvas canvas;
    private final JLabel wordLabel;

    /** True when it is the player's turn to draw. */
    private boolean isDrawing = false;

    public Board(int WIDTH, int HEIGHT) {
        canvas = new Canvas((int) (WIDTH * 0.9), (int) (HEIGHT * 0.7));

        JPanel drawingMenu = new JPanel(new FlowLayout());
        drawingMenu.setPreferredSize(new Dimension((int) (WIDTH * 0.9), (int) (HEIGHT * 0.2)));

        for (Color c : new Color[]{Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.PINK,
                Color.BLACK, Color.WHITE, Color.YELLOW, Color.MAGENTA, Color.CYAN}) {
            drawingMenu.add(colorButton(c));
        }

        // Size buttons
        for (int s : new int[]{4, 8, 14, 22}) {
            int sz = s;
            JButton sizeBtn = new JButton(s + "px");
            sizeBtn.addActionListener(e -> canvas.setStrokeSize(sz));
            sizeBtn.setPreferredSize(new Dimension(55, 40));
            drawingMenu.add(sizeBtn);
        }

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> canvas.clear());
        drawingMenu.add(clearButton);

        canvas.setBorder(BorderFactory.createLineBorder(Color.BLACK, 4));

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        add(canvas);
        add(drawingMenu);

        wordLabel = new JLabel("Waiting for game to start…");
        Map<TextAttribute, Object> attrs = new HashMap<>();
        attrs.put(TextAttribute.TRACKING, 0.5);
        wordLabel.setFont(getFont().deriveFont(20.0f).deriveFont(attrs));
        wordLabel.setAlignmentX(CENTER_ALIGNMENT);
        add(wordLabel);
    }

    // ---- public API --------------------------------------------------------

    public boolean isDrawing()                { return isDrawing; }
    public void   setDrawing(boolean drawing) { this.isDrawing = drawing; }
    public Canvas getCanvas()                 { return canvas; }

    /** Show the current word (drawer) or a hint pattern (guesser). */
    public void setWordLabel(String text) {
        SwingUtilities.invokeLater(() -> {
            wordLabel.setText(text);
            wordLabel.revalidate();
            wordLabel.repaint();
        });
    }

    // ---- helpers -----------------------------------------------------------

    private JButton colorButton(Color c) {
        JButton btn = new JButton();
        btn.setBackground(c);
        btn.setPreferredSize(new Dimension(40, 40));
        btn.addActionListener(e -> {
            currentColor = c;
            canvas.setColor(currentColor);
        });
        return btn;
    }

    // ========================================================================
    // Canvas inner class
    // ========================================================================
    public class Canvas extends JPanel {

        private int prevMouseX, prevMouseY;
        private BufferedImage image;
        private final ArrayList<Line> drawings = new ArrayList<>();
        private int strokeSz = 10;
        private Color drawColor = Color.BLACK;

        private Canvas(int W, int H) {
            setSize(W, H);
            setPreferredSize(new Dimension(W, H));

            image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            whiteOut(image);

            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseDragged(MouseEvent e) { drag(e); }
                @Override public void mousePressed(MouseEvent e) {
                    prevMouseX = e.getX(); prevMouseY = e.getY();
                }
                @Override public void mouseMoved(MouseEvent e) { /* no-op */ }
                @Override public void mouseReleased(MouseEvent e) { /* no-op */ }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private void drag(MouseEvent e) {
            if (!isDrawing) return;
            int cx = e.getX(), cy = e.getY();
            drawings.add(new Line(prevMouseX, prevMouseY, cx, cy, drawColor, strokeSz));
            prevMouseX = cx; prevMouseY = cy;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            // Lazy resize
            if (image.getWidth() != getWidth() || image.getHeight() != getHeight()) {
                BufferedImage resized = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
                whiteOut(resized);
                image = resized;
                drawings.clear();
            }

            // Render all strokes onto image
            Graphics2D g2d = image.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (Line l : drawings) {
                g2d.setStroke(new BasicStroke(l.size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2d.setColor(l.color);
                g2d.drawLine(l.x1, l.y1, l.x2, l.y2);
            }
            g2d.dispose();

            g.drawImage(image, 0, 0, null);
            drawings.clear(); // already baked in
        }

        public void clear() {
            drawings.clear();
            whiteOut(image);
            repaint();
        }

        public void setColor(Color c)     { this.drawColor = c; }
        public void setStrokeSize(int sz) { this.strokeSz = sz; }

        private void whiteOut(BufferedImage img) {
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.dispose();
        }
    }

    // ========================================================================
    // Line record
    // ========================================================================
    private static class Line {
        final int x1, y1, x2, y2, size;
        final Color color;
        Line(int x1, int y1, int x2, int y2, Color c, int sz) {
            this.x1=x1; this.y1=y1; this.x2=x2; this.y2=y2; color=c; size=sz;
        }
    }
}
