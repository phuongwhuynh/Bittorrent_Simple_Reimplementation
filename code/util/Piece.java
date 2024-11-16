package util;
import java.io.*;
import java.util.*;

public class Piece {
    public File piece;
    public int length;
    public int pieceIndex;
    public String hashSHA1;

    public static List<String> piecesSHA1(String sourceFilePath) throws IOException {
        File sourceFile = new File(sourceFilePath);
        long totalSize = sourceFile.length();
        int numPieces = (int) Math.ceil((double) totalSize / Conf.pieceLength);

        List<String> hash = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(sourceFile)) {
            byte[] buffer = new byte[Conf.pieceLength];
            for (int i = 0; i < numPieces; i++) {
                int bytesRead = fis.read(buffer);
                if (bytesRead == -1) break; // End of file

                String hashCode=SHA.generateSHA1Hash(buffer);
                hash.add(hashCode);
            }
        }
        return hash;
    }

}
