package peer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class PeerServer {
    private final int port;
    private final String infoHash;
    private final String expectedPeerId;
    private FileManager fileManager;
    public PeerServer(int port, String infoHash, String expectedPeerId, FileManager fileManager) {
        this.port = port;
        this.infoHash = infoHash;
        this.expectedPeerId = expectedPeerId;
        this.fileManager=fileManager;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Peer B listening on port " + port);
            while (true) {
                // Accept an incoming connection from Peer A
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " + clientSocket.getInetAddress());

                // Spawn a new PeerConnection for each accepted connection
                PeerConnection peerConnection = new PeerConnection(clientSocket, infoHash, expectedPeerId, fileManager);
                new Thread(peerConnection).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        //int port = 6881; // Port on which Peer B will listen
        //String infoHash = "someInfoHash";
        //String expectedPeerId = "peerBId";
        //PeerServer peerServer = new PeerServer(port, infoHash, expectedPeerId);
        //peerServer.start();
    }
}
