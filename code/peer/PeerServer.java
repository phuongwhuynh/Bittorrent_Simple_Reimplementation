package peer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import tracker.TorrentClient;

public class PeerServer extends Thread {
    private final int port;
    private final String infoHash;
    private FileManager fileManager;
    private TorrentClient.TorrentState parent;
    private ServerSocket serverSocket;
    public PeerServer( int port, String infoHash, FileManager fileManager, TorrentClient.TorrentState parent) {
        this.port = port;
        this.infoHash = infoHash;
        this.fileManager=fileManager;
        this.parent=parent;
    }

    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            this.serverSocket=serverSocket;
            System.out.println("Client server listening on port " + port);
            while (!serverSocket.isClosed()) {
                // accept an incoming connection
                try{
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Accepted connection from " + clientSocket.getInetAddress());
                    
                    // spawn a new PeerConnection for each accepted connection
                    PeerConnection peerConnection = new PeerConnection(clientSocket, infoHash, fileManager, parent);
                    new Thread(peerConnection).start();
                }
                catch (SocketException se){}
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void dropServer() throws IOException{
        serverSocket.close();
    }
    public static void main(String[] args) {
        //int port = 6881; // Port on which Peer B will listen
        //String infoHash = "someInfoHash";
        //String expectedPeerId = "peerBId";
        //PeerServer peerServer = new PeerServer(port, infoHash, expectedPeerId);
        //peerServer.start();
    }
}
