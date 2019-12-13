package com.aylanetworks.aylasdk.lan;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.InternalError;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.google.gson.annotations.Expose;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.Arrays;

import fi.iki.elonen.NanoHTTPD;

/**
 * Represents a message to or from the LAN-connected device. The AylaLanMessage has two fields,
 * enc and sign. enc is an encrypted payload; sign is the signature. Both are Base64-encoded
 * strings.
 * <p>
 * The {@link #getPayload(AylaEncryption)} method can be used to decrypt and verify a received
 * encrypted message.
 * <p>
 * The static {@link #fromSession(NanoHTTPD.IHTTPSession, AylaDevice)} method can be used
 * to construct an AylaLanMessage object from an HTTP session. This is used by {@link
 * AylaLanModule} when processing incoming messages for property updates or commands from the
 * module.
 */
public class AylaLanMessage {
    @Expose
    private String enc;
    @Expose
    private String sign;
    private WeakReference<AylaDevice> _deviceRef;

    /**
     * Creates an AylaLanMessage object from an HTTP session. This is used by AylaLanModule when
     * processing incoming commands from the device in LAN mode.
     *
     * @param session HTTP session containing the message data
     * @param device  Device this message is intended for
     * @return an AylaLanMessage initialized with the data found in the HTTP session
     * @throws PreconditionError if the device is not in LAN mode,
     * @throws InternalError     if no data is found in the HTTP session input stream
     */
    public static AylaLanMessage fromSession(NanoHTTPD.IHTTPSession session, AylaDevice device)
            throws PreconditionError, InternalError {
        if (device == null) {
            throw new IllegalArgumentException("Device may not be null");
        }

        Integer contentLength = Integer.parseInt(session.getHeaders().get("content-length"));

        AylaLanModule lanModule = device.getLanModule();
        if (lanModule == null) {
            throw new PreconditionError("Device is not in LAN mode");
        }

        String body = ObjectUtils.inputStreamToString(session.getInputStream(), contentLength);
        if (body == null) {
            throw new InternalError("No data found in request body");
        }

        // Create the LAN message and give it the device reference
        AylaLanMessage lanMessage = AylaNetworks.sharedInstance().getGson().fromJson(body,
                AylaLanMessage.class);
        if (lanMessage != null) {
            lanMessage._deviceRef = new WeakReference<AylaDevice>(device);
        }

        return lanMessage;
    }

    /**
     * Returns the decrypted message portion of the LanMessage object. If any errors are
     * encountered while processing, an AylaError will be thrown.
     *
     * @param encryption AylaEncryption object used to perform the decryption
     * @return The decrypted message payload
     * @throws AylaError if an error occurred. Errors can include:
     *                   <ul>
     *                   <li>PreconditionError if the message recipient could not be found or the device is
     *                   not running a LAN session (no LAN module exists)</li>
     *                   <li>InvalidArgumentError if the encryption parameter is null or the message could
     *                   not be decrypted or if the signature did not match</li>
     *                   </ul>
     */
    public Payload getPayload(AylaEncryption encryption) throws AylaError {
        // Decrypt and verify the message, and return the decrypted data
        AylaDevice device = _deviceRef.get();
        if (device == null) {
            throw new PreconditionError("Message recipient not found: no AylaDevice for this " +
                    "message");
        }

        if (encryption == null) {
            throw new InvalidArgumentError("encryption parameter may not be null");
        }

        // Do the decryption
        String decryptedMessage;
        try {
            decryptedMessage = encryption.unencodeDecrypt(enc);
        } catch (UnsupportedEncodingException e) {
            throw new InvalidArgumentError("Failed to decrypt message", e);
        }

        // Check the signature
        byte[] messageBytes = decryptedMessage.getBytes(Charset.forName("UTF-8"));
        byte[] signatureBytes = AylaEncryption.decodeBase64(sign);
        if (signatureBytes == null) {
            return null;
        }

        byte[] signKey = {0, 1, 2, 3, 4};
        if (encryption.devSignKey != null) {
            signKey = encryption.devSignKey;
        }
        byte[] calculatedSignature = AylaEncryption.hmacForKeyAndData(signKey, messageBytes);

        if (!Arrays.equals(signatureBytes, calculatedSignature)) {
            throw new InvalidArgumentError("Message signature does not match");
        }

        AylaLog.d("LanMsg", "Payload: " + decryptedMessage);
        JSONObject root;
        Payload payload = new Payload();
        try {
            root = new JSONObject(decryptedMessage);
            payload.seq_no = root.getInt("seq_no");
            payload.data = root.getString("data");
        } catch (JSONException e) {
            throw new JsonError(decryptedMessage, "Unable to parse payload", e);
        }

        return payload;
    }

    public static class Payload {
        @Expose
        public int seq_no;
        @Expose
        public String data;
    }
}
