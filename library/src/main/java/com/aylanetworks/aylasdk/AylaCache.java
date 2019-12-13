package com.aylanetworks.aylasdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.aylanetworks.aylasdk.auth.AylaAuthorization;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.util.encoders.Base64;

import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Arrays;
import java.util.HashMap;
import java.util.zip.CRC32;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Android_Aura
 * <p/>
 * Copyright 2016 Ayla Networks, all rights reserved
 */

/**
 * AylaCache class controls caching in Ayla SDK. AylaCache allows devices and properties to be
 * saved so they are available when service connectivity is not available. Typical use case is in
 * LAN-login feature (device access without service authentication).
 *
 * LAN configuration information is stored in an encrypted form tied to the session that was
 * active when the data was cached. As long as the last valid authentication was used to sign in
 * offline, the cache will properly decrypt any encrypted information saved from that session.
 */
public class AylaCache {
    private final static String LOG_TAG = "AYLA_CACHE";
    private final static String AYLA_CACHE_KEY = "com.aylanetworks.aylasdk.aylacache";
    private final static String AYLA_CACHED_DEVICES_PREFIX = "com.aylanetworks.aylasdk.devices";
    private final static String AYLA_CACHED_PROPERTIES_PREFIX = "com.aylanetworks.aylasdk" +
            ".properties";
    private final static String AYLA_CACHED_SETUP_PREFIX = "com.aylanetworks.aylasdk.setup";
    private final static String AYLA_CACHED_LANCONFIG_PREFIX = "com.aylanetworks.aylasdk.lanconfig";
    private final static String AYLA_CACHED_GROUP_PREFIX = "com.aylanetworks.aylasdk.group";
    private final static String AYLA_CACHED_NODE_PREFIX = "com.aylanetworks.aylasdk.node";

    private final static String AYLA_CRYPTO_SPEC = "AES/CBC/PKCS5Padding";

    public enum CacheType {
        DEVICE,
        PROPERTY,
        LAN_CONFIG,
        SETUP,
        GROUP,
        NODE
    }

    private boolean _isCachingEnabled;
    private WeakReference<AylaSessionManager> _sessionManagerRef;

    /**
     * Creates an AylaCache instance used to cache session data.
     */
    public AylaCache(AylaSessionManager sessionManager) {
        _sessionManagerRef = new WeakReference<>(sessionManager);
        _isCachingEnabled = true;
    }

    /**
     * Enable caching using AylaCache
     */
    public void enable(){
        _isCachingEnabled = true;
    }

    /**
     * Disable caching using AylaCache
     */
    public void disable(){
        _isCachingEnabled = false;
    }

    /**
     * Returns true if caching using AylaCache is enabled, false if disabled
     */
    public boolean isCachingEnabled(){
        return _isCachingEnabled;
    }

