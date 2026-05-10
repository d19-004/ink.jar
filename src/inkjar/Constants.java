package client;

public interface Constants {
    int PORT = 9876;
    int ROUND_SECONDS = 60;
    int HINT_SECONDS = 30;
    int MIN_PLAYERS = 2;

    String CMD_CONNECT = "CONNECT";
    String CMD_GUESS = "GUESS";
    String CMD_DRAW = "DRAW";
    String CMD_CLEAR = "CLEAR";
    String CMD_START = "START";
    String CMD_PING = "PING";

    String CMD_CONNECTED = "CONNECTED";
    String CMD_PLAYER_LIST = "PLAYER_LIST";
    String CMD_PLAYER_JOIN = "PLAYER_JOIN";
    String CMD_PLAYER_LEAVE = "PLAYER_LEAVE";
    String CMD_ROUND_START = "ROUND_START";
    String CMD_YOUR_WORD = "YOUR_WORD";
    String CMD_HINT = "HINT";
    String CMD_TIMER = "TIMER";
    String CMD_DRAW_FWD = "DRAW_FWD";
    String CMD_CLEAR_FWD = "CLEAR_FWD";
    String CMD_CHAT = "CHAT";
    String CMD_CORRECT = "CORRECT";
    String CMD_ROUND_END = "ROUND_END";
    String CMD_SCORES = "SCORES";
    String CMD_GAME_END = "GAME_END";
    String CMD_PONG = "PONG";
    String CMD_ERROR = "ERROR";
}
