package peer;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

import tracker.TorrentClient;
import util.*;


public class PeerConnection implements Runnable {
    public int peerIndex;   //for demonstrate purpose only.
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final String infoHash;
    public String peerId; //of the one we're trying to connect
    private FileManager fileManager;
    public List<Boolean> peerHave;
    public boolean amChoking = true;
    public boolean amInterested = false;
    public boolean peerChoking = true;
    public boolean peerInterested = false;
    private boolean cancelled=false;
    private TorrentClient.TorrentState parent;
    private int inflightRequests=0;
    private int curBlockIndex=0;    //differ to getBlockIndex in FileManager because one this one serve for sending pipelined requests
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
    private ConcurrentLinkedDeque<DownloadEvent> downloadHistory = new ConcurrentLinkedDeque<>();

    public PeerConnection(Socket socket, String infoHash, FileManager fileManager, TorrentClient.TorrentState parent) throws IOException {
        this.socket = socket;
        this.infoHash = infoHash;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.fileManager=fileManager;
        int totalPieces = fileManager.getTotalPieces();
        peerHave = new ArrayList<>(totalPieces);
        for (int i = 0; i < totalPieces; i++) {
            peerHave.add(false);
        }
        this.parent=parent;
        //after establishing connection, need to send handshake immediately.
        Handshake handshake = new Handshake(infoHash, new String(parent.peerId, "ISO-8859-1"));
        sendHandshake(handshake);
        //send bitfield to calculate rarest first
        sendMessage(Message.bitfieldMessage(fileManager.getBitField()));
    }

    public PeerConnection(int peerIndex, String host, int port, String infoHash, String expectedPeerId, FileManager fileManager, TorrentClient.TorrentState parent) throws IOException {
        this(new Socket(host, port), infoHash, fileManager, parent);
    }
    public void sendHandshake(Handshake handshake) throws IOException {
        handshake.send(out);
    }

    public synchronized void sendMessage(byte[] message) throws IOException {
        // out.write(message);
        // out.flush();
        try {
            out.write(message);
            out.flush();
        } catch (SocketException e) {
            System.err.println("Peer " + peerIndex+ ": Connection aborted");
            dropConnection();  // Close the connection cleanly
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    public void setChoke() throws IOException{
        //System.out.println("Choke peer " + peerIndex);
        sendMessage(Message.chokeMessage());
        amChoking=true;
    }
    public void setUnchoke() throws IOException{
        System.out.println("Unchoke peer " + peerIndex);
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
    public void sendHave(int pieceIndex) throws IOException {
        sendMessage(Message.haveMessage(pieceIndex));
    }
    public void startSendingRequest(int piece) throws IOException{
        //System.out.println("Assign piece " + piece +" to peer " + peerIndex);
        this.currentPiece=piece;
        this.curBlockIndex=fileManager.getBlockIndex(piece);
        if (curBlockIndex==-1){
            parent.assignPiece(this);
            return;
        }
        this.totalPieceBlock=fileManager.getTotalBlock(piece);
        try {
            sendNextRequest();
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
    private synchronized void sendNextRequest() throws IOException {
        while (peerChoking == false && inflightRequests<Conf.MAX_PIPELINE && curBlockIndex<totalPieceBlock){
            sendRequest(currentPiece, curBlockIndex);
            ++inflightRequests;
            ++curBlockIndex;
        }
    }
    public void sendRequest(int pieceIndex, int blockIndex) throws IOException {
        sendMessage(Message.requestMessage(pieceIndex, blockIndex*Conf.BLOCK_LENGTH, fileManager.getBlockLength(pieceIndex, blockIndex)));
    }

    public void updateDownloadedBytes(long bytes) {
        long currentTime = System.currentTimeMillis();
        downloadHistory.add(new DownloadEvent(currentTime, bytes)); 
    }
    public double calculateDownloadSpeed() {
        if (downloadHistory.isEmpty()) return 0;
    
        long totalBytesDownloaded = 0;
        long currentTime = System.currentTimeMillis();
    
        while (!downloadHistory.isEmpty() && (currentTime - downloadHistory.peekFirst().timestamp) > Conf.timeWindow) {
            downloadHistory.pollFirst(); 
        }
    
        if (downloadHistory.isEmpty()) return 0;
    
        for (DownloadEvent event : downloadHistory) {
            totalBytesDownloaded += event.bytes;
        }
    
        long timeDifferenceInSeconds = (currentTime - downloadHistory.peekFirst().timestamp) / 1000;
        
        if (timeDifferenceInSeconds == 0) {
            return 0;
        }
    
        return (double) totalBytesDownloaded / timeDifferenceInSeconds;
    }
    
    

    public void run()  {
        try{

            Handshake receivedHandshake = Handshake.receive(in);
            System.out.println("Received handshake from peer: " + receivedHandshake.getPeerId());
            if (!receivedHandshake.getInfoHash().equals(infoHash)) {
                System.out.println("infoHash mismatch. Closing connection");
                socket.close();
                return;
            }
            this.peerId=receivedHandshake.getPeerId();
            if (!parent.verifyPeerId(this.peerId)) {
                System.out.println("Peer ID mismatch. Closing connection.");
                socket.close();
                return;
            }
            parent.addConnection(this);

            while (!socket.isClosed()){
                try{

                    int length = in.readInt();
                    if (length == 0) continue; // keep-alive

                    byte messageId = in.readByte();

                    if (messageId==PeerProtocol.CHOKE){
                        peerChoking=true;
                        if (currentPiece !=-1) parent.setStatus(currentPiece, 0);  //choke halfway, set status of piece to 0 so that it can be assigned to new peer
                    }
                    else if (messageId== PeerProtocol.UNCHOKE){ 
                        peerChoking=false;
                        parent.assignPiece(this);
                    }
                    else if (messageId == PeerProtocol.INTERESTED){
                        peerInterested=true;
                    }
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
                                byte[] get=fileManager.getPieceBlock(index, i, Math.min(256,end-i));
                                System.arraycopy(get, 0, block, i-begin, Math.min(256,end-i));
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
                        int blockIndex=begin/Conf.BLOCK_LENGTH;
                        if (fileManager.blockDownloaded[index][blockIndex]) continue;
                        byte[] block= new byte[length-9];
                        in.readFully(block);
                        fileManager.writeBlock(index, begin, block);
                        inflightRequests--;
                        parent.increaseDownload(block.length);
                        updateDownloadedBytes(length-9);
                        if (blockIndex==totalPieceBlock-1){
                            if (fileManager.validate(index)) {
                                System.out.println("Downloaded piece " + currentPiece + " from peer " + peerIndex);
                                parent.setStatus(index, 1);
                                parent.sendHave(currentPiece);
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
                catch (SocketException e){ 
                    dropConnection(); return;
                }
                catch (EOFException eof){
                    dropConnection(); return;
                }
            }

        }  
        catch (IOException e){
            e.printStackTrace();
        }
    }
    public void dropConnection(){
        parent.peerLeave(this, this.peerId);
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        } 
    
    }  
}
