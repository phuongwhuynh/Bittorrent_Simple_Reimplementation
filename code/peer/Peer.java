package peer;

// import com.sun.net.httpserver.*;
// import java.net.*;
// import java.io.*;
// import java.util.*;
import java.nio.charset.StandardCharsets;
public class Peer {
    private String peerId;
    private String ip;
    private int port;
    private long uploaded;
    private long downloaded;
    private long left;

    public Peer(String peerId, String ip, int port, long uploaded, long downloaded, long left) {
        this.peerId = peerId;
        this.ip = ip;
        this.port = port;
        this.uploaded = uploaded;
        this.downloaded = downloaded;
        this.left = left;
    }

    // Generates a compact format for peer (6 bytes: 4 bytes for IP, 2 bytes for port)
    public String toCompactFormat() {
        String[] ipParts = ip.split("\\.");
        byte[] compact = new byte[6];
        for (int i = 0; i < 4; i++) {
            compact[i] = (byte) Integer.parseInt(ipParts[i]);
        }
        compact[4] = (byte) (port >> 8);
        compact[5] = (byte) (port & 0xFF);
        return new String(compact, StandardCharsets.ISO_8859_1);
    }
    public String getPeerId(){
        return peerId;
    }
    public String getIP(){
        return ip;
    }
    public int getPort(){
        return port;
    }
    public long getUploaded(){
        return uploaded;
    }
    public long downloaded(){
        return downloaded;
    }
    public long getLeft(){
        return left;
    }
}