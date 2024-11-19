package util;

public class Conf {
    //public int pieceLength=1048576;   //1MB  
    public static final int pieceLength=1024;        //1KB for testing
    public static final String announce="http://localhost:8080/announce";
    public static final int maxPeers=30;
    public static final int timeWindow=60*1000; //only count last 1 minute to calculate download speed
}
