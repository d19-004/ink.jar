package inkjar;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-local game engine – no sockets, no threads waiting on I/O.
 *
 * Responsibilities (previously split between NetworkHandler and server):
 *  - Maintain the word list
 *  - Choose the secret word each round
 *  - Broadcast word hints to the "guesser" view
 *  - Validate guesses
 *  - Track scores
 *  - Manage round / game lifecycle
 */
public class GameEngine {

    // ---- configuration -----------------------------------------------------
    public static final int ROUND_SECONDS    = 60;
    public static final int HINT_AT_SECONDS  = 30; // seconds remaining when hint appears

    // ---- state -------------------------------------------------------------
    private final String playerName;
    private final List<String> wordPool;
    private final List<String> usedWords = new ArrayList<>();

    private String currentWord  = "";
    private String currentHint  = "";   // e.g. "_ p p _ e" for "apple"
    private String partialHint  = "";   // hint with one letter revealed

    private int score           = 0;
    private int roundNumber     = 0;
    private int totalRounds;

    private boolean roundActive = false;
    private long roundStartMs   = 0;

    private Thread timerThread;

    // ---- callbacks to update the UI ----------------------------------------
    /** Called when the new word + hint pattern are ready. */
    @FunctionalInterface public interface OnRoundStart { void run(String word, String blankHint); }
    /** Called each second with remaining seconds. */
    @FunctionalInterface public interface OnTick       { void run(int secondsLeft); }
    /** Called with a status message (correct guess, time up, etc.). */
    @FunctionalInterface public interface OnMessage    { void run(String msg); }
    /** Called when the game ends. */
    @FunctionalInterface public interface OnGameEnd    { void run(int finalScore, int rounds); }
    /** Called when a partial hint should be shown. */
    @FunctionalInterface public interface OnHint       { void run(String hint); }

    private OnRoundStart onRoundStart;
    private OnTick       onTick;
    private OnMessage    onMessage;
    private OnGameEnd    onGameEnd;
    private OnHint       onHint;

    // ---- constructor -------------------------------------------------------

    public GameEngine(String playerName, int totalRounds) {
        this.playerName  = playerName;
        this.totalRounds = totalRounds;
        this.wordPool    = WordList.getWordList();
    }

    // ---- callback setters --------------------------------------------------

    public void setOnRoundStart(OnRoundStart cb) { this.onRoundStart = cb; }
    public void setOnTick(OnTick cb)             { this.onTick = cb; }
    public void setOnMessage(OnMessage cb)       { this.onMessage = cb; }
    public void setOnGameEnd(OnGameEnd cb)       { this.onGameEnd = cb; }
    public void setOnHint(OnHint cb)             { this.onHint = cb; }

    // ---- public API --------------------------------------------------------

    /** Start a new round. Safe to call from the EDT. */
    public synchronized void startRound() {
        if (roundActive) return;
        if (roundNumber >= totalRounds) {
            endGame();
            return;
        }

        roundNumber++;
        currentWord = WordList.randomWord(wordPool, usedWords);
        usedWords.add(currentWord);
        currentHint = buildBlankHint(currentWord);
        partialHint = buildPartialHint(currentWord);
        roundActive = true;
        roundStartMs = System.currentTimeMillis();

        if (onRoundStart != null) onRoundStart.run(currentWord, currentHint);
        if (onMessage != null)
            onMessage.run("Round " + roundNumber + "/" + totalRounds + " – Draw the word and type your guess below!");

        startTimer();
    }

    /**
     * Submit a guess. Returns {@code true} if the guess is correct.
     * Call from the EDT.
     */
    public synchronized boolean submitGuess(String guess) {
        if (!roundActive) return false;
        if (guess == null || guess.isBlank()) return false;

        if (guess.trim().equalsIgnoreCase(currentWord)) {
            int elapsed = (int)((System.currentTimeMillis() - roundStartMs) / 1000);
            int points  = Math.max(10, ROUND_SECONDS - elapsed); // faster = more points
            score += points;
            if (onMessage != null)
                onMessage.run("CORRECT! The word was \" + currentWord + \"! +" + points + " pts  (total: " + score + ")");
            endRound(true);
            return true;
        } else {
            if (onMessage != null)
                onMessage.run("WRONG: " + playerName + " guessed '" + guess + "' - not quite!");
            return false;
        }
    }

    /** Skip the current round (no points). */
    public synchronized void skipRound() {
        if (!roundActive) return;
        if (onMessage != null)
            onMessage.run("SKIP: The word was '" + currentWord + "'.");
        endRound(false);
    }

    public int getScore()       { return score; }
    public int getRoundNumber() { return roundNumber; }
    public int getTotalRounds() { return totalRounds; }
    public boolean isRoundActive() { return roundActive; }
    public String getCurrentWord() { return currentWord; }

    // ---- private helpers ---------------------------------------------------

    private void startTimer() {
        if (timerThread != null) timerThread.interrupt();

        timerThread = new Thread(() -> {
            boolean hintSent = false;
            for (int remaining = ROUND_SECONDS; remaining >= 0; remaining--) {
                if (Thread.currentThread().isInterrupted()) return;

                final int r = remaining;
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (onTick != null) onTick.run(r);
                });

                // Send partial hint halfway through
                if (!hintSent && remaining <= HINT_AT_SECONDS) {
                    hintSent = true;
                    final String h = partialHint;
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        if (onHint != null) onHint.run(h);
                        if (onMessage != null) onMessage.run("💡 Hint: " + h);
                    });
                }

                if (remaining == 0) break;

                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
            }

            javax.swing.SwingUtilities.invokeLater(() -> {
                synchronized (GameEngine.this) {
                    if (roundActive) {
                        if (onMessage != null)
                            onMessage.run("TIME UP! The word was '" + currentWord + "'.");
                        endRound(false);
                    }
                }
            });
        });
        timerThread.setDaemon(true);
        timerThread.start();
    }

    private void endRound(boolean guessedCorrectly) {
        roundActive = false;
        if (timerThread != null) timerThread.interrupt();

        if (roundNumber >= totalRounds) {
            endGame();
        } else {
            // Short pause then notify UI to start the next round
            new Thread(() -> {
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                javax.swing.SwingUtilities.invokeLater(this::startRound);
            }).start();
        }
    }

    private void endGame() {
        if (onGameEnd != null) onGameEnd.run(score, roundNumber);
    }

    /** Builds "_ p p _ e" style blank hint. */
    private static String buildBlankHint(String word) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            if (word.charAt(i) == ' ') sb.append("  ");
            else { if (sb.length() > 0) sb.append(' '); sb.append('_'); }
        }
        return sb.toString().trim();
    }

    /** Reveals one random (non-space) character from the word. */
    private static String buildPartialHint(String word) {
        // collect indices of letters
        ArrayList<Integer> idxs = new ArrayList<>();
        for (int i = 0; i < word.length(); i++)
            if (word.charAt(i) != ' ') idxs.add(i);

        if (idxs.isEmpty()) return buildBlankHint(word);

        int reveal = idxs.get((int) (Math.random() * idxs.size()));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == ' ')     { sb.append("  "); }
            else if (i == reveal) { if (sb.length() > 0) sb.append(' '); sb.append(c); }
            else              { if (sb.length() > 0) sb.append(' '); sb.append('_'); }
        }
        return sb.toString().trim();
    }
}
