
// CLI Interface
import java.io.IOException;
import java.util.Scanner;

import torrent.TorrentInfo;
import tracker.*;

public class Main {
    private static boolean running=true;
    public static void startInputThread(TorrentClient torrentClient) {
        Thread inputThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (running) {
                String input="";
                while (!input.equals("1") && !input.equals("2") && !input.equalsIgnoreCase("exit")){
                    System.out.print("Enter action (1=download, 2=upload): ");
                    input = scanner.nextLine();
                }                
                if (input.equals("1")){
                    System.out.print("Enter torrent file path: ");
                    input=scanner.nextLine();
                    if (input.equalsIgnoreCase("exit")) continue;
                    String torrentPath=input;
                    System.out.print("Enter download path: ");
                    input=scanner.nextLine();
                    if (input.equalsIgnoreCase("exit")) continue;
                    String downloadPath=input;
                    System.out.print("Enter port to download: ");
                    input=scanner.nextLine();
                    if (input.equalsIgnoreCase("exit")) continue;
                    int port=Integer.parseInt(input);
                    try {
                        torrentClient.addTorrentFromFileContent(torrentPath, downloadPath, port);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else if (input.equals("2")){
                    System.out.print("Enter upload file path: ");
                    input=scanner.nextLine();
                    if (input.equalsIgnoreCase("exit")) continue;
                    String uploadPath=input;
                    System.out.print("Enter torrent path where the system will generate: ");
                    input=scanner.nextLine();
                    if (input.equalsIgnoreCase("exit")) continue;
                    String torrentPath=input;
                    System.out.print("Enter port to upload: ");
                    input=scanner.nextLine();
                    if (input.equalsIgnoreCase("exit")) continue;
                    int port=Integer.parseInt(input);
                    try{
                        TorrentInfo.generateTorrentFile(input, torrentPath);
                        torrentClient.addSeeder(torrentPath,uploadPath,port);
                    }
                    catch (IOException e){
                        e.printStackTrace();
                    }
                }
                else if (input.equalsIgnoreCase("exit")) {
                    running=false;
                }
            }
            scanner.close();
            System.out.println("Program ended.");
        });
        inputThread.start();
    }


    public static void main(String[] args)  {
        TorrentClient torrentClient=new TorrentClient(true);
        startInputThread(torrentClient);
    }
}

