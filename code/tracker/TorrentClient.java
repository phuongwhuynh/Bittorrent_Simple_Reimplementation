package tracker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.io.File;
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
import torrent.TorrentInfo;

public class TorrentClient {

    private Map<String, TorrentState> torrentStates; // Maps infoHash to torrent state
    private boolean compact;

    public TorrentClient(boolean compact) {
        this.torrentStates = new ConcurrentHashMap<>();
        this.compact = compact;
    }
    public void addTorrentFromFileContent(String torrentFilePath, String outputPath, int port) throws IOException {
        TorrentInfo torrentInfo=new TorrentInfo(torrentFilePath);
        String announceURL = (String) torrentInfo.getAnnounceURL();
        Map<String, Object> info = torrentInfo.getInfo();
        byte[] infoHash = SHA.calculateInfoHash(info);
        // Extract additional metadata
        String fileName = (String) info.get("name");
        int fileLength = (int) info.get("length");
        int pieceLength = (int) info.get("piece length");

        System.out.printf("Leecher: Parsed torrent file - Announce URL: %s, File Name: %s, File Length: %d, Piece Length: %d\n",
                announceURL, fileName, fileLength, pieceLength);

        addTorrent(announceURL, infoHash, port, fileLength, 30, torrentInfo, outputPath, false); // Interval of 30s
    }
    public void addSeeder(String torrentFilePath, String inputPath, int port) throws IOException {
        TorrentInfo torrentInfo=new TorrentInfo(torrentFilePath);
        String announceURL = (String) torrentInfo.getAnnounceURL();
        Map<String, Object> info = torrentInfo.getInfo();
        byte[] infoHash = SHA.calculateInfoHash(info);

        //new todo: validate file with pieces hash

        // File file=new File(inputPath);
        // byte[] curHash=SHA.generateSHA1Hash(file);
        // if (!Arrays.equals(infoHash, curHash)){
        //     System.out.println("This file does not fit in with this torrent");
        //     return;
        // }
        // Extract additional metadata
        String fileName = (String) info.get("name");
        int fileLength = (int) info.get("length");
        int pieceLength = (int) info.get("piece length");

        System.out.printf("Seeder: Parsed torrent file - Announce URL: %s, File Name: %s, File Length: %d, Piece Length: %d\n",
                announceURL, fileName, fileLength, pieceLength);
        
        addTorrent(announceURL, infoHash, port, 0, 30, torrentInfo, inputPath, true); // Interval of 30s

    }

