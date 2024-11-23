package peer;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class Handshake {
    private final byte pstrlen;
    private final String pstr = PeerProtocol.PROTOCOL_STRING;
    private final byte[] reserved = new byte[8];
    private final String infoHash; // 20-byte SHA1 hash
    private final String peerId; // 20-byte unique client ID

    public Handshake(String infoHash, String peerId) {
        this.pstrlen = (byte) pstr.length();
        this.infoHash = infoHash;
        this.peerId = peerId;
    }

    public byte[] getBytes() throws UnsupportedEncodingException {
        ByteBuffer buffer = ByteBuffer.allocate(PeerProtocol.HANDSHAKE_LENGTH);
        buffer.put(pstrlen);
        buffer.put(pstr.getBytes("ISO-8859-1"));
        buffer.put(reserved);
        buffer.put(infoHash.getBytes("ISO-8859-1"));
        buffer.put(peerId.getBytes("ISO-8859-1"));
        return buffer.array();
    }

    public void send(DataOutputStream out) throws IOException {
        out.write(getBytes());
        out.flush();
    }
    public String getPeerId(){
        return peerId;
    }
    public String getInfoHash(){
        return infoHash;
    }
    public static Handshake receive(DataInputStream in) throws IOException, UnsupportedEncodingException {
        byte pstrlen = in.readByte();
        byte[] pstrBytes = new byte[pstrlen];
        in.readFully(pstrBytes);
        //String pstr = new String(pstrBytes, StandardCharsets.UTF_8);
        byte[] reserved = new byte[8];
        in.readFully(reserved);
        byte[] infoHashByte = new byte[20];
        in.readFully(infoHashByte);
        String infoHash = new String(infoHashByte, "ISO-8859-1");
        byte[] peerIdBytes = new byte[20];
        in.readFully(peerIdBytes);
        String peerId = new String(peerIdBytes, "ISO-8859-1");

        return new Handshake(infoHash, peerId);
    }
}
