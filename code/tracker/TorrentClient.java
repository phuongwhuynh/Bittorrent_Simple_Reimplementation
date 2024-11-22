package tracker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.Socket;
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

        addTorrent(announceURL, infoHash, port, uploaded, downloaded, fileLength, 30, torrentInfo); // Interval of 30s
    }

    // for magnet... future todo
    public void addTorrent(String announceURL, byte[] infoHash, int port, long uploaded, long downloaded, long left, int interval, TorrentInfo torrentInfo) {
        TorrentState torrentState = new TorrentState(announceURL, infoHash, port, uploaded, downloaded, left, interval, torrentInfo);
        torrentStates.put(new String(infoHash), torrentState);
        new Thread(() -> sendRequest(torrentState)).start(); // Start tracking each torrent in a separate thread
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
                torrentState.updatePeerList(peers);
            }
            System.out.println(new String(torrentState.peerId) + ": Peers found for torrent " + torrentState.infoHash + ": " + torrentState.peers.size());
            for (Map<String, Object> peer : torrentState.peers) {
                System.out.println("Peer: " + peer);
            }
        }
    }


    // Inner class to manage the state of each torrent
    public class TorrentState {
        String announceURL;
        byte[] infoHash;  
        byte[] peerId;
        int port;
        long uploaded;
        long downloaded;
        long left;
        int interval; 
        PeerServer peerServer;
        List<Map<String, Object>> peers;
        final TorrentInfo torrentInfo;    //==null if use magnet link
        List<PeerConnection> peerConnections;
        FileManager fileManager;
        List<Integer> pieceStatus;  //0 -> missing, not assigned to any connection, 1 -> having, 2 -> missing, assigned
        List<Integer> pieceCounter; 
        private ChokingManager chokingManager;
        public int peerIndex=0;
        public synchronized void updateCounter(int pieceIndex){
            pieceCounter.set(pieceIndex, pieceCounter.get(pieceIndex)+1);
        }
        public void updateCounter(List<Boolean> peerHave){
            for (int i=0; i<peerHave.size(); ++i){
                if (peerHave.get(i)) updateCounter(i);
            }
        }

        public TorrentState(String announceURL, byte[] infoHash, int port, long uploaded, long downloaded, 
            long left, int interval, TorrentInfo torrentInfo) {
            this.announceURL = announceURL;
            this.infoHash = infoHash;  
            this.peerId = generatePeerId();
            this.port = port;
            this.uploaded = uploaded;
            this.downloaded = downloaded;
            this.left = left;
            this.interval = interval;
            this.peers = new ArrayList<>();
            this.torrentInfo=torrentInfo;
            this.fileManager=new FileManager(this.torrentInfo);
            this.pieceStatus=new ArrayList<>(Collections.nCopies(fileManager.getTotalPieces(), 0));     //default value 0
            this.pieceCounter = new ArrayList<>(Collections.nCopies(fileManager.getTotalPieces(), 0));  //default value 0
            peerServer=new PeerServer(port, new String(infoHash), new String(peerId), fileManager, this);
            peerServer.start();
            chokingManager = new ChokingManager();
            chokingManager.start();
    
        }
        
        void updatePeerList(List<Map<String, Object>> peers){
            for (PeerConnection peerConnection : this.peerConnections) peerConnection.dropConnection();
            this.peers=peers;
            peerConnections=new ArrayList<>(peers.size());
            this.peerIndex=0;
            for (Map<String,Object> peer : peers){
                try{
                    Socket socket=new Socket((String) peer.get("ip"), (int) peer.get("port"));
                    peerConnections.add(new PeerConnection(peerIndex++, socket, new String(infoHash), (String) peer.get("peer_id"), fileManager, this));
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
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
        public synchronized void increaseUpload(int data){
            this.uploaded+=data;
        }
        public synchronized void increaseDownload(int data){
            this.downloaded+=data;
        }
        public synchronized void setStatus(int pieceIndex, int status){
            this.pieceStatus.set(pieceIndex, status);
        }
        public int getStatus(int pieceIndex){
            return this.pieceStatus.get(pieceIndex);
        }
        public synchronized void assignPiece(PeerConnection peerConnection) throws IOException{
            // fuck
            List<Boolean> peerHave=peerConnection.peerHave;
            int rarest=Integer.MAX_VALUE;
            int findPiece=-1;
            for (int i=0; i<peerHave.size(); ++i){
                if (peerHave.get(i) && getStatus(i)==0 && pieceCounter.get(i)<rarest) {
                    findPiece=i;
                    rarest=pieceCounter.get(i);
                }
            }
            if (findPiece==-1) peerConnection.sendNotInterested();
            else{ 
                setStatus(findPiece, 2);
                peerConnection.startSendingRequest(findPiece);
            }

        }
        private class ChokingManager extends Thread {
            private int optimisticallyUnchokedIndex = -1;
            public void run(){
                while (true){
                    try {
                        // unchoke 4 + 1, 4 highest download speed + 1 optimistic
                        Collections.sort(peerConnections, Comparator.comparingDouble(PeerConnection::calculateDownloadSpeed));
                        for (PeerConnection peer: peerConnections) peer.setChoke();
                        int unchokedPeersCount = 0;
                        for (PeerConnection peer : peerConnections) {
                            if (peer.peerInterested && unchokedPeersCount < Conf.MAX_UNCHOKED_PEERS) {
                                peer.setUnchoke();
                                unchokedPeersCount++;
                            }
                        }
                        if (peerConnections.size() > Conf.MAX_UNCHOKED_PEERS) {
                            // If there hasn't been an optimistic unchoke or it's time to rotate
                            if (optimisticallyUnchokedIndex == -1 ||
                                System.currentTimeMillis() % Conf.OPTIMISTIC_UNCHOKE_INTERVAL < Conf.UNCHOKE_INTERVAL) {
                                        List<PeerConnection> eligiblePeers = peerConnections.subList(Conf.MAX_UNCHOKED_PEERS, peerConnections.size());
                                List<PeerConnection> interestedPeers = new ArrayList<>();
                                for (PeerConnection peer : eligiblePeers) {
                                    if (peer.peerInterested) interestedPeers.add(peer);
                                }
                                if (!interestedPeers.isEmpty()) {
                                    // Randomly select from interested peers outside the main unchoked group
                                    optimisticallyUnchokedIndex = Conf.MAX_UNCHOKED_PEERS + new Random().nextInt(interestedPeers.size());
                                    interestedPeers.get(optimisticallyUnchokedIndex - Conf.MAX_UNCHOKED_PEERS).setUnchoke();
                                }
                            }
                        }
        
                        Thread.sleep(Conf.UNCHOKE_INTERVAL);
                    }
                    catch (InterruptedException e){
                        Thread.currentThread().interrupt();
                        System.err.println("ChokingManager interrupted: " + e.getMessage());
                        break;
                    }
                    catch (IOException io){
                        io.printStackTrace();
                    }
                }
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
