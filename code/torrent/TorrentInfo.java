package torrent;
import util.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.*;

//Single-file mode
public class TorrentInfo {
    private Map<String, Object> torrentInfo;  

    public TorrentInfo(String torrentFilePath) throws IOException {
        parseTorrentFile(torrentFilePath);
    }

    private void parseTorrentFile(String torrentFilePath) throws IOException {
        // Load and decode the torrent file
        Path filePath = Paths.get(torrentFilePath);
        String metadata = Files.readString(filePath);

        @SuppressWarnings("unchecked")
        Map<String, Object> torrentInfo = (Map<String, Object>) BDecode.decode(metadata);
        this.torrentInfo=torrentInfo;
        
    }

    public String getAnnounceURL() {
        return (String)torrentInfo.get("announce");
    }

    public Map<String, Object> getInfo() {
        @SuppressWarnings("unchecked") 
        Map<String,Object> info=(Map<String,Object>) torrentInfo.get("info");
        return info;
    }

    public void displayInfo() {
        Map<String,Object> info=getInfo();
        System.out.println("Announce URL: " + getAnnounceURL());
        System.out.println("Name: " + info.get("name"));
        System.out.println("Length: " + info.get("length"));
        System.out.println("Piece Length: " + info.get("piece length"));
        
        String pieces = (String) info.get("pieces");
        int pieceCount = pieces.length() / 20;
        System.out.println("Pieces: " + pieceCount + " pieces (each 20 bytes long)");
        
        // Print each piece hash in hexadecimal format
        for (int i = 0; i < pieceCount; i++) {
            String pieceHash = pieces.substring(i*20, (i+1)*20);
            System.out.println("  Piece " + (i + 1) + " Hash: " + pieceHash);
        }
    }
    public static void generateTorrentFile(String filePath, String torrentFilePath) throws IOException {
        File file=new File(filePath);
        Map<String, Object> info=new HashMap<>();
        info.put("name", file.getName());
        info.put("length", file.length());
        info.put("piece length", Conf.pieceLength);
        List<String> hash=Piece.piecesSHA1(filePath);
        String hashString="";
        for (String code : hash) hashString+=code;
        info.put("pieces", hashString);
        Map<String, Object> torrentInfo=new HashMap<>();
        torrentInfo.put("announce", Conf.announce);
        torrentInfo.put("info", info);
        String code= BEncode.encode(torrentInfo);
        FileWriter writer=new FileWriter(torrentFilePath);
        writer.write(code);
        writer.close();
    }

    public static void main(String[] args) throws IOException {
        String filePath="testDir/random.txt";
        String torrentFilePath="torrent/example.torrent";
        generateTorrentFile(filePath, torrentFilePath);
        TorrentInfo test= new TorrentInfo(torrentFilePath);
        test.displayInfo();
    }
}
