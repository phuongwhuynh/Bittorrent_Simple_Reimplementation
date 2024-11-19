package peer;

public class PeerProtocol {
    public static final int KEEP_ALIVE = -1;
    public static final int CHOKE = 0;
    public static final int UNCHOKE = 1;
    public static final int INTERESTED = 2;
    public static final int NOT_INTERESTED = 3;
    public static final int HAVE = 4;
    public static final int BITFIELD = 5;
    public static final int REQUEST = 6;
    public static final int PIECE = 7;
    public static final int CANCEL = 8;
    public static final int PORT = 9;
    public static final int MAX_REQUEST_SIZE = 16384;
    public static final String PROTOCOL_STRING = "BitTorrent protocol";
    public static final int HANDSHAKE_LENGTH = 68; //49+PROTOCOL_STRING.length()
}
