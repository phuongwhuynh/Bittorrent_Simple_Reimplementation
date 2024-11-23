package util;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;

public class SHA {
    // generate SHA-1 hash for a given file (any type)
    public static byte[] generateSHA1Hash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            
            byte[] buffer = new byte[1024];  // 1 KB buffer size
            int bytesRead;
            // open a file input stream to read the file in chunk
            try (FileInputStream fis = new FileInputStream(file)) {
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException | IOException e) {
            System.err.println("Error computing SHA-1 hash: " + e.getMessage());
            return null;
        }
    }
    // generate SHA-1 hash for byte[]
    public static byte[] generateSHA1Hash(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(input);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error: SHA-1 algorithm not found.");
            return null;
        }
    }
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    public static byte[] calculateInfoHash(Map<String, Object> info) throws UnsupportedEncodingException {
        try {
            // Re-encode the "info" dictionary to bencode format
            String infoEncoded = BEncode.encode(info); 
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] infoHashBytes = digest.digest(infoEncoded.getBytes("ISO-8859-1"));
            return infoHashBytes; // Convert byte array to hexadecimal string
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not found", e);
        }
    }

    public static byte[] piecesSHA1(String sourceFilePath) throws IOException {
        File sourceFile = new File(sourceFilePath);
        long totalSize = sourceFile.length();
        int numPieces = (int) Math.ceil((double) totalSize / Conf.pieceLength);

        byte[] concantenatedHash=new byte[numPieces * 20];

        try (FileInputStream fis = new FileInputStream(sourceFile)) {
            int currentPos=0;
            for (int i = 0; i < numPieces; i++) {
                byte[] buffer = new byte[Math.min(Conf.pieceLength,(int) totalSize-Conf.pieceLength*i)];

                int bytesRead = fis.read(buffer);
                if (bytesRead == -1) break; // End of file

                byte[] hashCode=SHA.generateSHA1Hash(buffer);
                System.arraycopy(hashCode, 0, concantenatedHash, currentPos, 20);
                currentPos+=20;
            }
        }
        return concantenatedHash;
    }
    
    public static void main(String[] args) throws UnsupportedEncodingException, IOException {
        // byte[] sha1Hash = piecesSHA1("testDir/random.txt");
        // if (sha1Hash != null) {
        //     System.out.println("SHA-1 Hash: " + new String(sha1Hash, "ISO-8859-1"));
        // }
        byte[][] pieces=new byte[3][];
        for (int i=0; i<3; ++i) pieces[i]=new byte[Math.min(Conf.pieceLength, 4215-i*Conf.pieceLength)];
        try (FileInputStream fileInputStream = new FileInputStream("testDir/random.txt")) {
            byte[] buffer = new byte[Conf.pieceLength];
            int bytesRead;
            
            for (int i = 0; i < 3; i++) {
                bytesRead = fileInputStream.read(buffer);
                if (bytesRead == -1) {
                    break; // End of file reached
                }
                
                if (bytesRead < Conf.pieceLength) {
                    pieces[i] = Arrays.copyOf(buffer, bytesRead);
                } else {
                    pieces[i] = buffer.clone();
                }
            }
            for (int i=0; i<3; ++i) System.out.println(new String(SHA.generateSHA1Hash(pieces[i]), "ISO-8859-1"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
