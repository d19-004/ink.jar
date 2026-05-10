package client;

import java.awt.*;
import javax.swing.*;

/**
 * Main game window – pure single-player, zero networking.
 *
 * Layout:
 *   WEST  – scoreboard / info panel
 *   CENTER – drawing Board
 *   EAST  – chat / guess panel
 */
public class Game {

    // ---- window constants --------------------------------------------------
    private static final double ASPECT = 16.0 / 9.0;
    private static final int    HEIGHT = 720;
    private static final int    WIDTH  = (int) (HEIGHT * ASPECT);

    private static final int DEFAULT_ROUNDS = 5;

    // ---- swing state -------------------------------------------------------
    private final JFrame frame;
    private final String playerName;

    // panels
    private JPanel infoPanel;
    private JTextArea chatArea;
    private JTextField guessField;
    private JLabel timerLabel;
    private JLabel scoreLabel;
    private JLabel roundLabel;
    private Board board;

    // engine
    private final GameEngine engine;

    // ========================================================================
    public Game(String playerName) {
        this.playerName = playerName;
        this.engine     = new GameEngine(playerName, DEFAULT_ROUNDS);

        frame = new JFrame("ink.jar  |  " + playerName);
        frame.setSize(WIDTH, HEIGHT);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setLayout(new BorderLayout(6, 6));
        frame.add(buildInfoPanel(),  BorderLayout.WEST);
        frame.add(buildBoardPanel(), BorderLayout.CENTER);
        frame.add(buildChatPanel(),  BorderLayout.EAST);

        wireEngine();

        frame.setVisible(true);

        // Kick off the first round
        SwingUtilities.invokeLater(engine::startRound);
    }

    // ---- panel builders ----------------------------------------------------

    private JPanel buildInfoPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(WIDTH / 6, HEIGHT));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));

        JLabel title = new JLabel("ink.jar");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel playerLabel = new JLabel("Player: " + playerName);
        playerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        scoreLabel = new JLabel("Score: 0");
        scoreLabel.setFont(scoreLabel.getFont().deriveFont(Font.BOLD, 16f));
        scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        roundLabel = new JLabel("Round: –");
        roundLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerLabel = new JLabel("Time: –");
        timerLabel.setFont(timerLabel.getFont().deriveFont(Font.BOLD, 18f));
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton skipBtn = new JButton("Skip Word");
        skipBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        skipBtn.setMaximumSize(new Dimension(130, 36));
        skipBtn.addActionListener(e -> engine.skipRound());

        panel.add(title);
        panel.add(Box.createVerticalStrut(12));
        panel.add(playerLabel);
        panel.add(Box.createVerticalStrut(8));
        panel.add(scoreLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(roundLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(timerLabel);
        panel.add(Box.createVerticalGlue());
        panel.add(skipBtn);
        panel.add(Box.createVerticalStrut(10));

        infoPanel = panel;
        return panel;
    }

    private JPanel buildBoardPanel() {
        board = new Board(WIDTH * 2 / 3, HEIGHT * 8 / 10);
        board.setDrawing(true); // single-player: always drawing

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(board, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildChatPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension((int)(WIDTH / 4.5), HEIGHT));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 8));

        JLabel chatTitle = new JLabel("Guess the word");
        chatTitle.setFont(chatTitle.getFont().deriveFont(Font.BOLD, 14f));
        chatTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setPreferredSize(new Dimension((int)(WIDTH / 4.5) - 12, HEIGHT * 7 / 10));
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        guessField = new JTextField();
        guessField.setAlignmentX(Component.LEFT_ALIGNMENT);
        guessField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        guessField.addActionListener(e -> submitGuess());

        JButton sendBtn = new JButton("Guess");
        sendBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        sendBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        sendBtn.addActionListener(e -> submitGuess());

        panel.add(chatTitle);
        panel.add(Box.createVerticalStrut(4));
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(6));
        panel.add(new JLabel("Type your guess:"));
        panel.add(guessField);
        panel.add(Box.createVerticalStrut(4));
        panel.add(sendBtn);

        return panel;
    }

    // ---- engine wiring -----------------------------------------------------

    private void wireEngine() {
        engine.setOnRoundStart((word, hint) -> {
            board.setWordLabel("Draw: " + word);
            board.getCanvas().clear();
            roundLabel.setText("Round: " + engine.getRoundNumber() + "/" + engine.getTotalRounds());
            timerLabel.setForeground(Color.BLACK);
        });

        engine.setOnTick(seconds -> {
            timerLabel.setText("Time: " + seconds + "s");
            if (seconds <= 10) timerLabel.setForeground(Color.RED);
            else               timerLabel.setForeground(Color.BLACK);
        });

        engine.setOnMessage(msg -> {
            chatArea.append(msg + "\n");
            // auto-scroll to bottom
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });

        engine.setOnHint(hint -> board.setWordLabel("Hint: " + hint));

        engine.setOnGameEnd((finalScore, rounds) -> {
            board.setWordLabel("Game Over!");
            timerLabel.setText("Done");
            int option = JOptionPane.showOptionDialog(
                    frame,
                    "Game over!\nYou scored " + finalScore + " points over " + rounds + " rounds.\n\nPlay again?",
                    "Game Over",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    new String[]{"Play Again", "Quit"},
                    "Play Again"
            );
            if (option == JOptionPane.YES_OPTION) {
                restartGame();
            } else {
                System.exit(0);
            }
        });
    }

    // ---- actions -----------------------------------------------------------

    private void submitGuess() {
        String text = guessField.getText().trim();
        guessField.setText("");
        if (text.isBlank()) return;
        chatArea.append(playerName + ": " + text + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
        engine.submitGuess(text);
        scoreLabel.setText("Score: " + engine.getScore());
    }

    private void restartGame() {
        frame.dispose();
        new Game(playerName);
    }
}
