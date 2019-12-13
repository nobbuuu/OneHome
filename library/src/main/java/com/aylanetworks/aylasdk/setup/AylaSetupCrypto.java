/*
 * AylaencryptionHelper.java
 * Ayla Mobile Library
 * 
 * Created by Di Wang on 05/20/2015
 * Copyright (c) 2015 Ayla Networks. All Rights Reserved.
 * */

package com.aylanetworks.aylasdk.setup;

import org.spongycastle.asn1.ASN1EncodableVector;
import org.spongycastle.asn1.ASN1Integer;
import org.spongycastle.asn1.ASN1Sequence;
import org.spongycastle.asn1.DERSequence;
import org.spongycastle.crypto.AsymmetricBlockCipher;
import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.encodings.PKCS1Encoding;
import org.spongycastle.crypto.engines.RSAEngine;
import org.spongycastle.crypto.generators.RSAKeyPairGenerator;
import org.spongycastle.crypto.params.AsymmetricKeyParameter;
import org.spongycastle.crypto.params.RSAKeyGenerationParameters;
import org.spongycastle.crypto.params.RSAKeyParameters;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;


/**
 * AylaSetupCrypto contains methods used to assist with public key crypto used during WiFi setup
 * with a device. Since the normal key exchange using the cloud as a liaison between the mobile
 * device and device module cannot be performed, as neither device can reach the Internet, a
 * public key is used to protect the transfer of the LAN session key used for LAN communications.
 * <p>
 * This class contains helper methods that use the SpongyCastle crypto library to assist with the
 * crypto operations.
 * <p>
 *
 * @see <a href=https://rtyley.github.io/spongycastle/>http://rtyley.github.io/spongycastle/</a>
 */
public class AylaSetupCrypto {

    private static final String LOG_TAG = "CryptHelper";
    public static final int DEFAULT_KEY_SIZE = 1024;

    private AsymmetricCipherKeyPair _keyPair = null;
    private int _keySize;
    private BigInteger _publicMod = null;
    private BigInteger _publicExp = null;
    private BigInteger _privateMod = null;
    private BigInteger _privateExp = null;

    public AylaSetupCrypto(int keySize) {
        _keySize = keySize;
    }

    public AylaSetupCrypto() {
        this(DEFAULT_KEY_SIZE);
    }

    public byte[] getPublicKeyPKCS1V21Encoded() throws IOException {
        if (_keyPair == null) {
            generateKeyPair();
        }
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(new ASN1Integer(_publicMod));
        v.add(new ASN1Integer(_publicExp));

        ASN1Sequence sequence = new DERSequence(v);
        return sequence.getEncoded();
    }

    public byte[] getPrivateKeyPKCS1V21Encoded() throws IOException {
        if (_keyPair == null) {
            generateKeyPair();
        }

        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(new ASN1Integer(_privateMod));
        v.add(new ASN1Integer(_privateExp));
        ASN1Sequence sequence = new DERSequence(v);

        return sequence.getEncoded();
    }

    /**
     * Creates the public / private key pair to be used for establishing a secure session during
     * wifi setup on a device. This method must be called to create the keys before any of the
     * other methods can be used.
     */
    public void generateKeyPair() {
        RSAKeyPairGenerator generator = new RSAKeyPairGenerator();

        generator.init(new RSAKeyGenerationParameters(
                new BigInteger("65537") // public exponent.
                , new SecureRandom()
                , _keySize
                , 5) /* The bigger this param, the better odds for successful key generation,
                            the slower the process. */
        );

        _keyPair = generator.generateKeyPair();

        initRSAKeyParam();
    }

    private void initRSAKeyParam() {
        if (_keyPair == null) {
            generateKeyPair();
        }

        AsymmetricKeyParameter key1 = _keyPair.getPublic();
        if (!(key1 instanceof RSAKeyParameters)) {
            throw new RuntimeException("Public key is not RSA.");
        }
        RSAKeyParameters rsaPublicKey = (RSAKeyParameters) key1;
        AsymmetricKeyParameter key2 = _keyPair.getPrivate();
        if (!(key2 instanceof RSAKeyParameters)) {
            throw new RuntimeException("Private key is not RSA.");
        }
        RSAKeyParameters rsaPrivateKey = (RSAKeyParameters) key2;

        _publicMod = rsaPublicKey.getModulus();
        _publicExp = rsaPublicKey.getExponent();

        _privateMod = rsaPrivateKey.getModulus();
        _privateExp = rsaPrivateKey.getExponent();
    }

    /**
     * Encrypts the specified bytes using our public key and returns the encrypted byte array.
     *
     * @param src Array of bytes to encrypt
     * @return what you passed in, but encrypted using our public key.
     */
    public byte[] encrypt(final byte[] src) {
        if (src == null || src.length == 0) {
            return null;
        }

        if (_keyPair == null) {
            throw new RuntimeException("Call generateKeyPair() before calling this method");
        }

        try {
            AsymmetricBlockCipher e = new RSAEngine();
            e = new PKCS1Encoding(e);
            e.init(true, _keyPair.getPublic());
            return e.processBlock(src, 0, src.length);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Decrypts the specified bytes using our public key and returns the decrypted byte array.
     *
     * @param src Array of bytes to decrypt
     * @return the decrypted data, or null if the data could not be decrypted
     */
    public byte[] decrypt(final byte[] src) {
        if (src == null || src.length == 0) {
            return null;
        }
        if (_keyPair == null) {
            throw new RuntimeException("Call generateKeyPair() before calling this method");
        }

        try {
            AsymmetricBlockCipher e = new RSAEngine();
            e = new PKCS1Encoding(e);
            e.init(false, _keyPair.getPrivate());
            return e.processBlock(src, 0, src.length);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}











