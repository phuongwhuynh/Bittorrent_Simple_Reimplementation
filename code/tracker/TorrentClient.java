package tracker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import util.*;



public class TorrentClient {

    // Track all torrents being downloaded by this client
    private Map<String, TorrentState> torrentStates; // Maps infoHash to torrent state
    private boolean compact;

    public TorrentClient(boolean compact) {
        this.torrentStates = new ConcurrentHashMap<>();
        this.compact = compact;
    }
    public void addTorrentFromFileContent(String torrentContent, int port, long uploaded, long downloaded) {
        // Decode the .torrent file content
        @SuppressWarnings("unchecked")
        Map<String, Object> decodedTorrent = (Map<String, Object>) BDecode.decode(torrentContent);
        
        // Extract announce URL
        String announceURL = (String) decodedTorrent.get("announce");
        
        // Extract and hash the info dictionary to get the infoHash
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) decodedTorrent.get("info");
        String infoHash = calculateInfoHash(info);
        
        // Extract additional metadata
        String fileName = (String) info.get("name");
        long fileLength = (long) info.get("length");
        long pieceLength = (long) info.get("piece length");

        System.out.printf("Parsed torrent file - Announce URL: %s, File Name: %s, File Length: %d, Piece Length: %d\n",
                announceURL, fileName, fileLength, pieceLength);

        // Create a new TorrentState with extracted data and start tracking
        addTorrent(announceURL, infoHash, port, uploaded, downloaded, fileLength, 30); // Interval of 30s
    }

    private String calculateInfoHash(Map<String, Object> info) {
        try {
            // Re-encode the "info" dictionary to bencode format
            String infoEncoded = BEncode.encode(info); // Assuming Encode class can handle encoding
            
            // Calculate the SHA-1 hash of the encoded info dictionary
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] infoHashBytes = digest.digest(infoEncoded.getBytes(StandardCharsets.ISO_8859_1));
            return SHA.bytesToHex(infoHashBytes); // Convert byte array to hexadecimal string
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not found", e);
        }
    }


    // Inner class to manage the state of each torrent
    private class TorrentState {
        String announceURL;
        String infoHash;  // Stored as Hexadecimal String
        String peerId;
        int port;
        long uploaded;
        long downloaded;
        long left;
        int interval; // in seconds
        List<Map<String, Object>> peers;

        public TorrentState(String announceURL, String infoHash, int port, long uploaded, long downloaded, long left, int interval) {
            this.announceURL = announceURL;
            this.infoHash = SHA.generateSHA1Hash(infoHash.getBytes(StandardCharsets.ISO_8859_1));  // Store infoHash as Hex
            this.peerId = generatePeerId();
            this.port = port;
            this.uploaded = uploaded;
            this.downloaded = downloaded;
            this.left = left;
            this.interval = interval;
            this.peers = new ArrayList<>();
        }

        private String generatePeerId() {
            Random random = new Random();
            byte[] peerIdBytes = new byte[20];
            random.nextBytes(peerIdBytes);
            return urlEncodeBytes(peerIdBytes);
        }

        // Method to URL encode bytes
        private String urlEncodeBytes(byte[] bytes) {
            StringBuilder encoded = new StringBuilder();
            for (byte b : bytes) {
                if ((b >= '0' && b <= '9') || (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || b == '.' || b == '-' || b == '_' || b == '~') {
                    encoded.append((char) b);
                } else {
                    encoded.append(String.format("%%%02X", b));  // URL encode the byte
                }
            }
            return encoded.toString();
        }

        // Build the URL for sending to the tracker
        private String buildURL() {
            try {
                // URL encode the infoHash and peerId before sending
                String encodedInfoHash = URLEncoder.encode(infoHash, StandardCharsets.UTF_8.name());
                String encodedPeerId = URLEncoder.encode(peerId, StandardCharsets.UTF_8.name());
                return String.format("%s?info_hash=%s&peer_id=%s&port=%d&uploaded=%d&downloaded=%d&left=%d&compact=%d",
                        announceURL, encodedInfoHash, encodedPeerId, port, uploaded, downloaded, left, compact ? 1 : 0);
            } catch (Exception e) {
                System.err.println("Error encoding URL parameters: " + e.getMessage());
                return null;
            }
        }
    }

    // Adds a torrent to the client for tracking and downloading
    public void addTorrent(String announceURL, String infoHash, int port, long uploaded, long downloaded, long left, int interval) {
        TorrentState torrentState = new TorrentState(announceURL, infoHash, port, uploaded, downloaded, left, interval);
        torrentStates.put(infoHash, torrentState);
        new Thread(() -> sendRequest(torrentState)).start(); // Start tracking each torrent in a separate thread
    }

    // Send the request to the tracker at regular intervals for a specific torrent
    private void sendRequest(TorrentState torrentState) {
        while (true) {
            try {
                String fullURL = torrentState.buildURL();
                if (fullURL == null) {
                    System.out.println("Error building URL for torrent: " + torrentState.infoHash);
                    return;
                }
                System.out.println("Sending request to: " + fullURL);  // Log the URL
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

                        // Decode the tracker's response for this torrent
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
            }
        }
    }

    // Handle the decoded response from the tracker for a specific torrent
    private void handleTrackerResponse(Map<String, Object> response, TorrentState torrentState) {
        // Update interval if provided by the tracker
        torrentState.interval = (int) response.getOrDefault("interval", torrentState.interval);

        // Clear and update the peer list
        torrentState.peers.clear();
        if (response.containsKey("peers")) {
            // `peers` is either a list of dictionaries or a compact string of peer IPs and ports
            Object peersObj = response.get("peers");
            if (peersObj instanceof List) {
                List<?> peers = (List<?>) peersObj;
                for (Object peerObj : peers) {
                    if (peerObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> peerMap = (Map<String, Object>) peerObj;
                        torrentState.peers.add(peerMap);
                    }
                }
            }
            System.out.println("Peers found for torrent " + torrentState.infoHash + ": " + torrentState.peers.size());
            for (Map<String, Object> peer : torrentState.peers) {
                System.out.println("Peer: " + peer);
            }
        }
    }

    // Main method for testing
    public static void main(String[] args) {
        TorrentClient client = new TorrentClient(true);

        String announceURL1 = "http://localhost:8080/announce";
        String infoHash1 = "sample_torrent_file_data_1";  // Simulated file data
        String announceURL2 = "http://localhost:8080/announce";
        String infoHash2 = "sample_torrent_file_data_2";  // Simulated file data

        client.addTorrent(announceURL1, infoHash1, 6881, 0, 0, 5000, 30); // Add first torrent
        client.addTorrent(announceURL2, infoHash2, 6882, 0, 0, 10000, 30); // Add second torrent
    }
}
