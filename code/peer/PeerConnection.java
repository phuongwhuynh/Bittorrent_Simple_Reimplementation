package peer;

import java.io.*;
import java.net.Socket;
import java.util.*;
import util.*;


public class PeerConnection implements Runnable {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final String infoHash;
    private final String expectedPeerId; //of the one we're trying to connect
    private List<Boolean> peerHave;
    private FileManager fileManager;
    public boolean amChoking = true;
    public boolean amInterested = false;
    public boolean peerChoking = true;
    public boolean peerInterested = false;
    private boolean cancelled=false;
    private class DownloadEvent {
        long timestamp;
        long bytes;

        DownloadEvent(long timestamp, long bytes) {
            this.timestamp = timestamp;
            this.bytes = bytes;
        }
    }
    private LinkedList<DownloadEvent> downloadHistory = new LinkedList<>(); // Store download history

    public PeerConnection(Socket socket, String infoHash, String expectedPeerId, FileManager fileManager) throws IOException {
        this.socket = socket;
        this.infoHash = infoHash;
        this.expectedPeerId = expectedPeerId;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.fileManager=fileManager;
        peerHave=new ArrayList<>(fileManager.getTotalPieces());
        //after establishing connection, need to send handshake immediately.
        Handshake handshake = new Handshake(infoHash, expectedPeerId);
        sendHandshake(handshake);
        //send bitfield to calculate rarest first
        sendMessage(Message.bitfieldMessage(fileManager.getBitField()));
    }

    public PeerConnection(String host, int port, String infoHash, String expectedPeerId, FileManager fileManager) throws IOException {
        this(new Socket(host, port), infoHash, expectedPeerId, fileManager);
        
    }

    public void sendHandshake(Handshake handshake) throws IOException {
        handshake.send(out);
    }

    public void sendMessage(byte[] message) throws IOException {
        out.write(message);
        out.flush();
    }
    public void updateDownloadedBytes(long bytes) {
        long currentTime = System.currentTimeMillis();
        downloadHistory.add(new DownloadEvent(currentTime, bytes)); // Add download event with timestamp

        // Remove events that are older than 1 minutes
        while (!downloadHistory.isEmpty() && (currentTime - downloadHistory.get(0).timestamp) > Conf.timeWindow) {
            downloadHistory.remove(0);
        }
    }
    public double calculateDownloadSpeed() {
        long totalBytesDownloaded = 0;
        long currentTime=System.currentTimeMillis();
        while (!downloadHistory.isEmpty() && (currentTime - downloadHistory.get(0).timestamp) > Conf.timeWindow) {
            downloadHistory.remove(0);
        }
        // Sum up all the bytes downloaded within the last 5 minutes
        for (DownloadEvent event : downloadHistory) {
            totalBytesDownloaded += event.bytes;
        }

        // Calculate speed: bytes downloaded / elapsed time in seconds
        long timeDifferenceInSeconds = (currentTime - (downloadHistory.isEmpty() ? currentTime : downloadHistory.get(0).timestamp)) / 1000;
        if (timeDifferenceInSeconds == 0) {
            return 0;
        }

        double downloadSpeed = (double) totalBytesDownloaded / timeDifferenceInSeconds;  // speed in bytes per second
        return downloadSpeed;
    }

    public void run() {
        try{

            Handshake receivedHandshake = Handshake.receive(in);
            System.out.println("Received handshake from peer: " + receivedHandshake.getPeerId());
            if (!receivedHandshake.getInfoHash().equals(infoHash)) {
                System.out.println("infoHash mismatch. Closing connection");
                socket.close();
                return;
            }
            if (!receivedHandshake.getPeerId().equals(expectedPeerId)) {
                System.out.println("Peer ID mismatch. Closing connection.");
                socket.close();
                return;
            }
            
            while (!socket.isClosed()){
                int length = in.readInt();
                if (length == 0) continue; // keep-alive
                byte messageId = in.readByte();

                if (messageId==PeerProtocol.CHOKE) amChoking=true;
                else if (messageId== PeerProtocol.UNCHOKE) amChoking=false;
                else if (messageId == PeerProtocol.INTERESTED) peerInterested=true;
                else if (messageId == PeerProtocol.NOT_INTERESTED) peerInterested=false;
                else if (messageId == PeerProtocol.HAVE) {
                    int pieceIndex=in.readInt();
                    peerHave.set(pieceIndex,true);
                }
                else if (messageId == PeerProtocol.BITFIELD){
                    byte[] bitfield = new byte[length - 1];
                    in.readFully(bitfield);
                    for (int i = 0; i < peerHave.size(); i++) {
                        int byteIndex = i / 8;  // Index of the byte in the bitfield
                        int bitIndex = 7 - (i % 8); // 7 - (i % 8) to prioritize most significant bit
                        boolean bitValue = (bitfield[byteIndex] & (1 << bitIndex)) != 0;
                        peerHave.set(i, bitValue);
                    }
                }
                else if (messageId == PeerProtocol.REQUEST){
                    int index=in.readInt();
                    int begin=in.readInt();
                    int blockLength=in.readInt();
                    if (peerChoking || !fileManager.havePiece.get(index) || cancelled) continue;
                    else {
                        int end=begin+blockLength;
                        byte[] block=new byte[blockLength];
                        for (int i=begin; i<end; i+=1024){  
                            if (cancelled) break;
                            byte[] get=fileManager.getPieceBlock(index, i, 1024);
                            System.arraycopy(block, i-begin, get, 0, 1024);
                        }
                        if (cancelled) continue;
                        else sendMessage(Message.pieceMessage(index, begin, block));
                    }
                }
                else if (messageId == PeerProtocol.PIECE){
                    int index=in.readInt();
                    int begin=in.readInt();
                    byte[] block= new byte[length-9];
                    in.readFully(block);
                    fileManager.writeBlock(index, begin, block);
                }
                else if (messageId == PeerProtocol.CANCEL){
                    cancelled=true;
                    // stop transfering current block
                }
            }

        }  
        catch (IOException e){
            e.printStackTrace();
        }
    }   
}
