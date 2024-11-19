package peer;
import java.io.*;
import java.util.*;
import util.*;
import torrent.TorrentInfo;

public class FileManager {
    private String name;
    private byte[][] pieces;
    private List<byte[]> pieceHashes;
    public FileManager(TorrentInfo torrentInfo) {
        this.name=torrentInfo.getName();
        this.pieceHashes=torrentInfo.getPieceHashes();
        pieces= new byte[pieceHashes.size()][];
        int fileSize=torrentInfo.getFileSize();
        int pieceSize=torrentInfo.getPieceSize();
        for (int i=0; i<pieceHashes.size(); ++i){
            pieces[i]=new byte[Math.min(fileSize, pieceSize)];
            fileSize-=pieceSize;
        }
    }
}
