package com.aylanetworks.aylasdk.lan;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.aylanetworks.aylasdk.setup.AylaSetupCrypto;
import com.google.gson.annotations.Expose;

import java.io.IOException;

/**
 * AylaLanConfig contains information needed by the system to securely connect to a device
 * over the local network. This information is fetched from the service during {@link com
 * .aylanetworks.aylasdk.AylaDeviceManager} initialization for each device known to the system.
 */
public class AylaLanConfig {
    @Expose
    public Number lanipKeyId;
    @Expose
    public String lanipKey;
    @Expose
    public Number keepAlive;
    @Expose
    public Number autoSync;
    @Expose
    public String status = "Unknown";

    // For secure setup, we have an AylaSetupCrypto object to help us
    private AylaSetupCrypto _aylaSetupCrypto;

    public AylaLanConfig(AylaSetupCrypto setupCrypto) {
        _aylaSetupCrypto = setupCrypto;
    }

    public byte[] getPublicKey() throws IOException {
        if (_aylaSetupCrypto == null) {
            return null;
        }
        return _aylaSetupCrypto.getPublicKeyPKCS1V21Encoded();
    }

    public AylaSetupCrypto getSetupCrypto() {
        return _aylaSetupCrypto;
    }

    public static class Wrapper {
        @Expose
        public AylaLanConfig lanip;
    }
}
