package com.aylanetworks.aylasdk.util;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.SecureRandom;


public class ObjectUtils {
    /**
     * Checks for equality for objects of any type. Either or both arguments may be null.
     *
     * @param <T> Generic object class type
     * @param o1 First object to compare
     * @param o2 Second object to compare
     *
     * @return the result of equals() if both objects are non-null, or true if both objects are
     * null, or false if one object is null and the other is non-null.
     */
    public static <T> boolean equals(T o1, T o2) {
        if ( o1 == null ) {
            return o2 == null;      // Two nulls are equal
        }
        else if ( o2 == null ) {
            return false;
        }
        return o1.equals(o2);
    }

    /**
     * Returns the provided string surrounded with double-quotes (").
     * @param string String to surround with quotes
     * @return the provided string surrounded with double-quotes
     */
    public static String quote(String string) {
        return "\"" + string + "\"";
    }

    public static String unquote(String string) {
        if(string != null) {
            return string.replaceAll("^\"|\"$", "");
        }
        return string;
    }

    public static String inputStreamToString(InputStream in) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, Charset.forName ("UTF-8")));

        StringBuilder sb = new StringBuilder(512);
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch(IOException e) {
            e.printStackTrace();
            return null;
        }

        return sb.toString();
    }

    public static String inputStreamToString(InputStream in, int length) {

        int bufferSize = 1024;
        char[] buffer = new char[bufferSize];
        final StringBuilder strBuilder = new StringBuilder();
        Reader inputStreamReader = null;
        try {
            inputStreamReader = new InputStreamReader(in, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        int totalBytes = 0;
        while (totalBytes < length) {
            int numBytes = 0;
            try {
                buffer = new char[bufferSize];
                numBytes = inputStreamReader.read(buffer, 0, buffer.length);
                totalBytes += numBytes;
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (numBytes < 0){
                break;
            }
            strBuilder.append(buffer, 0, numBytes);
        }
        return strBuilder.toString();
    }

    private final static SecureRandom __secureRandom = new SecureRandom();

    private final static String RANDOM_CHARSET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    public static String generateRandomToken(int length) {
        byte[] data = new byte[length];
        for ( int i = 0; i < length; i++ ) {
            data[i] = (byte)RANDOM_CHARSET.charAt((int)(Math.random() * RANDOM_CHARSET.length()));
        }
        return new String(data, Charset.forName("UTF-8"));
    }

    public int getRandomInt() {
        return __secureRandom.nextInt();
    }

    public void fillRandom(byte[] byteBuf) {
        __secureRandom.nextBytes(byteBuf);
    }

    /**
     * Method used to mask info in logs. All characters other than first and last will be
     * replaced by "*".
     * @param text to be masked
     * @return anonymized text. Returns the input value for null or empty strings.
     */
    public static String getAnonymizedText(String text){
        if(text != null && !text.isEmpty()){
            if(text.length() > 2){
                StringBuilder builder = new StringBuilder(text);
                builder.replace(1, text.length()-1, "*");
                return builder.toString();
            } else{
                return "*";
            }
        }
        return text;
    }

    /**
     * Returns a string value with array elements separated by the delimiter character.
     * @param array String array
     * @param delimiter Delimiter character to separate elements of the array
     * @return String value with array elements separated by the delimiter character.
     */
    public static String getDelimitedString(String[] array, String delimiter){
        if(array == null){
            return null;
        }
        int length = array.length;
        StringBuilder strBuilder = new StringBuilder(length * 16);
        for(int i=0; i < length; i++){
            if(i != 0) {
                strBuilder.append(delimiter);
            }
            strBuilder.append(array[i]);
        }
        return strBuilder.toString();

    }
}