    // for magnet... future todo
    public void addTorrent(String announceURL, byte[] infoHash, int port, long left, int interval, TorrentInfo torrentInfo, String outputPath, boolean seeder) 
            throws IOException, UnsupportedEncodingException {
        TorrentState torrentState = new TorrentState(announceURL, infoHash, port, 0, 0, left, interval, torrentInfo, outputPath, seeder);
        torrentStates.put(new String(infoHash, "ISO-8859-1"), torrentState);
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
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "ISO-8859-1"))) {
                        StringBuilder response = new StringBuilder();
                        int input;
                        while ((input = in.read()) != -1) {
                            response.append((char) input);
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
    private void handleTrackerResponse(Map<String, Object> response, TorrentState torrentState) throws IOException {
        
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
            try{
                System.out.println(new String(torrentState.peerId, "ISO-8859-1") + ": Peers found for torrent " + 
                    new String(torrentState.infoHash, "ISO-8859-1") + ": " + torrentState.peers.size());
            }
            catch (UnsupportedEncodingException abc){}
            for (Map<String, Object> peer : torrentState.peers) {
                System.out.println("Peer: " + peer);
            }
        }
    }


    // Inner class to manage the state of each torrent
    public class TorrentState {
        final String announceURL;
        final byte[] infoHash;  
        final public byte[] peerId;
        final int port;
        long uploaded;
        long downloaded;
        long left;
        int interval; 
        PeerServer peerServer;
        List<Map<String, Object>> peers;
        final TorrentInfo torrentInfo;    //==null if use magnet link
        final String outputPath;
        List<PeerConnection> peerConnections;
        Map<String, PeerConnection> connectionMap;
        FileManager fileManager;
        List<Integer> pieceStatus;  //0 -> missing, not assigned to any connection, 1 -> having, 2 -> missing, assigned
        List<Integer> pieceCounter; 
        private ChokingManager chokingManager;
        public int peerIndex=0;
        private boolean gotAll=false;
        public synchronized void updateCounter(int pieceIndex){
            pieceCounter.set(pieceIndex, pieceCounter.get(pieceIndex)+1);
        }
        public void updateCounter(List<Boolean> peerHave){
            for (int i=0; i<peerHave.size(); ++i){
                if (peerHave.get(i)) updateCounter(i);
            }
        }

        public TorrentState(String announceURL, byte[] infoHash, int port, long uploaded, long downloaded, 
            long left, int interval, TorrentInfo torrentInfo, String outputPath, boolean seeder) throws IOException, UnsupportedEncodingException {
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
            this.outputPath=outputPath;
            this.fileManager=new FileManager(this.torrentInfo, this);
            connectionMap=new ConcurrentHashMap<>();
            if (!seeder){
                this.pieceStatus=new ArrayList<>(Collections.nCopies(fileManager.getTotalPieces(), 0));     //default value 0
                this.pieceCounter = new ArrayList<>(Collections.nCopies(fileManager.getTotalPieces(), 0));  //default value 0
            }
            else {
                fileManager.fileToBuffer(outputPath);
                gotAll=true;
                this.pieceStatus=new ArrayList<>(Collections.nCopies(fileManager.getTotalPieces(), 1));     //default value 1
                this.pieceCounter = new ArrayList<>(Collections.nCopies(fileManager.getTotalPieces(), 0));  //default value 0
            }
            peerConnections=new ArrayList<>();
            chokingManager = new ChokingManager();
            chokingManager.start();
        }
        
        void updatePeerList(List<Map<String, Object>> peers) throws IOException{

            connectionMap.clear();
            this.peers=peers;
            peerConnections.clear();
            while (!this.peerConnections.isEmpty()){
                PeerConnection peerConnection=this.peerConnections.get(0);
                peerConnection.dropConnection();
            }

            if (peerServer!=null){
                peerServer.dropServer();
            }
            this.peerIndex=0;
            peerServer=new PeerServer(this.port, new String(this.infoHash, "ISO-8859-1"), this.fileManager, this);
            peerServer.start();


            for (Map<String,Object> peer : peers){
                try{
                    
                    Socket socket=new Socket((String) peer.get("ip"), (int) peer.get("port"));
                    PeerConnection peerConnection=new PeerConnection(socket, new String(this.infoHash, "ISO-8859-1"), fileManager, this);
                    new Thread(peerConnection).start();
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
        public synchronized void peerLeave(PeerConnection peerConnection, String peerId){
            peerConnections.remove(peerConnection);
            connectionMap.remove(peerId);

        }
        public synchronized void addConnection(PeerConnection peerConnection) throws IOException {
            if (connectionMap.get(peerConnection.peerId)==null){
                peerConnections.add(peerConnection);
                connectionMap.put(peerConnection.peerId, peerConnection);
                peerConnection.peerIndex=this.peerIndex++;
            }
            //else peerConnection.dropConnection();
        }
        public boolean verifyPeerId(String handshakePeerId){
            for (Map<String, Object> peer: peers) {
                if (handshakePeerId.equals(peer.get("peer_id"))) return true;
            }
            return false;
        }
        private byte[] generatePeerId() {
            Random random = new Random();
            byte[] peerId= new byte[20];
            random.nextBytes(peerId);
            return peerId;
        }
        
        private String buildURL() throws UnsupportedEncodingException {     
            try {
                String encodedInfoHash = URLHandle.encode(infoHash);
                System.out.println("hash: " + encodedInfoHash);
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
        public void sendHave(int pieceIndex) throws IOException{
            for (PeerConnection peerConnection : peerConnections) peerConnection.sendHave(pieceIndex);
        }
        public synchronized void assignPiece(PeerConnection peerConnection) throws IOException{

            System.out.println();
            if (gotAll){
                peerConnection.sendNotInterested();
            }
            else {
                List<Boolean> peerHave=peerConnection.peerHave;
                int rarest=Integer.MAX_VALUE;
                int findPiece=-1;
                boolean all1s=true;
                for (int i=0; i<peerHave.size(); ++i){
                    if (getStatus(i)!=1){
                        all1s=false;
                        if (peerHave.get(i) && pieceCounter.get(i)<rarest && getStatus(i)==0) {
                            findPiece=i;
                            rarest=pieceCounter.get(i);
                        }
                    }
                }
                if (all1s){
                    fileManager.bufferToFile(this.outputPath);
                    gotAll=true;
                    peerConnection.sendNotInterested();
                }
                else if (findPiece==-1) peerConnection.sendNotInterested();
                else{ 
                    setStatus(findPiece, 2);
                    peerConnection.startSendingRequest(findPiece);
                }
            }

        }
        private class ChokingManager extends Thread {
            private int optimisticallyUnchokedIndex = -1;
            public void run(){
                while (true){
                    try {
                        // unchoke 4 + 1, 4 highest download speed + 1 optimistic
                        //System.out.println("choking manager running");
                        if (peerConnections==null) {
                            Thread.sleep(1000);
                            continue;
                        }
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
        TorrentClient seeder = new TorrentClient(true);
        TorrentClient leecher1=new TorrentClient(true);
        TorrentClient leecher2=new TorrentClient(true);
        TorrentClient leecher3=new TorrentClient(true);
        TorrentClient leecher4=new TorrentClient(true);
        TorrentClient leecher5=new TorrentClient(true);

        try{
            String filename="testDir/dieubuonnhat.mp4";
            String torrentPath="torrent/audio.torrent";
            TorrentInfo.generateTorrentFile(filename, torrentPath);
            seeder.addSeeder(torrentPath, filename, 6880);
            leecher1.addTorrentFromFileContent(torrentPath, "testDir/video1.mp4", 6881);
            leecher2.addTorrentFromFileContent(torrentPath, "testDir/video2.mp4", 6882);
            leecher3.addTorrentFromFileContent(torrentPath, "testDir/video3.mp4", 6883);
            leecher4.addTorrentFromFileContent(torrentPath, "testDir/video4.mp4", 6884);
            leecher5.addTorrentFromFileContent(torrentPath, "testDir/video5.mp4", 6885);
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
}
