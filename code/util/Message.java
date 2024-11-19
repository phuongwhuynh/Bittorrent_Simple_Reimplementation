package util;

import java.nio.ByteBuffer;

import peer.PeerProtocol;

public class Message {
    public static byte[] keepAliveMessage(){
        ByteBuffer buffer=ByteBuffer.allocate(4);
        buffer.putInt(0);
        return buffer.array();
    }
    public static byte[] chokeMessage(){
        ByteBuffer buffer = ByteBuffer.allocate(5); 
        buffer.putInt(1); // message length
        buffer.put((byte) PeerProtocol.CHOKE); 
        return buffer.array();
    }
    public static byte[] unchokeMessage(){
        ByteBuffer buffer = ByteBuffer.allocate(5); 
        buffer.putInt(1); // message length
        buffer.put((byte) PeerProtocol.UNCHOKE); 
        return buffer.array();
    }
    public static byte[] interestedMessage(){
        ByteBuffer buffer = ByteBuffer.allocate(5); 
        buffer.putInt(1); // message length
        buffer.put((byte) PeerProtocol.INTERESTED); 
        return buffer.array(); 
    }
    public static byte[] notInterestedMessage(){
        ByteBuffer buffer = ByteBuffer.allocate(5); 
        buffer.putInt(1); // message length
        buffer.put((byte) PeerProtocol.NOT_INTERESTED); 
        return buffer.array(); 
    }
    public static byte[] haveMessage(int pieceIndex){
        ByteBuffer buffer = ByteBuffer.allocate(5); 
        buffer.putInt(5); // message length
        buffer.put((byte) PeerProtocol.HAVE); 
        buffer.putInt(pieceIndex);
        return buffer.array();
    }
    public static byte[] bitfieldMessage(byte[] bits){
        ByteBuffer buffer=ByteBuffer.allocate(5+bits.length);
        buffer.putInt(1+bits.length);
        buffer.put((byte) PeerProtocol.BITFIELD);
        buffer.put(bits);
        return buffer.array();
    }
    public static byte[] requestMessage(int index, int begin, int length){
        ByteBuffer buffer = ByteBuffer.allocate(17); //4 bytes length integer + 13 bytes
        buffer.putInt(13); // message length
        buffer.put((byte) PeerProtocol.REQUEST); // message ID
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.putInt(length);
        return buffer.array();
    }
    public static byte[] pieceMessage(int index, int begin, byte[] block){
        ByteBuffer buffer = ByteBuffer.allocate(13 + block.length);
        buffer.putInt(9 + block.length); // message length
        buffer.put((byte) PeerProtocol.PIECE); // message ID
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.put(block);
        return buffer.array();
    }
    public static byte[] cancelMessage(int index, int begin, int length){
        ByteBuffer buffer = ByteBuffer.allocate(17); //4 bytes length integer + 13 bytes
        buffer.putInt(13); // message length
        buffer.put((byte) PeerProtocol.CANCEL); // message ID
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.putInt(length);
        return buffer.array();
    }
}
