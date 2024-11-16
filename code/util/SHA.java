package util;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA {
    // generate SHA-1 hash for a given file (any type)
    public static String generateSHA1Hash(File file) {
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
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            System.err.println("Error computing SHA-1 hash: " + e.getMessage());
            return null;
        }
    }
    // generate SHA-1 hash for byte[]
    public static String generateSHA1Hash(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(input);
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            
            return hexString.toString();
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

    
    public static void main(String[] args) {
        File file = new File("testDir/random.txt"); 
        String sha1Hash = generateSHA1Hash(file);
        if (sha1Hash != null) {
            System.out.println("SHA-1 Hash: " + sha1Hash);
        }
    }
}
