package tracker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
//import java.net.URLEncoder;
//import java.nio.charset.StandardCharsets;
//import java.security.MessageDigest;
//import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import torrent.TorrentInfo;
import util.*;
import peer.*;


public class TorrentClient {

    private Map<String, TorrentState> torrentStates; // Maps infoHash to torrent state
    private boolean compact;

    public TorrentClient(boolean compact) {
        this.torrentStates = new ConcurrentHashMap<>();
        this.compact = compact;
    }
    public void addTorrentFromFileContent(String torrentFilePath, int port, long uploaded, long downloaded) throws IOException {
        TorrentInfo torrentInfo=new TorrentInfo(torrentFilePath);
        
        String announceURL = (String) torrentInfo.getAnnounceURL();
        
        Map<String, Object> info = torrentInfo.getInfo();
        byte[] infoHash = SHA.calculateInfoHash(info);
        
        // Extract additional metadata
        String fileName = (String) info.get("name");
        int fileLength = (int) info.get("length");
        int pieceLength = (int) info.get("piece length");

        System.out.printf("Parsed torrent file - Announce URL: %s, File Name: %s, File Length: %d, Piece Length: %d\n",
                announceURL, fileName, fileLength, pieceLength);

        addTorrent(announceURL, infoHash, port, uploaded, downloaded, fileLength, 30); // Interval of 30s
    }

    // for magnet... future todo
    public void addTorrent(String announceURL, byte[] infoHash, int port, long uploaded, long downloaded, long left, int interval) {
        TorrentState torrentState = new TorrentState(announceURL, infoHash, port, uploaded, downloaded, left, interval);
        torrentStates.put(new String(infoHash), torrentState);
        new Thread(() -> sendRequest(torrentState)).start(); // Start tracking each torrent in a separate thread
    }
    // Inner class to manage the state of each torrent
    private class TorrentState {
        String announceURL;
        byte[] infoHash;  
        byte[] peerId;
        int port;
        long uploaded;
        long downloaded;
        long left;
        int interval; 
        List<Map<String, Object>> peers;

        public TorrentState(String announceURL, byte[] infoHash, int port, long uploaded, long downloaded, long left, int interval) {
            this.announceURL = announceURL;
            this.infoHash = infoHash;  
            this.peerId = generatePeerId();
            this.port = port;
            this.uploaded = uploaded;
            this.downloaded = downloaded;
            this.left = left;
            this.interval = interval;
            this.peers = new ArrayList<>();
        }

        private byte[] generatePeerId() {
            Random random = new Random();
            byte[] peerId= new byte[20];
            random.nextBytes(peerId);
            return peerId;
        }
        

        private String buildURL() {
            try {
                String encodedInfoHash = URLHandle.encode(infoHash);
                System.out.println("hash: " + new String(encodedInfoHash));
                String encodedPeerId = URLHandle.encode(peerId);
                return String.format("%s?info_hash=%s&peer_id=%s&port=%d&uploaded=%d&downloaded=%d&left=%d&compact=%d",
                        announceURL, encodedInfoHash, encodedPeerId, port, uploaded, downloaded, left, compact ? 1 : 0);
            } catch (Exception e) {
                System.err.println("Error encoding URL parameters: " + e.getMessage());
                return null;
            }
        }
    }


    
    // send the request to the tracker at regular intervals for a specific torrent
    private void sendRequest(TorrentState torrentState) {
        while (true) {
            try {
                String fullURL = torrentState.buildURL();
                if (fullURL == null) {
                    System.out.println("Error building URL for torrent: " + torrentState.infoHash);
                    return;
                }
                System.out.println("Sending request to: " + fullURL); 
                URL url = new URL(fullURL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);  // 10 seconds timeout
                connection.setReadTimeout(10000);     // 10 seconds timeout
                int responseCode = connection.getResponseCode();
                System.out.println("Response Code: " + responseCode);  // Log the response code
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder response = new StringBuilder();
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> decodedResponse = (Map<String, Object>) BDecode.decode(response.toString());
                        handleTrackerResponse(decodedResponse, torrentState);
                    }
                } else {
                    System.out.println("Failed to connect. Response Code: " + responseCode);
                }

                // Wait for the specified interval before sending the next update
                Thread.sleep(torrentState.interval * 1000);
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
                break;
            }
        }
    }

    // Handle the decoded response from the tracker for a specific torrent
    private void handleTrackerResponse(Map<String, Object> response, TorrentState torrentState) {
        torrentState.interval = (int) response.getOrDefault("interval", torrentState.interval);
        torrentState.peers.clear();
        if (response.containsKey("peers")) {
            // `peers` is either a list of dictionaries or a compact string of peer IPs and ports
            Object peersObj = response.get("peers");
            if (peersObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String,Object>> peers = (List<Map<String,Object>>) peersObj;
                // for (Object peerObj : peers) {
                //     if (peerObj instanceof Map) {
                //         @SuppressWarnings("unchecked")
                //         Map<String, Object> peerMap = (Map<String, Object>) peerObj;
                //         torrentState.peers.add(peerMap);
                //     }
                // }
                torrentState.peers=peers;
            }
            System.out.println(new String(torrentState.peerId) + ": Peers found for torrent " + torrentState.infoHash + ": " + torrentState.peers.size());
            for (Map<String, Object> peer : torrentState.peers) {
                System.out.println("Peer: " + peer);
            }
        }
    }

    // Main method for testing
    public static void main(String[] args) {
        TorrentClient client = new TorrentClient(true);
        try{
            client.addTorrentFromFileContent("torrent/example.torrent", 6881, 0, 0);
            //client.addTorrentFromFileContent("torrent/example.torrent", 6882, 0, 0);

        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
}
