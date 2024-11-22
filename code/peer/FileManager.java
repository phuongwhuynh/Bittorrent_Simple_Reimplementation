package peer;
import java.io.*;
import java.util.*;
import util.Conf;
import util.SHA;
import torrent.TorrentInfo;

public class FileManager {
    //private final String name;
    private byte[][] pieces;
    private List<byte[]> pieceHashes;
    public List<Boolean> havePiece;
    private final int totalPieces;
    private final int fileSize;
    private int downloaded;
    private boolean[][] blockDownloaded;

    public FileManager(TorrentInfo torrentInfo) {
        //this.name=torrentInfo.getName();
        this.pieceHashes=torrentInfo.getPieceHashes();
        this.totalPieces=pieceHashes.size();
        pieces= new byte[totalPieces][];
        int fileSize=torrentInfo.getFileSize();
        this.fileSize=fileSize;
        blockDownloaded=new boolean[totalPieces][];
        int pieceSize=torrentInfo.getPieceSize();
        for (int i=0; i<totalPieces; ++i){
            pieces[i]=new byte[Math.min(fileSize, pieceSize)];
            blockDownloaded[i]=new boolean[(int) Math.ceil(pieces[i].length/Conf.BLOCK_LENGTH)];
            for (int j=0; j<blockDownloaded[i].length; ++j) blockDownloaded[i][j]=false;
            fileSize-=pieceSize;
        }
        havePiece = new ArrayList<>(totalPieces);
        for (int i=0; i<totalPieces; ++i) havePiece.add(Boolean.FALSE); // Correct initialization
        downloaded=0;
    }
    public int getTotalPieces(){
        return totalPieces;
    }
    public int getTotalBlock(int piece){
        return blockDownloaded[piece].length;
    }
    public byte[] getBitField() {
        int byteArraySize = (int) Math.ceil(havePiece.size() / 8.0);
        byte[] bitfield = new byte[byteArraySize];
    
        for (int i = 0; i < havePiece.size(); i++) {
            if (havePiece.get(i)) {
                int byteIndex = i / 8;
                int bitIndex = 7 - (i % 8); // 7 - to make most significant bit first
                // Set the corresponding bit using bitwise OR
                bitfield[byteIndex] |= (1 << bitIndex);
            }
        }
        return bitfield;
    }
    public synchronized void writeBlock(int index, int begin, byte[] block) {   //multithreading
        if (index >= 0 && index < totalPieces) {
            // Check if the piece is valid
            byte[] piece = pieces[index];
            int end = Math.min(begin + block.length, piece.length);
            System.arraycopy(block, 0, piece, begin, end - begin); // Write the block to the piece
            int blockIndex=begin/Conf.BLOCK_LENGTH;
            setStatus(index, blockIndex, true);

            
        } else {
            System.out.println("Invalid piece index: " + index);
        }
    }
    public int getBlockIndex(int pieceIndex){
        for (int i=0; i<blockDownloaded[pieceIndex].length; ++i){
            if (blockDownloaded[pieceIndex][i]==false) return i;
        }
 
        return -1;
    }
    public int getBlockLength(int pieceIndex, int blockIndex){
        return Math.min(Conf.BLOCK_LENGTH, pieces[pieceIndex].length-Conf.BLOCK_LENGTH*blockIndex);
    }
    public void constructFile(String outputFilePath) throws IOException {
        if (downloaded == fileSize) {
            System.out.println("All pieces downloaded. Assembling the file...");
            try (FileOutputStream fileOutputStream = new FileOutputStream(outputFilePath)) {
                for (int i = 0; i < totalPieces; ++i) {
                    fileOutputStream.write(pieces[i]);
                }
            }
            System.out.println("File successfully constructed at: " + outputFilePath);
        } 
        else {
            System.out.println("File is not completely downloaded.");
        }
    }
    public byte[] getPieceBlock(int index, int begin, int length){
        if (index >= 0 && index < pieces.length) {
            byte[] piece = pieces[index];  // Get the piece from the array
            int pieceLength = piece.length;
            int end = Math.min(begin + length, pieceLength);
            // Extract the requested block
            byte[] block = new byte[end - begin];
            System.arraycopy(piece, begin, block, 0, end - begin);
            return block;
        } else {
            return null;  // Invalid index
        }
    }
    public synchronized void setStatus(int pieceIndex, int blockIndex, boolean status){
        blockDownloaded[pieceIndex][blockIndex]=status;
    }
    public boolean validate(int pieceIndex){
        byte[] curHash=SHA.generateSHA1Hash(pieces[pieceIndex]);
        byte[] expectedHash= pieceHashes.get(pieceIndex);
        if (Arrays.equals(curHash, expectedHash)){
            //update havePiece if the piece is valid
            havePiece.set(pieceIndex,true);
            downloaded+=pieces[pieceIndex].length;
            return true;
        }
        else {
            for (int i=0; i<blockDownloaded[pieceIndex].length; ++i) setStatus(pieceIndex, i, false); //flush the status to get the piece again
            return false;
        }
        
    }
}


