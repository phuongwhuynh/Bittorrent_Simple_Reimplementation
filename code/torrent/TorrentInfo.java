package torrent;
import util.*;
import java.util.*;
import java.io.*;

//Single-file mode
public class TorrentInfo {
    private Map<String, Object> torrentInfo;  

    public TorrentInfo(String torrentFilePath) throws IOException {
        parseTorrentFile(torrentFilePath);
    }

    private void parseTorrentFile(String torrentFilePath) throws IOException {
        // Load and decode the torrent file
        String metadata="";
        FileInputStream fis = new FileInputStream(torrentFilePath);
        char c;
        int get;
        while ((get=fis.read()) != -1){
            c=(char) get;
            metadata+=c;
        }
        fis.close();

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
    public String getName(){
        return (String) getInfo().get("name");
    }
    public int getFileSize(){
        return (int) getInfo().get("length");
    }
    public int getPieceSize(){
        return (int) getInfo().get("piece length");
    }
    public List<byte[]> getPieceHashes() throws UnsupportedEncodingException {
        String pieces = (String) getInfo().get("pieces");
        int pieceCount = pieces.length() / 20;
        List<byte[]> hashes=new ArrayList<>(pieceCount);
        for (int i = 0; i < pieceCount; i++) {
            String pieceHash = pieces.substring(i*20, (i+1)*20);
            hashes.add(pieceHash.getBytes("ISO-8859-1"));
        }
        return hashes;

    }
    public void displayInfo() throws UnsupportedEncodingException {
        Map<String,Object> info=getInfo();
        // System.out.println("Announce URL: " + getAnnounceURL());
        // System.out.println("Name: " + info.get("name"));
        // System.out.println("Length: " + info.get("length"));
        // System.out.println("Piece Length: " + info.get("piece length"));
        
        // String pieces = (String) info.get("pieces");
        // int pieceCount = pieces.length() / 20;
        // System.out.println("Pieces: " + pieceCount + " pieces (each 20 bytes long)");
        
        // // Print each piece hash in hexadecimal format
        // for (int i = 0; i < pieceCount; i++) {
        //     String pieceHash = pieces.substring(i*20, (i+1)*20);
        //     System.out.println("  Piece " + (i + 1) + " Hash: " + pieceHash);
        // }
        String pieces= (String) info.get("pieces");
        byte[] hashPieces=pieces.getBytes("ISO-8859-1");
        for (int i=0; i<hashPieces.length; ++i) System.out.print(hashPieces[i]+",");
        System.out.println();
    }
    public static void generateTorrentFile(String filePath, String torrentFilePath) throws IOException {
        File file=new File(filePath);
        Map<String, Object> info=new HashMap<>();
        info.put("name", file.getName());
        info.put("length", file.length());
        info.put("piece length", Conf.pieceLength);
        byte[] hashString= SHA.piecesSHA1(filePath);

        info.put("pieces", hashString);
        Map<String, Object> torrentInfo=new HashMap<>();
        torrentInfo.put("announce", Conf.announce);
        torrentInfo.put("info", info);
        String code = BEncode.encode(torrentInfo);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(torrentFilePath), "ISO-8859-1")) {
            writer.write(code);
        }
    }

    public static void main(String[] args) throws IOException {
        String filePath="testDir/random.txt";
        String torrentFilePath="torrent/example.torrent";
        generateTorrentFile(filePath, torrentFilePath);
        TorrentInfo test= new TorrentInfo(torrentFilePath);
        test.displayInfo();
    }
}
