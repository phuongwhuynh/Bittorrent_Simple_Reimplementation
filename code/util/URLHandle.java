package util;

import java.io.ByteArrayOutputStream;

public class URLHandle {
    public static String encode(byte[] binaryData) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : binaryData) {
            // Allowed characters: 0-9, a-z, A-Z, '.', '-', '_', '~' can be added directly
            if ((b >= '0' && b <= '9') ||
                (b >= 'a' && b <= 'z') ||
                (b >= 'A' && b <= 'Z') ||
                b == '.' || b == '-' || b == '_' || b == '~') {
                encoded.append((char) b);
            } else {
                // Percent-encode other bytes
                encoded.append(String.format("%%%02X", b));
            }
        }
        return encoded.toString();
    }
    public static byte[] decode(String encodedString) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        
        for (int i = 0; i < encodedString.length(); i++) {
            char c = encodedString.charAt(i);
            if (c == '%') {
                // Decode the next two hex characters as a single byte
                String hex = encodedString.substring(i + 1, i + 3);
                int byteValue = Integer.parseInt(hex, 16);
                byteArrayOutputStream.write(byteValue);
                i += 2; // Skip the two hex characters
            } else {
                // Directly write allowed characters (0-9, a-z, A-Z, '.', '-', '_', '~')
                byteArrayOutputStream.write((byte) c);
            }
        }
        
        return byteArrayOutputStream.toByteArray();
    }
}
