import java.io.*;
import java.net.*;

public class SocketServer {
    public static void main(String[] args) throws IOException {
        // Server 1 listening on port 6881
        ServerSocket serverSocket1 = new ServerSocket(6881);
        System.out.println("Server 1 started on port 6881");
        Socket socket1 = serverSocket1.accept();
        DataInputStream in1 = new DataInputStream(socket1.getInputStream());
        DataOutputStream out1 = new DataOutputStream(socket1.getOutputStream());
        
        // Server 2 listening on port 6882
        ServerSocket serverSocket2 = new ServerSocket(6882);
        System.out.println("Server 2 started on port 6882");
        Socket socket2 = serverSocket2.accept();
        DataInputStream in2 = new DataInputStream(socket2.getInputStream());
        DataOutputStream out2 = new DataOutputStream(socket2.getOutputStream());

        // Example of data transfer between the two sockets
        byte[] data = new byte[14];
        in1.readFully(data);
        System.out.println("Received message from Socket 1: " + new String(data));
        
        out2.write("Message received from Server 1".getBytes());
    }
}
