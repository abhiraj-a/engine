package com.Engine.Service;

import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

@Component
public class HashRouter {

    private final SortedMap<Long,String> ring =new TreeMap<>();
    private static final int VIRTUAL_NODES=100;

    public void updateCluster(List<String> nodes){
        ring.clear();
        for(String node:nodes){
            for (int i=0;i<VIRTUAL_NODES;i++){
                long hash = hash(node+"-VN"+i);
                ring.put(hash,node);
            }
        }
    }

    public String getOwnerNode(String clientId){
        if(ring.isEmpty()) return null;
        long hash = hash(clientId);
        SortedMap<Long, String> tail = ring.tailMap(hash);
        long nodeHash = tail.isEmpty()? ring.firstKey() : tail.firstKey();
        return ring.get(nodeHash);
    }

    private long hash(String s){
        try {
            MessageDigest digest=MessageDigest.getInstance("MD5");
            byte[] bytes  =s.getBytes(StandardCharsets.UTF_8);
            digest.digest(bytes);
            return ((long) (bytes[3] & 0xFF) << 24) |
                    ((long) (bytes[2] & 0xFF) << 16) |
                    ((long) (bytes[1] & 0xFF) << 8) |
                    ((long) (bytes[0] & 0xFF));

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}
