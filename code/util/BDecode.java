package util;
import java.util.*;
class DecodePackage{
    public int index;
    public int length;
    public String code;
    public DecodePackage(String str){
        index=0;
        length=str.length();
        code=str;
    }
    public char getCur(){
        return code.charAt(index);
    }
}
public class BDecode {
    private static int decodeInteger(DecodePackage dp){
        String num="";
        dp.index++;
        while (dp.getCur()!='e'){
            num+=dp.getCur();
            ++dp.index;
        }
        dp.index++;
        return Integer.parseInt(num);
    }
    private static String decodeString(DecodePackage dp){
        String len="";
        while (dp.getCur()!=':'){
            len+=dp.getCur();
            ++dp.index;
        }
        dp.index++;
        String ans="";
        int size=Integer.parseInt(len);
        for (int i=0; i<size; ++i) {
            ans+=dp.getCur();
            dp.index++;
        }
        return ans;
    }
    private static List<Object> decodeList(DecodePackage dp){
        List<Object> ans=new ArrayList<>();
        dp.index++;
        while (dp.getCur()!='e'){
            ans.add(decodeObject(dp));
        }
        dp.index++;
        return ans;
    }
    private static Map<Object,Object> decodeMap(DecodePackage dp){
        Map<Object,Object> ans=new LinkedHashMap<>();
        dp.index++;
        while (dp.getCur()!='e'){
            Object key=decodeObject(dp);
            Object value=decodeObject(dp);
            ans.put(key, value);
        }
        dp.index++;
        return ans;
    }
    private static Object decodeObject(DecodePackage dp){
        if (dp.index>=dp.length) return null;
        char first=dp.getCur();
        if (first=='i'){
            return decodeInteger(dp);
        }
        else if (first=='0'){
            decodeString(dp);
            return null;
        }
        else if (first>'0' && first <='9'){
            return decodeString(dp);
        }
        else if (first=='l'){
            return decodeList(dp);
        }
        else if (first=='d'){
            return decodeMap(dp);
        }   
        else throw new IllegalArgumentException("Not a bencode: "+first);
    }
    public static Object decode(String str){
        DecodePackage dp=new DecodePackage(str);
        return decodeObject(dp);
    }
    // public static void main(String args[]){
    //     List<Object> list = new LinkedList<>();
    //     list.add("Hello");
    //     list.add(123);         
    //     List<Object> list2=new ArrayList<>();
    //     list2.add("wtf");
    //     list2.add(1808);
    //     list.add(list2);
    //     Map<Object,Object> map=new LinkedHashMap<>();
    //     map.put(18,15);
    //     map.put("lp", 18);
    //     map.put(15, "st");
    //     map.put(list, "1808");
    //     String code=Encode.encode(map);
    //     System.out.println(code);
    //     System.out.println(Encode.encode(decode(code)));
    // }
}
