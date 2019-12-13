//
//  AylaSDK
//
//  Created by Daniel Myers on 11/25/12.
//  Copyright (c) 2012 Ayla Networks. All rights reserved.
//

package com.aylanetworks.aylasdk.lan;

import android.text.TextUtils;
import android.util.Base64;

import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.error.AuthError;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.util.DateUtils;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;


/**
 * Helper class used for private key exchange done during WiFi setup
 */
class AylaEncryption {
    protected static String TYPE_SETUP_RSA = "wifi_setup_rsa";

    protected int version;
    protected int proto_1 = -1;
    protected int key_id_1 = -1;
    protected String sRnd_1;
    protected String sRnd_2;
    protected Number nTime_1 = -1;
    protected Number nTime_2 = -1L;
    protected String sTime_1 = null;
    protected String sTime_2 = null;

    private String createdAt = null;
    private int sessionId = -1;

    private String sLanKey;
    private byte[] bLanKey = null;

    private byte[] lastByte = new byte[1];
    private byte[] appSignKey = null;
    private byte[] appCryptoKey = null;
    private byte[] appIvSeed = null;
    protected byte[] devSignKey = null;
    private byte[] devCryptoKey = null;
    private byte[] devIvSeed = null;

    private Cipher eCipher = null;
    private Cipher dCipher = null;
    private java.security.Key eSkey = null;
    private java.security.Key dSkey = null;

    private static int nextSessionID = 1;

    private WeakReference<AylaDevice> _device;

    protected AylaEncryption(AylaDevice device) {
        _device = new WeakReference<>(device);
    }

    protected AylaError generateSessionKeys(String sessionType, byte[] secData) {
        // (NSDictionary *)param sRnd1:(NSString*)_sRnd1 nTime1:(NSNumber *)_nTime1 sRnd2:(NSString*)_sRnd2 nTime2:(NSNumber*)_nTime2;
        //int generateSessionKeys(, String sRnd_1, Number nTime_1, String sRnd_2, Number nTime_2) {
        byte[] bRnd_1;
        byte[] bRnd_2;
        byte[] bTime_1;
        byte[] bTime_2;

        createdAt = DateUtils.getISO8601DateFormat().format(new Date());
        sessionId = AylaEncryption.nextSessionID++;

        AylaDevice device = _device.get();
        if (device == null) {
            return new AylaError(AylaError.ErrorType.InvalidArgument, "No device associated with " +
                    "encryption");
        }

        try {
            bRnd_1 = sRnd_1.getBytes("UTF-8");
            bRnd_2 = sRnd_2.getBytes("UTF-8");

            if (sessionType != null) {
                if (TextUtils.equals(sessionType, TYPE_SETUP_RSA)) {
                    bLanKey = secData;
                } else {
                    return new InvalidArgumentError("Unsupported sessionType " + sessionType);
                }
            } else if (device.getLanConfig() != null && device.getLanConfig().lanipKey != null) {
                sLanKey = device.getLanConfig().lanipKey;
                bLanKey = sLanKey.getBytes("UTF-8");
            } else {
                return new InvalidArgumentError("No LAN config found on device");
            }
        } catch (UnsupportedEncodingException e) {
            return new InvalidArgumentError("Unsupported string encoding", e);
        }

        sTime_1 = nTime_1.toString();
        sTime_2 = nTime_2.toString();
        try {
            bTime_1 = sTime_1.getBytes("UTF-8");
            bTime_2 = sTime_2.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return new InvalidArgumentError("Unsupported string encoding", e);
        }

        // generate session keys
        byte[] bTempKey;

        // App Signing key:    <random_1> + <random_2> + <time_1> + <time_2> + 0
        // App Encrypting key: <random_1> + <random_2> + <time_1> + <time_2> + 1
        // App IV CBC seed:    <random_1> + <random_2> + <time_1> + <time_2> + 2
        lastByte[0] = 48;
        bTempKey = concat(bRnd_1, bRnd_2, bTime_1, bTime_2, lastByte);
        appSignKey = hmacForKeyAndData(bLanKey, concat(hmacForKeyAndData(bLanKey, bTempKey), bTempKey));

        lastByte[0] = 49;
        bTempKey = concat(bRnd_1, bRnd_2, bTime_1, bTime_2, lastByte);
        appCryptoKey = hmacForKeyAndData(bLanKey, concat(hmacForKeyAndData(bLanKey, bTempKey), bTempKey));

        lastByte[0] = 50;
        bTempKey = concat(bRnd_1, bRnd_2, bTime_1, bTime_2, lastByte);
        byte[] seed = hmacForKeyAndData(bLanKey, concat(hmacForKeyAndData(bLanKey, bTempKey), bTempKey));
        if ( seed == null ) {
            return new AuthError("Failed to generate app seed", null);
        }
        appIvSeed = Arrays.copyOfRange(seed, 0, 16);

        // Device Signing key:    <random_2> + <random_1> + <time_2> + <time_1> + 0
        // Device Encrypting key: <random_2> + <random_1> + <time_2> + <time_1> + 1
        // Device IV CBC seed:    <random_2> + <random_1> + <time_2> + <time_1> + 2

        lastByte[0] = 48;
        bTempKey = concat(bRnd_2, bRnd_1, bTime_2, bTime_1, lastByte);
        devSignKey = hmacForKeyAndData(bLanKey, concat(hmacForKeyAndData(bLanKey, bTempKey), bTempKey));

        lastByte[0] = 49;
        bTempKey = concat(bRnd_2, bRnd_1, bTime_2, bTime_1, lastByte);
        devCryptoKey = hmacForKeyAndData(bLanKey, concat(hmacForKeyAndData(bLanKey, bTempKey), bTempKey));

        lastByte[0] = 50;
        bTempKey = concat(bRnd_2, bRnd_1, bTime_2, bTime_1, lastByte);
        seed = hmacForKeyAndData(bLanKey, concat(hmacForKeyAndData(bLanKey, bTempKey), bTempKey));
        if ( seed == null ) {
            return new AuthError("Failed to generate device seed", null);
        }
        devIvSeed = Arrays.copyOfRange(seed, 0, 16);

        // instantiate cipher objects
        try {
            // encrypt
            eCipher = Cipher.getInstance("AES/CBC/NoPadding");
            eSkey = new javax.crypto.spec.SecretKeySpec(appCryptoKey, "AES");
            eCipher.init(Cipher.ENCRYPT_MODE, eSkey, new IvParameterSpec(appIvSeed));

            // decrypt
            dCipher = Cipher.getInstance("AES/CBC/NoPadding");
            dSkey = new javax.crypto.spec.SecretKeySpec(devCryptoKey, "AES");
            dCipher.init(Cipher.DECRYPT_MODE, dSkey, new IvParameterSpec(devIvSeed));
        } catch (NoSuchPaddingException e) {
            return new AuthError("Padding error while initializing ciphers", e);
        } catch (InvalidAlgorithmParameterException e) {
            return new AuthError("Invalid algorithm parameter while initializing ciphers", e);
        } catch (NoSuchAlgorithmException e) {
            return new AuthError("No such algorithm", e);
        } catch (InvalidKeyException e) {
            return new AuthError("Invalid key", e);
        }

        return null;
    }

