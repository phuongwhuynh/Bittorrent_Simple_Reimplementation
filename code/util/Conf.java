package util;

public class Conf {
    public static int pieceLength=262144;   //256KB
    public static final String announce="http://localhost:8080/announce";
    public static final int SERVER_PORT=8080;
    public static final int maxPeers=30;
    public static final int timeWindow=60*1000; //only count last 1 minute to calculate download speed

    public static final int MAX_PIPELINE=5;
    public static final int UNCHOKE_INTERVAL = 10000;  // 10 seconds
    public static final int MAX_UNCHOKED_PEERS = 2;    // Number of peers to unchoke
    public static final int OPTIMISTIC_UNCHOKE_INTERVAL = 30000; // 30 seconds

    public static final int BLOCK_LENGTH=16384;    //16KB
}
