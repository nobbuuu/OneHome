package com.aylanetworks.aylasdk.setup;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaDeviceManager;
import com.google.gson.annotations.Expose;

import java.lang.ref.WeakReference;

/**
 * Represents a candidate for registration returned from the Ayla Cloud Service in response to a
 * {@link AylaRegistration#fetchCandidate}.
 */
public class AylaRegistrationCandidate {
    @Expose
    private String dsn;
    @Expose
    private String lan_ip;
    @Expose
    private String model;
    @Expose
    private String oem_model;
    @Expose
    private String product_name;
    @Expose
    private String connected_at;
    @Expose
    private String product_class;

    @Expose
    private String connection_status;
    @Expose
    private String device_type;
    @Expose
    private String mac;
    @Expose
    private String address;
    @Expose
    private String setupToken;
    @Expose
    private String registrationToken;
    @Expose
    private String latitude;
    @Expose
    private String longitude;

    @Expose
    private String unique_hardware_id;

    private AylaDevice.RegistrationType registrationType;

    protected WeakReference<AylaDeviceManager> _deviceManagerRef;

    public AylaDeviceManager getDeviceManager() {
        return _deviceManagerRef.get();
    }

    /**
     * Default constructor
     */
    public AylaRegistrationCandidate() {}

    /**
     * Construct a candidate from a device (used mainly for local device registration)
     * @param device Device to use as a template for the registration candidate
     */
    public AylaRegistrationCandidate(AylaDevice device) {
        super();
        dsn = device.getDsn();
        model = device.getModel();
        oem_model = device.getOemModel();
        product_name = device.getProductName();
        device_type = device.getRegistrationType().stringValue();
        mac = device.getMac();
        latitude = device.getLat();
        longitude = device.getLng();
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
    }

    public void setSetupToken(String setupToken) {
        this.setupToken = setupToken;
    }

    public void setRegistrationType(AylaDevice.RegistrationType registrationType) {
        this.registrationType = registrationType;
    }

    public void setDsn(String dsn) {
        this.dsn = dsn;
    }

    public void setOemModel(String value) {
        this.oem_model = value;
    }

    public void setLanIp(String lan_ip) {
        this.lan_ip = lan_ip;
    }

    public String getHardwareAddress() {
        return unique_hardware_id;
    }

    public void setHardwareAddress(String hwAddr) {
        this.unique_hardware_id = hwAddr;
    }

    public String getRegistrationToken() {return registrationToken;}

    public String getSetupToken() {return setupToken;}

    public String getMac() {
        return mac;
    }

    public String getMacAddress() {
        return address;
    }

    public String getDsn() {
        return dsn;
    }

    public String getLan_ip() {
        return lan_ip;
    }

    public String getModel() {
        return model;
    }

    public String getOemModel() {
        return oem_model;
    }

    public String getProductName() {
        return product_name;
    }

    public void setProductName(String productName) {
        product_name = productName;
    }

    public String getConnectedAt() {
        return connected_at;
    }

    public String getProductClass() {return product_class;}

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public AylaDevice.RegistrationType getRegistrationType() { return registrationType; }

    public static class Wrapper {
        @Expose
        public AylaRegistrationCandidate device;

        public static AylaRegistrationCandidate[] unwrap(Wrapper[] wrappedCandidates) {
            int size = 0;
            if (wrappedCandidates != null) {
                size = wrappedCandidates.length;
            }

            AylaRegistrationCandidate[] candidates = new AylaRegistrationCandidate[size];
            for (int i = 0; i < size; i++) {
                candidates[i] = wrappedCandidates[i].device;
            }
            return candidates;
        }
    }
}
