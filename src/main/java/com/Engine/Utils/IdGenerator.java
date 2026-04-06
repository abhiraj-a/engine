package com.Engine.Utils;

import java.security.SecureRandom;

public class IdGenerator {

    private static final String ALPHA="abcdefghijklmnopqrstuwvxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_";
    private static final SecureRandom secureRandom =new SecureRandom();
    public static String generate(){
        StringBuilder sb =new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHA.charAt(secureRandom.nextInt(ALPHA.length())));
        }
        return sb.toString();
    }
}
