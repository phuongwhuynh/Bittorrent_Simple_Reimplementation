package util;
import java.util.*;

public class BEncode {
    public static String encodeNumber(Number num) {
        if (num instanceof Integer) {
            return 'i' + String.valueOf(num.intValue()) + 'e';
        } else if (num instanceof Long) {
            return 'i' + String.valueOf(num.longValue()) + 'e';
        } else if (num instanceof Short) {
            return 'i' + String.valueOf(num.shortValue()) + 'e';
        } else {
            throw new IllegalArgumentException("Unsupported numeric type (floating-point numbers not allowed): " + num.getClass().getName());
        }
    }

    public static String encodeString(String str) {
        return String.valueOf(str.length()) + ':' + str;
    }

    // public static String encodeByteArray(byte[] bytes) {
    //     StringBuilder hexString = new StringBuilder();
    //     for (byte b : bytes) {
    //         hexString.append(String.format("%02x", b));
    //     }
    //     return bytes.length + ":" + hexString.toString();
    // }
    
    public static String encodeList(List<?> list) {
        String ans = "";
        for (int i = 0; i < list.size(); ++i) {
            Object o = list.get(i);
            ans += encode(o);
        }
        return 'l' + ans + 'e';
    }

    public static String encodeMap(Map<?, ?> map) {
        String ans = "";
        for (Map.Entry<?, ?> ele : map.entrySet()) {
            Object key = ele.getKey();
            Object value = ele.getValue();
            ans += encode(key);
            ans += encode(value);
        }
        return 'd' + ans + 'e';
    }

    public static String encode(Object o) {
        String ans = "";
        if (o instanceof String) {
            String str = (String) o;
            ans += encodeString(str);
        } 
        // else if (o instanceof byte[]){
        //     byte[] bytes=(byte[]) o;
        //     ans+=encodeByteArray(bytes);
        // }
        else if (o instanceof Number) {
            ans += encodeNumber((Number) o);
        } 
        else if (o instanceof List) {
            List<?> list = (List<?>) o;
            ans += encodeList(list);
        } 
        else if (o instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) o;
            ans += encodeMap(map);
        } 
        else {
            throw new IllegalArgumentException("Unsupported type: " + o.getClass().getName());
        }
        return ans;
    }

    // public static void main(String[] args) {
    //     System.out.println(encode("hello"));
    // }
}
