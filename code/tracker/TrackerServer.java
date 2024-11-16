package tracker;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import peer.Peer;
import util.BEncode;

public class TrackerServer {

    private static final int PORT = 8080;
    private static final Map<String, Map<String, Peer>> torrents = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/announce", new AnnounceHandler());
        System.out.println("Tracker is running on port " + PORT);
        server.start();
    }

    static class AnnounceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }


            // Parse query parameters
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQueryParams(query);

            // Retrieve and decode infoHash
            String infoHashUrlEncoded = params.get("info_hash");

            if (infoHashUrlEncoded == null || infoHashUrlEncoded.isEmpty()) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            // Decode the URL-encoded info_hash
            String infoHashHex = URLDecoder.decode(infoHashUrlEncoded, StandardCharsets.UTF_8);
            
            // Validate the decoded infoHash if needed (e.g., check if it's a valid hex string)
            if (infoHashHex.length() != 40) { // SHA-1 hash should be 40 characters long
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            String peerId = params.get("peer_id");
            int port = Integer.parseInt(params.get("port"));
            long uploaded = Long.parseLong(params.getOrDefault("uploaded", "0"));
            long downloaded = Long.parseLong(params.getOrDefault("downloaded", "0"));
            long left = Long.parseLong(params.getOrDefault("left", "0"));
            String event = params.getOrDefault("event", "");

            // Manage peer list for the torrent
            torrents.putIfAbsent(infoHashHex, new ConcurrentHashMap<>());
            Map<String, Peer> peerMap = torrents.get(infoHashHex);

            if ("stopped".equals(event)) {
                peerMap.remove(peerId);
            } else {
                Peer peer = new Peer(peerId, exchange.getRemoteAddress().getAddress().getHostAddress(), port, uploaded, downloaded, left);
                peerMap.put(peerId, peer);
            }

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("interval", 1800);

            int complete = 0;  // Number of seeders
            int incomplete = 0; // Number of leechers
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
                Map<String, Object> peerInfo = new HashMap<>();
                peerInfo.put("peer id", peer.getPeerId());
                peerInfo.put("ip", peer.getIP());
                peerInfo.put("port", peer.getPort());
                
                peerList.add(peerInfo);
            }
            responseMap.put("peers", peerList);



            String response = BEncode.encode(responseMap);
            System.out.println("here");
            System.out.println("Encoded Response: " + response);

            // Send response headers
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }

        }

        // Utility method to parse query parameters
        private Map<String, String> parseQueryParams(String query) throws IOException {
            Map<String, String> params = new HashMap<>();
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2) {
                        String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                        String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                        params.put(key, value);
                    }
                }
            }
            return params;
        }
    }
}
