package peer;

import java.io.*;
import java.nio.ByteBuffer;
import java.net.Socket;

class RequestMessage {
    private final int index;
    private final int begin;
    private final int length;
    public RequestMessage(int index, int begin, int length) {
        this.index = index;
        this.begin = begin;
        this.length = length;
    }

    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(17); //4 bytes length + 13 bytes
        buffer.putInt(13); // message length
        buffer.put((byte) PeerProtocol.REQUEST); // message ID
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.putInt(length);
        return buffer.array();
    }
}

class PieceMessage {
    private final int index;
    private final int begin;
    private final byte[] block;

    public PieceMessage(int index, int begin, byte[] block) {
        this.index = index;
        this.begin = begin;
        this.block = block;
    }

    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(13 + block.length);
        buffer.putInt(9 + block.length); // message length
        buffer.put((byte) PeerProtocol.PIECE); // message ID
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.put(block);
        return buffer.array();
    }
}

public class PeerConnection implements Runnable {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    private boolean amChoking = true;
    private boolean amInterested = false;
    private boolean peerChoking = true;
    private boolean peerInterested = false;
    public PeerConnection(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
    }

    public void sendHandshake(Handshake handshake) throws IOException {
        handshake.send(out);
    }

    public void sendMessage(byte[] message) throws IOException {
        out.write(message);
        out.flush();
    }

    public void run() {
        try{
            Handshake receivedHandshake = Handshake.receive(in);
            System.out.println("Received handshake from peer: " + receivedHandshake.getPeerId());
            
            while (!socket.isClosed()){
                int length = in.readInt();
                if (length == 0) continue; // keep-alive
                byte messageId = in.readByte();

                if (messageId==PeerProtocol.CHOKE) peerChoking=true;
                else if (messageId== PeerProtocol.UNCHOKE) peerChoking=false;
                else if (messageId == PeerProtocol.INTERESTED) peerInterested=true;
                else if (messageId == PeerProtocol.NOT_INTERESTED) peerInterested=false;
                else if (messageId == PeerProtocol.HAVE) {
                    int pieceIndex=in.readInt();
                    // to-do
                }
                else if (messageId == PeerProtocol.BITFIELD){
                    byte[] bitfield = new byte[length - 1];
                    in.readFully(bitfield);
                    //to-do
                }
                else if (messageId == PeerProtocol.REQUEST){
                    //to-do (uploading)
                }
                else if (messageId == PeerProtocol.PIECE){
                    //to-do (downloading)
                }
                else if (messageId == PeerProtocol.CANCEL){
                    // stop transfering current block
                }
            }

        }  
        catch (IOException e){
            e.printStackTrace();
        }
    }   
}