    private static int __sequenceNumber = 0;
    protected String encryptEncapsulateSign(String jsonProperty) {
        String jsonBase64;
        String jsonText;

        String jsonText0 = "{\"enc\":" + "\"";

        String jsonText1 = "";
        jsonText1 = jsonText1 + "{\"seq_no\":" + __sequenceNumber++;
        jsonText1 = jsonText1 + ",\"data\":";
        if (jsonProperty != null) {

            jsonText1 = jsonText1 + jsonProperty;
        }

        jsonText1 = jsonText1 + "}";

        byte[] bJsonText1;
        try {
            bJsonText1 = jsonText1.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }

        // signature
        String jsonText2 = "";

        byte[] thisSign = AylaEncryption.hmacForKeyAndData(appSignKey, bJsonText1);

        jsonBase64 = Base64.encodeToString(thisSign, Base64.NO_WRAP);

        jsonText2 = jsonText2 + "\"sign\":" + "\"" + jsonBase64 + "\""; // add signature
        jsonText2 = jsonText2 + "}";

        // create a padded buffer for CBC cipher
        int len = jsonText1.length() + 1; // add one for nul termination
        int pad = len % 16; // 128 bit AES buffer
        pad = (pad > 0) ? (16 - pad) : pad;
        byte[] paddedBuffer = Arrays.copyOfRange(bJsonText1, 0, len + pad);

        // Encrypt the message using key and initialization vector derived during key generation, then base64 encode
        byte[] encrypted;
        encrypted = eCipher.update(paddedBuffer); // encrypt
        jsonBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP); // encode
        jsonText1 = jsonBase64 + "\",";

        jsonText = String.format("%s%s%s", jsonText0, jsonText1, jsonText2);
        return jsonText;
    }

    protected static byte[] decodeBase64(String encoded) {
        return Base64.decode(encoded, Base64.NO_WRAP); // base64 decodeBase64
    }

    // Base64 decodeBase64, then decrypt the message using key and initialization vector derived during key generation.
    protected String unencodeDecrypt(String encodedEncrypted) throws UnsupportedEncodingException {
        byte[] decoded;
        byte[] unEncrypt;
        String unEncrypted = null;
        if (encodedEncrypted != null) {
            decoded = Base64.decode(encodedEncrypted, Base64.NO_WRAP); // base64 decodeBase64
            unEncrypt = dCipher.update(decoded); // decrypt

            // strip buffer nuls if any, convert to a string
            int i = unEncrypt.length - 1;
            byte[] unEncryptNulTerm;
            while ((unEncrypt[i] == 0) && (i >= 0)) i--;
            unEncryptNulTerm = Arrays.copyOfRange(unEncrypt, 0, ++i);
            unEncrypted = new String(unEncryptNulTerm, 0, i, "UTF-8");
        }

        return unEncrypted;
    }

    protected static byte[] hmacForKeyAndData(byte[] key, byte[] data) {
        javax.crypto.Mac mac;
        try {
            mac = javax.crypto.Mac.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }

        javax.crypto.spec.SecretKeySpec secret = new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256");
        try {
            mac.init(secret);
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            return null;
        }
        return mac.doFinal(data);
    }

    protected static byte[] concat(byte[]... arrays) {
        // Determine the length of the result array
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }

        // create the result array
        byte[] result = new byte[totalLength];

        // copy the source arrays into the result array
        int currentIndex = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, currentIndex, array.length);
            currentIndex += array.length;
        }

        return result;
    }

    // generate an alphanumeric random number
   protected static String randomToken(int length) {
        String token = "";
        char c;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < length; i++) {
            c = chars.charAt((int) (Math.random() * chars.length()));
            token += c;
        }

        return token;
    }
}