    /**
     * Clear all values stored in Ayla Cache.
     */
    public void clearAll(){
        SharedPreferences.Editor editor = AylaNetworks.sharedInstance().getContext()
                .getSharedPreferences(AYLA_CACHE_KEY, Context.MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();
    }

    /**
     * Save data to AylaCache when a complete cache key is known. Cache key can be obtained from
     * the method {@link AylaCache#getKey(CacheType, String)}.
     * Alternatively, use {@link AylaCache#save(CacheType, String, String)}
     * @param cacheKey Key to identify cache to which data is to be saved.
     * @param value Data to be stored. If this value is null, the key will be removed.
     */
    public void save(String cacheKey, String value){

        if(!isCachingEnabled()){
            return;
        }

        String lanCacheName = getKey(CacheType.LAN_CONFIG, null);
        if (cacheKey.startsWith(lanCacheName) && value != null) {
            // Encrypt the data before saving
            value = encrypt(value);
            if (value == null) {
                AylaLog.e(LOG_TAG, "Encryption failed- LAN config data will not be cached");
            }
        }

        SharedPreferences.Editor editor = AylaNetworks.sharedInstance().getContext()
                .getSharedPreferences(AYLA_CACHE_KEY, Context.MODE_PRIVATE).edit();
        if (value == null) {
            editor.remove(cacheKey);
        } else {
            editor.putString(cacheKey, value);
        }
        editor.apply();
    }

    private String encrypt(String data) {
        if (data == null) {
            return null;
        }

        SecretKey key = getCryptoKey();
        String result = null;
        Charset utf8 = Charset.forName("UTF-8");
        try {
            if (key != null) {
                Cipher cipher = Cipher.getInstance(AYLA_CRYPTO_SPEC);
                cipher.init(Cipher.ENCRYPT_MODE, key, getInitializationVector());
                byte[] encrypted = cipher.doFinal(data.getBytes(utf8));
                result = Base64.toBase64String(encrypted);
            }
        } catch (InvalidKeyException e) {
            AylaLog.e(LOG_TAG, "Invalid key trying to encrypt cache: " + e);
        } catch (NoSuchAlgorithmException nsa) {
            AylaLog.e(LOG_TAG, "No such algorithm trying to encrypt cache: " + nsa);
        } catch (NoSuchPaddingException nsp) {
            AylaLog.e(LOG_TAG, "No such padding trying to encrypt cache: " + nsp);
        } catch (IllegalBlockSizeException ibs) {
            AylaLog.e(LOG_TAG, "Illegal block size trying to encrypt cache: " + ibs);
        } catch (BadPaddingException bpe) {
            AylaLog.e(LOG_TAG, "Bad padding exception trying to encrypt cache: " + bpe);
        } catch (InvalidAlgorithmParameterException iap) {
            AylaLog.e(LOG_TAG, "Invalid parameter exception trying to encrypt cache: " + iap);
        }


        return result;
    }

    private String decrypt(String data) {
        if (data == null) {
            return null;
        }

        SecretKey key = getCryptoKey();
        String result = null;
        try {
            if (key != null) {
                Cipher cipher = Cipher.getInstance(AYLA_CRYPTO_SPEC);
                cipher.init(Cipher.DECRYPT_MODE, key, getInitializationVector());
                byte[] encryptedData = Base64.decode(data);
                byte[] decrypted = cipher.doFinal(encryptedData);
                result = new String(decrypted, Charset.forName("UTF-8"));
            }
        } catch (InvalidKeyException e) {
            AylaLog.e(LOG_TAG, "Invalid key trying to decrypt cache: " + e);
        } catch (NoSuchAlgorithmException nsa) {
            AylaLog.e(LOG_TAG, "No such algorithm trying to decrypt cache: " + nsa);
        } catch (NoSuchPaddingException nsp) {
            AylaLog.e(LOG_TAG, "No such padding trying to decrypt cache: " + nsp);
        } catch (IllegalBlockSizeException ibs) {
            AylaLog.e(LOG_TAG, "Illegal block size trying to decrypt cache: " + ibs);
        } catch (BadPaddingException bpe) {
            AylaLog.e(LOG_TAG, "Bad padding exception trying to decrypt cache: " + bpe);
        } catch (InvalidAlgorithmParameterException ipe) {
            AylaLog.e(LOG_TAG, "Invalid parameter exception trying to decrypt cache: " + ipe);
        }

        return result;
    }

    /**
     * Returns a SecretKey used to encrypt / decrypt LAN config data. This key is based on a hash
     * of the authorization object used for the session.
     * @return a SecretKey used for encrypting / decrypting LAN config data.
     */
    private SecretKey getCryptoKey() {
        SHA256Digest digest = new SHA256Digest();
        byte[] keyData = _sessionManagerRef.get().getAuthHeaderValue().getBytes();
        byte[] output = new byte[digest.getDigestSize()];
        digest.update(keyData, 0, keyData.length);
        digest.doFinal(output, 0);

        byte[] rawKey = Arrays.copyOf(output, 16); // Only 16 bytes are used for the key
        return new SecretKeySpec(rawKey, "AES");
    }

    /**
     * Generates an IV for crypto operations. Note that this is based off of the session's
     * authorization token, similar to the key. This is not a secure way of generating an IV, but
     * it will work for our purposes.
     *
     * @return the IV required for crypto operations
     */
    private IvParameterSpec getInitializationVector() {
        byte iv[] = new byte[16];
        SHA256Digest digest = new SHA256Digest();
        byte[] data = _sessionManagerRef.get().getAuthHeaderValue().getBytes();
        digest.update(data, 0, data.length);
        data = "lanconfig-iv-salt".getBytes();
        digest.update(data, 0, data.length);

        byte[] hashData = new byte[digest.getDigestSize()];
        digest.doFinal(hashData, 0);

        // Use 16 bytes of the hash for the IV
        return new IvParameterSpec(Arrays.copyOf(hashData, 16));
    }

    /**
     * Add data to AylaCache with cache type and unique id.
     * @param type Cache type
     * @param id to be appended to the cache type prefix for this entry. Required for property,
     *           lan_config and node types.
     * @param value Data to be stored. If this is null, the key will be removed.
     */
    public void save(CacheType type, String id, String value){
        if(type == null){
            AylaLog.d(LOG_TAG, "Data not saved to cache. key is null");
            return;
        }
        if(id == null || id.length() == 0){
            if(isIdRequired(type)){
                AylaLog.d(LOG_TAG, "Data not saved to cache. Id is null");
                return;
            }
        }
        String cacheKey = getKey(type, id);
        save(cacheKey, value);
    }

    /**
     * Get saved data from AylaCache.
     * @param key key for AylaCache entry.
     */
    public String getData(String key){
        SharedPreferences preferences = AylaNetworks.sharedInstance().getContext()
                .getSharedPreferences(AYLA_CACHE_KEY, Context.MODE_PRIVATE);
        String result = preferences.getString(key, null );
        String lanConfigPrefix = getKey(CacheType.LAN_CONFIG, null);
        if (key.startsWith(lanConfigPrefix) && result != null) {
            result = decrypt(result);
        }
        return result;
    }

    /**
     * Get cache key for retrieving data for this cache type.
     * @param type Cache type
     * @param id Unique id to be appended to the cache type prefix for this entry.
     * @return key which can be used to access data from this cache.
     */
    public String getKey(CacheType type, String id){
        String key;
        if (TextUtils.isEmpty(id)) {
            id = "";
        }

        switch (type){
            case DEVICE:
                key = AYLA_CACHED_DEVICES_PREFIX;
                break;
            case PROPERTY:
                key = AYLA_CACHED_PROPERTIES_PREFIX + id;
                break;
            case LAN_CONFIG:
                key = AYLA_CACHED_LANCONFIG_PREFIX + id;
                break;
            case SETUP:
                key = AYLA_CACHED_SETUP_PREFIX;
                break;
            case GROUP:
                key = AYLA_CACHED_GROUP_PREFIX;
                break;
            case NODE:
                key = AYLA_CACHED_NODE_PREFIX + id;
                break;
            default:
                return null;
        }

        return _sessionManagerRef.get().getSessionName() + key;
    }

    /**
     * Check if an id is required for this cache type.
     * @param type Cache type
     */
    private static boolean isIdRequired(CacheType type){
        boolean isIdRequired;
        switch (type){
            case DEVICE:
            case SETUP:
            case GROUP:
                isIdRequired = false;
                break;
            case PROPERTY:
            case LAN_CONFIG:
            case NODE:
                isIdRequired = true;
                break;
            default:
                isIdRequired = false;
        }

        return isIdRequired;
    }

    /**
     * Convert an array to JSON format and save it in cache.
     * @param cacheType Cache type
     * @param id Unique id to be appended to the cache type prefix for this entry.
     * @param array Array to be converted to JSON and saved in AylaCache.
     */
    public<T> void saveArray(CacheType cacheType, String id, T[] array){
        Gson gson = AylaNetworks.sharedInstance().getGson();
        String jsonVal = gson.toJson(array);
        save(cacheType, id, jsonVal);
    }
}
