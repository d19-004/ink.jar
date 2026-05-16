package inkjar;

import java.net.InetAddress;

public class NetPlayer {
    private final String name;
    private final InetAddress address;
    private final int port;
    private int score = 0;
    private boolean guessedThisRound = false;

    public NetPlayer(String name, InetAddress address, int port) {
        this.name = name;
        this.address = address;
        this.port = port;
    }

    public String getName() { return name; }
    public InetAddress getAddress() { return address; }
    public int getPort() { return port; }
    public int getScore() { return score; }
    public void addScore(int points) { score += points; }
    public boolean hasGuessed() { return guessedThisRound; }
    public void setGuessed(boolean guessed) { guessedThisRound = guessed; }
    public void resetGuessed() { guessedThisRound = false; }
}