package peer;

import java.io.*;
import java.net.Socket;
import java.util.*;

import tracker.TorrentClient;
import util.*;


public class PeerConnection implements Runnable {
    public final int peerIndex;   //for demonstrate purpose only.
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final String infoHash;
    private final String expectedPeerId; //of the one we're trying to connect
    private FileManager fileManager;
    public List<Boolean> peerHave;
    public boolean amChoking = true;
    public boolean amInterested = false;
    public boolean peerChoking = true;
    public boolean peerInterested = false;
    private boolean cancelled=false;
    private TorrentClient.TorrentState parent;
    private int inflightRequests=0;
    private int curBlockIndex=-1;    //differ to getBlockIndex in FileManager because one this one serve for sending pipelined requests
    private int currentPiece=-1;
    private int totalPieceBlock=-1;
    private class DownloadEvent {
        long timestamp;
        long bytes;

        DownloadEvent(long timestamp, long bytes) {
            this.timestamp = timestamp;
            this.bytes = bytes;
        }
    }
    private LinkedList<DownloadEvent> downloadHistory = new LinkedList<>(); // Store download history

    public PeerConnection(int peerIndex, Socket socket, String infoHash, String expectedPeerId, FileManager fileManager, TorrentClient.TorrentState parent) throws IOException {
        this.peerIndex=peerIndex;
        this.socket = socket;
        this.infoHash = infoHash;
        this.expectedPeerId = expectedPeerId;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.fileManager=fileManager;
        peerHave=new ArrayList<>(fileManager.getTotalPieces());
        this.parent=parent;
        //after establishing connection, need to send handshake immediately.
        Handshake handshake = new Handshake(infoHash, expectedPeerId);
        sendHandshake(handshake);
        //send bitfield to calculate rarest first
        sendMessage(Message.bitfieldMessage(fileManager.getBitField()));
    }

    public PeerConnection(int peerIndex, String host, int port, String infoHash, String expectedPeerId, FileManager fileManager, TorrentClient.TorrentState parent) throws IOException {
        this(peerIndex, new Socket(host, port), infoHash, expectedPeerId, fileManager, parent);
        
    }
    public void sendHandshake(Handshake handshake) throws IOException {
        handshake.send(out);
    }

    public void sendMessage(byte[] message) throws IOException {
        out.write(message);
        out.flush();
    }
    public void setChoke() throws IOException{
        sendMessage(Message.chokeMessage());
        amChoking=true;
    }
    public void setUnchoke() throws IOException{
        sendMessage(Message.unchokeMessage());
        amChoking=false;
    }
    public void sendInterested() throws IOException {
        sendMessage(Message.interestedMessage());
        amInterested=true;
    }
    public void sendNotInterested() throws IOException {
        sendMessage(Message.notInterestedMessage());
        amInterested=false;
    }
    public void startSendingRequest(int piece){
        this.currentPiece=piece;
        this.curBlockIndex=fileManager.getBlockIndex(piece);
        this.totalPieceBlock=fileManager.getTotalBlock(piece);
        try {
            sendNextRequest();
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
    private void sendNextRequest() throws IOException {
        while (peerChoking == false && inflightRequests<Conf.MAX_PIPELINE && curBlockIndex<totalPieceBlock){
            ++inflightRequests;
            ++curBlockIndex;
            sendRequest(currentPiece, curBlockIndex);
        }
    }
    public void sendRequest(int pieceIndex, int blockIndex) throws IOException {
        sendMessage(Message.requestMessage(pieceIndex, blockIndex*Conf.BLOCK_LENGTH, fileManager.getBlockLength(pieceIndex, blockIndex)));
    }
    public void updateDownloadedBytes(long bytes) {
        long currentTime = System.currentTimeMillis();
        downloadHistory.add(new DownloadEvent(currentTime, bytes)); // Add download event with timestamp
        // Remove events that are older than timeWindow
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
        // Sum up all the bytes downloaded within the last timeWindow
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

                if (messageId==PeerProtocol.CHOKE){
                    peerChoking=true;
                    parent.setStatus(currentPiece, 0);  //choke halfway, set status of piece to 0 so that it can be assigned to new peer
                }
                else if (messageId== PeerProtocol.UNCHOKE){ 
                    peerChoking=false;
                    parent.assignPiece(this);
                }
                else if (messageId == PeerProtocol.INTERESTED) peerInterested=true;
                else if (messageId == PeerProtocol.NOT_INTERESTED) peerInterested=false;
                else if (messageId == PeerProtocol.HAVE) {
                    int pieceIndex=in.readInt();
                    peerHave.set(pieceIndex,true);
                    parent.updateCounter(pieceIndex);
                    if (parent.getStatus(pieceIndex)==0) sendInterested();
                }
                else if (messageId == PeerProtocol.BITFIELD){
                    byte[] bitfield = new byte[length - 1];
                    in.readFully(bitfield);
                    boolean flagInteresed=false;
                    for (int i = 0; i < peerHave.size(); i++) {
                        int byteIndex = i / 8;  // Index of the byte in the bitfield
                        int bitIndex = 7 - (i % 8); // 7 - (i % 8) to prioritize most significant bit
                        boolean bitValue = (bitfield[byteIndex] & (1 << bitIndex)) != 0;
                        peerHave.set(i, bitValue);
                        if (bitValue==true && parent.getStatus(i)==0) flagInteresed=true;
                    }
                    parent.updateCounter(peerHave);
                    if (flagInteresed) sendInterested();
                }
                else if (messageId == PeerProtocol.REQUEST){
                    int index=in.readInt();
                    int begin=in.readInt();
                    int blockLength=in.readInt();
                    if (amChoking || !fileManager.havePiece.get(index) || cancelled) continue;
                    else {
                        int end=begin+blockLength;
                        byte[] block=new byte[blockLength];
                        for (int i=begin; i<end; i+=256){  
                            if (cancelled) break;
                            byte[] get=fileManager.getPieceBlock(index, i, 256);
                            System.arraycopy(block, i-begin, get, 0, 256);
                        }
                        if (cancelled) continue;
                        else{
                            sendMessage(Message.pieceMessage(index, begin, block));
                            parent.increaseUpload(block.length);
                        } 
                    }
                }
                else if (messageId == PeerProtocol.PIECE){
                    int index=in.readInt();
                    int begin=in.readInt();
                    byte[] block= new byte[length-9];
                    in.readFully(block);
                    fileManager.writeBlock(index, begin, block);
                    inflightRequests--;
                    parent.increaseDownload(block.length);
                    int blockIndex=begin/Conf.BLOCK_LENGTH;
                    if (blockIndex==totalPieceBlock-1){
                        if (fileManager.validate(index)) {
                            parent.setStatus(index, 1);
                            parent.assignPiece(this);
                        }
                        else {
                            curBlockIndex=0;
                            sendNextRequest();
                        }
                    }
                    else sendNextRequest();


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
    public void dropConnection(){
        //to do: drop socket connection, given member Socket socket;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        } 
    
    }  
}
