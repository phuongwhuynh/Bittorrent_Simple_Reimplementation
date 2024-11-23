package peer;
import java.io.*;
import java.util.*;
import util.Conf;
import util.SHA;
import torrent.TorrentInfo;
import tracker.TorrentClient;

public class FileManager {
    //private final String name;
    private byte[][] pieces;
    private List<byte[]> pieceHashes;
    public List<Boolean> havePiece;
    private final int totalPieces;
    private final int fileSize;
    private int downloaded;
    private boolean[][] blockDownloaded;
    private TorrentClient.TorrentState parent;
    public FileManager(TorrentInfo torrentInfo, TorrentClient.TorrentState parent) throws UnsupportedEncodingException {
        //this.name=torrentInfo.getName();
        this.parent=parent;
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
        for (int i=0; i<totalPieces; ++i) havePiece.add(Boolean.FALSE); 
        downloaded=0;
    }
    public void bufferToFile(String outputPath){
        //to do: write byte[][] pieces to outputPath
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputPath)) {
            for (byte[] piece : pieces) {
                if (piece != null) {
                    fileOutputStream.write(piece);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (int i=0; i<totalPieces; ++i) havePiece.set(i,Boolean.TRUE);

    }
    public void fileToBuffer(String inputPath) throws IOException{
        //to do write to pieces
        try (FileInputStream fileInputStream = new FileInputStream(inputPath)) {
            byte[] buffer = new byte[Conf.pieceLength];
            int bytesRead;
            
            for (int i = 0; i < totalPieces; i++) {
                bytesRead = fileInputStream.read(buffer);
                if (bytesRead == -1) {
                    break; // End of file reached
                }
                
                if (bytesRead < Conf.pieceLength) {
                    pieces[i] = Arrays.copyOf(buffer, bytesRead);
                } else {
                    pieces[i] = buffer.clone();
                }
            }
            for (int i=0; i<totalPieces; ++i) havePiece.set(i,Boolean.TRUE);
            for (int i=0; i<totalPieces; ++i){
                byte[] expectedHash=pieceHashes.get(i);
                byte[] curHash=SHA.generateSHA1Hash(pieces[i]);
                System.out.print("curHash: ");
                for (int j=0; j<20; ++j) System.out.print((int) curHash[j]+",");
                System.out.println();
                System.out.print("expectedHash: ");
                for (int j=0; j<20; ++j) System.out.print((int) expectedHash[j]+",");
                System.out.println();

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
    public boolean validate(int pieceIndex) throws UnsupportedEncodingException{
        byte[] curHash=SHA.generateSHA1Hash(pieces[pieceIndex]);
        byte[] expectedHash= pieceHashes.get(pieceIndex);
        System.out.println("curHash: "+ new String(curHash, "ISO-8859-1"));
        System.out.println("expectedHash: "+ new String(expectedHash, "ISO-8859-1"));

        if (Arrays.equals(curHash, expectedHash)){
            //update havePiece if the piece is valid
            havePiece.set(pieceIndex,true);
            downloaded+=pieces[pieceIndex].length;
            return true;
        }
        else {
            havePiece.set(pieceIndex, false);   //redundancy for good
            for (int i=0; i<blockDownloaded[pieceIndex].length; ++i) setStatus(pieceIndex, i, false); //flush the status to get the piece again
            parent.setStatus(pieceIndex, 0);
            return false;
        }
        
    }
    public byte[] concatenation(){
        int totalLength = 0;
        for (byte[] piece : pieces) {
            totalLength += piece.length;
        }
            byte[] result = new byte[totalLength];
    
        int currentPosition = 0;
        for (byte[] piece : pieces) {
            System.arraycopy(piece, 0, result, currentPosition, piece.length);
            currentPosition += piece.length;
        }
    
        return result;
    
    }
    // public boolean validateAll(byte[] infoHash){
    //     byte[] temp=concatenation();
    //     if (Arrays.equals(infoHash, SHA.generateSHA1Hash(temp))) return true;
    //     else {
    //         for (int i=0; i<totalPieces; ++i) validate(i);
    //         return false;
    //     }
    // }

}


