package inkjar;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GameServer implements Runnable, Constants {
    private static final int WAITING = 0;
    private static final int IN_GAME = 1;
    private static final int GAME_OVER = 2;

    private final DatagramSocket serverSocket;
    private final int numPlayers;
    private final Map<String, NetPlayer> players = new LinkedHashMap<>();

    private int gameStage = WAITING;
    private int drawerIndex = 0;
    private int roundNumber = 0;
    private int totalRounds = 0;
    private String currentWord = "";
    private final List<String> usedWords = new ArrayList<>();

    public GameServer(int numPlayers) throws Exception {
        this.numPlayers = numPlayers;
        serverSocket = new DatagramSocket(PORT);
        serverSocket.setSoTimeout(50);
        System.out.println("[Server] Listening on UDP port " + PORT
                + " - waiting for " + numPlayers + " player(s).");
        new Thread(this, "server-main").start();
    }

    private void broadcast(String msg) {
        for (NetPlayer player : players.values()) {
            send(player, msg);
        }
    }

    private void send(NetPlayer player, String msg) {
        try {
            byte[] buf = msg.getBytes("UTF-8");
            DatagramPacket pkt = new DatagramPacket(buf, buf.length, player.getAddress(), player.getPort());
            serverSocket.send(pkt);
        } catch (Exception e) {
            System.err.println("[Server] Send error to " + player.getName() + ": " + e);
        }
    }

    private void broadcastExcept(String msg, String exceptName) {
        for (NetPlayer player : players.values()) {
            if (!player.getName().equals(exceptName)) {
                send(player, msg);
            }
        }
    }

    private String scoresCsv() {
        StringBuilder sb = new StringBuilder();
        for (NetPlayer player : players.values()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(player.getName()).append(':').append(player.getScore());
        }
        return sb.toString();
    }

    private String playerListCsv() {
        return String.join(",", players.keySet());
    }

    private synchronized void startGame() {
        gameStage = IN_GAME;
        drawerIndex = 0;
        roundNumber = 0;
        totalRounds = players.size();
        usedWords.clear();
        for (NetPlayer player : players.values()) {
            player.addScore(-player.getScore());
        }
        broadcast(CMD_SCORES + " " + scoresCsv());
        nextRound();
    }

    private synchronized void nextRound() {
        roundNumber++;
        if (roundNumber > totalRounds) {
            endGame();
            return;
        }

        for (NetPlayer player : players.values()) {
            player.resetGuessed();
        }

        List<String> names = new ArrayList<>(players.keySet());
        String drawerName = names.get(drawerIndex % names.size());
        drawerIndex = (drawerIndex + 1) % names.size();
        currentWord = WordList.pick(usedWords);
        usedWords.add(currentWord);

        String hint = WordList.blankHint(currentWord);
        broadcast(CMD_ROUND_START + " " + drawerName + " " + roundNumber + " " + totalRounds + " " + hint);
        send(players.get(drawerName), CMD_YOUR_WORD + " " + currentWord);

        System.out.println("[Server] Round " + roundNumber + "/" + totalRounds
                + " drawer=" + drawerName + " word=" + currentWord);

        final int roundSnapshot = roundNumber;
        final String wordSnapshot = currentWord;
        new Thread(() -> runRoundTimer(wordSnapshot, roundSnapshot), "round-timer").start();
    }

    private void runRoundTimer(String word, int roundSnapshot) {
        boolean hintSent = false;
        for (int t = ROUND_SECONDS; t >= 0; t--) {
            synchronized (this) {
                if (roundNumber != roundSnapshot || gameStage != IN_GAME) return;
            }

            broadcast(CMD_TIMER + " " + t);

            if (!hintSent && t <= HINT_SECONDS) {
                hintSent = true;
                broadcast(CMD_HINT + " " + WordList.partialHint(word));
            }

            if (t == 0) break;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }

        synchronized (this) {
            if (roundNumber == roundSnapshot && gameStage == IN_GAME) {
                broadcast(CMD_ROUND_END + " " + word);
                sleepQuietly(2000);
                nextRound();
            }
        }
    }

    private synchronized void handleCorrectGuess(NetPlayer guesser, String drawerName) {
        if (guesser.hasGuessed()) return;
        guesser.setGuessed(true);

        int points = Math.max(10, ROUND_SECONDS);
        guesser.addScore(points);
        broadcast(CMD_CORRECT + " " + guesser.getName() + " " + points);
        broadcast(CMD_SCORES + " " + scoresCsv());

        long remaining = players.values().stream()
                .filter(player -> !player.getName().equals(drawerName))
                .filter(player -> !player.hasGuessed())
                .count();

        if (remaining == 0) {
            broadcast(CMD_ROUND_END + " " + currentWord);
            sleepQuietly(1500);
            nextRound();
        }
    }

    private void endGame() {
        gameStage = GAME_OVER;
        NetPlayer winner = null;
        for (NetPlayer player : players.values()) {
            if (winner == null || player.getScore() > winner.getScore()) {
                winner = player;
            }
        }
        String winnerName = winner != null ? winner.getName() : "nobody";
        int winnerScore = winner != null ? winner.getScore() : 0;
        broadcast(CMD_GAME_END + " " + winnerName + " " + winnerScore);
        System.out.println("[Server] Game over. Winner: " + winnerName + " with " + winnerScore + " pts.");
    }

    @Override
    public void run() {
        while (true) {
            byte[] buf = new byte[1400];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);

            try {
                serverSocket.receive(pkt);
            } catch (Exception e) {
                continue;
            }

            String raw = new String(buf, 0, pkt.getLength()).trim();
            if (raw.isEmpty()) continue;

            InetAddress addr = pkt.getAddress();
            int port = pkt.getPort();
            String[] tok = raw.split(" ", 3);
            String cmd = tok[0].toUpperCase();

            if (cmd.equals(CMD_CONNECT)) {
                handleConnect(addr, port, tok);
                continue;
            }

            NetPlayer sender = findPlayer(addr, port);
            if (sender == null) continue;

            switch (cmd) {
                case CMD_PING:
                    send(sender, CMD_PONG);
                    break;
                case CMD_START:
                    if (gameStage == WAITING && players.size() >= MIN_PLAYERS) {
                        startGame();
                    }
                    break;
                case CMD_GUESS:
                    handleGuess(sender, tok);
                    break;
                case CMD_DRAW:
                    if (gameStage == IN_GAME && tok.length >= 2) {
                        String drawData = raw.substring(CMD_DRAW.length() + 1);
                        broadcastExcept(CMD_DRAW_FWD + " " + drawData, sender.getName());
                    }
                    break;
                case CMD_CLEAR:
                    if (gameStage == IN_GAME) {
                        broadcastExcept(CMD_CLEAR_FWD, sender.getName());
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private synchronized void handleConnect(InetAddress addr, int port, String[] tok) {
        if (tok.length < 2) return;
        String name = tok[1].trim();
        if (name.isEmpty() || name.length() > 18) {
            sendRaw(addr, port, CMD_ERROR + " Invalid username");
            return;
        }
        if (gameStage != WAITING) {
            sendRaw(addr, port, CMD_ERROR + " Game already in progress");
            return;
        }
        if (players.containsKey(name)) {
            sendRaw(addr, port, CMD_ERROR + " Name already taken");
            return;
        }

        NetPlayer player = new NetPlayer(name, addr, port);
        players.put(name, player);
        System.out.println("[Server] " + name + " connected (" + players.size() + "/" + numPlayers + ")");

        send(player, CMD_CONNECTED + " " + name);
        send(player, CMD_PLAYER_LIST + " " + playerListCsv());
        broadcastExcept(CMD_PLAYER_JOIN + " " + name, name);
        broadcast(CMD_SCORES + " " + scoresCsv());

        if (players.size() >= numPlayers && gameStage == WAITING) {
            startGame();
        }
    }

    private void handleGuess(NetPlayer sender, String[] tok) {
        if (gameStage != IN_GAME || tok.length < 2) return;
        String guess = tok[1].trim();
        List<String> names = new ArrayList<>(players.keySet());
        String drawerNow = names.get((drawerIndex - 1 + names.size()) % names.size());
        if (sender.getName().equals(drawerNow)) return;

        broadcast(CMD_CHAT + " " + sender.getName() + " " + guess);
        if (guess.equalsIgnoreCase(currentWord)) {
            handleCorrectGuess(sender, drawerNow);
        }
    }

    private NetPlayer findPlayer(InetAddress addr, int port) {
        for (NetPlayer player : players.values()) {
            if (player.getAddress().equals(addr) && player.getPort() == port) {
                return player;
            }
        }
        return null;
    }

    private void sendRaw(InetAddress addr, int port, String msg) {
        try {
            byte[] buf = msg.getBytes("UTF-8");
            serverSocket.send(new DatagramPacket(buf, buf.length, addr, port));
        } catch (Exception ignored) {
        }
    }

    private static void sleepQuietly(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws Exception {
        int players = 2;
        if (args.length >= 1) {
            try {
                players = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Usage: java client.GameServer <numPlayers>");
                System.exit(1);
            }
        }
        new GameServer(players);
        Thread.currentThread().join();
    }
}
