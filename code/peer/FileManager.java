package peer;
import java.io.*;
import java.util.*;
//import util.*;
import torrent.TorrentInfo;

public class FileManager {
    //private final String name;
    private byte[][] pieces;
    private List<byte[]> pieceHashes;
    public List<Boolean> havePiece;
    private final int totalPieces;
    private final int fileSize;
    private int downloaded;
    private int[] bytesDownloaded;

    public FileManager(TorrentInfo torrentInfo) {
        //this.name=torrentInfo.getName();
        this.pieceHashes=torrentInfo.getPieceHashes();
        this.totalPieces=pieceHashes.size();
        pieces= new byte[totalPieces][];
        int fileSize=torrentInfo.getFileSize();
        this.fileSize=fileSize;
        bytesDownloaded=new int[totalPieces];
        int pieceSize=torrentInfo.getPieceSize();
        for (int i=0; i<totalPieces; ++i){
            bytesDownloaded[i]=0;
            pieces[i]=new byte[Math.min(fileSize, pieceSize)];
            fileSize-=pieceSize;
        }
        havePiece = new ArrayList<>(totalPieces);
        for (int i=0; i<totalPieces; ++i) havePiece.add(Boolean.FALSE); // Correct initialization
        downloaded=0;
    }
    public int getTotalPieces(){
        return totalPieces;
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
    public void writeBlock(int index, int begin, byte[] block) {
        if (index >= 0 && index < totalPieces) {
            // Check if the piece is valid
            byte[] piece = pieces[index];
            int end = Math.min(begin + block.length, piece.length);
            System.arraycopy(block, 0, piece, begin, end - begin); // Write the block to the piece

            // Update 'have' list if the entire piece has been downloaded
            bytesDownloaded[index]+=block.length;
            downloaded+=block.length;
            if (bytesDownloaded[index] == pieces[index].length) havePiece.set(index, true);
        } else {
            System.out.println("Invalid piece index: " + index);
        }
    }
    public int getBlockIndex(int pieceIndex){
        return bytesDownloaded[pieceIndex];
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
}


