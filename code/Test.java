import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.net.*;

import util.*;

class Shit {
    public int a;
}
class BullShit {
    public Shit shit;
    BullShit(Shit shit){
        this.shit=shit;
    }
}

public class Test {
    static byte[] generatePeerId() {
        Random random = new Random();
        byte[] peerId= new byte[20];
        random.nextBytes(peerId);
        return peerId;
    }
    static private Map<String, String> parseQueryParams(String query) throws IOException {
            Map<String, String> params = new HashMap<>();
            if (query != null) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2) {
                        String key = new String(URLHandle.decode(keyValue[0]));
                        String value = new String(URLHandle.decode(keyValue[1]));
                        if (key=="info_hash") System.out.println("hash: " +value);
                        params.put(key, value);
                    }
                }
            }
            return params;
        }
    public static void main(String[] args) throws IOException {
        // byte[] random=generatePeerId();
        // String str1=new String(random);
        // byte[] random2=str1.getBytes();
        // String str2=new String(random2);
        // String str2encoded=URLHandle.encode(random2);
        // String str3=new String(URLHandle.decode(str2encoded));
        // System.out.println(str1);
        // System.out.println(str2);
        // System.out.println(str2encoded);
        // System.out.println(str3);
        // System.out.println(str3.length());
        //System.out.println( URLHandle.decode("%2B.%07%F6%07%E4x%D1G%0A%E6%C3%F1%BD%EEf%E2%B1%C8%01"));
        //System.out.println( (new String(random) == str1) );
        Shit shit=new Shit();
        shit.a=3;
        BullShit bs=new BullShit(shit);
        shit.a=5;
        System.out.println(bs.shit.a);
    }
}
