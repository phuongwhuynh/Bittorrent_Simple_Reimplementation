package tracker;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
//import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import peer.Peer;
import util.BEncode;
import util.URLHandle;

public class TrackerServer {

    private static final int PORT = 8080;
    private static final Map<String, Map<String, Peer>> torrents = new ConcurrentHashMap<>();

    static class AnnounceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }


            String query = exchange.getRequestURI().getRawQuery();
            Map<String, String> params = parseQueryParams(query);

            // Retrieve and decode infoHash
            // String infoHashUrlEncoded = params.get("info_hash");

            // if (infoHashUrlEncoded == null || infoHashUrlEncoded.isEmpty()) {
            //     exchange.sendResponseHeaders(400, -1);
            //     return;
            // }

            // // Decode the URL-encoded info_hash
            // String infoHash = new String(URLHandle.decode(infoHashUrlEncoded));
            String infoHash=params.get("info_hash");
            if (infoHash.length() != 20) { // SHA-1 hash should be 20 bytes long
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            // String peerIdEncoded=params.get("peer_id");
            // if (peerIdEncoded == null || peerIdEncoded.isEmpty()){
            //     exchange.sendResponseHeaders(402, -1);
            //     return;
            // }
            // String peerId = new String(URLHandle.decode(peerIdEncoded));
            String peerId=params.get("peer_id");
            if (peerId.length() != 20){
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            int port = Integer.parseInt(params.get("port"));
            long uploaded = Long.parseLong(params.getOrDefault("uploaded", "0"));
            long downloaded = Long.parseLong(params.getOrDefault("downloaded", "0"));
            long left = Long.parseLong(params.getOrDefault("left", "0"));
            String event = params.getOrDefault("event", "");

            // Manage peer list for the torrent
            torrents.putIfAbsent(infoHash, new ConcurrentHashMap<>());
            Map<String, Peer> peerMap = torrents.get(infoHash);

            if ("stopped".equals(event)) {
                peerMap.remove(peerId);
            } else {
                //IP address: exchange.getRemoteAddress().getAddress().getHostAddress();
                Peer peer = new Peer(peerId.getBytes("ISO-8859-1"), exchange.getRemoteAddress().getAddress().getHostAddress(), port, uploaded, downloaded, left);
                peerMap.put(peerId, peer);
            }

            Map<String, Object> responseMap = new HashMap<>();

            int complete = 0;  
            int incomplete = 0; 
            for (Peer peer : peerMap.values()) {
                if (peer.getLeft() == 0) {
                    complete++; // Seeder
                } else {
                    incomplete++; // Leecher
                }
            }

            responseMap.put("complete", complete);
            responseMap.put("incomplete", incomplete);
            List<Map<String, Object>> peerList = new ArrayList<>();

            for (Peer peer : peerMap.values()) {
                if (new String(peer.getPeerId(), "ISO-8859-1").equals(peerId)) {
                    continue;
                }
                Map<String, Object> peerInfo = new HashMap<>();
                peerInfo.put("peer_id", peer.getPeerId());
                peerInfo.put("ip", peer.getIP());
                peerInfo.put("port", peer.getPort());
                
                peerList.add(peerInfo);
            }
            responseMap.put("peers", peerList);

            //if peerList too small (first initiated torrent) put interval time short so that peer can discover each other.
            // if (peerList.size()>10) responseMap.put("interval", 1800);
            // else if (peerList.size() >5) responseMap.put("interval", 60);
            // else if (peerList.size() >2) responseMap.put("interval", 30);
            // else responseMap.put("interval", 10);
            if (peerList.size()==0) responseMap.put("interval", 10);
            else responseMap.put("interval", 1800);

            String response = BEncode.encode(responseMap);
            System.out.println("Encoded Response: " + response);

            // Send response headers
            byte[] responseBytes = response.getBytes("ISO-8859-1");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }

        }

        private Map<String, String> parseQueryParams(String query) throws IOException, UnsupportedEncodingException {
            Map<String, String> params = new HashMap<>();
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2) {
                        String key = new String(URLHandle.decode(keyValue[0]), "ISO-8859-1");
                        String value = new String(URLHandle.decode(keyValue[1]), "ISO-8859-1");
                        params.put(key, value);
                    }
                }
            }
            return params;
        }
    }
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/announce", new AnnounceHandler());
        System.out.println("Tracker is running on port " + PORT);
        server.start();
    }
}
