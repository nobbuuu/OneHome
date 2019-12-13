package com.aylanetworks.aylasdk;

import com.google.gson.annotations.Expose;

/**
 * Android_Aura
 * <p>
 * Copyright 2017 Ayla Networks, all rights reserved
 */

public class AylaDeviceConnection {
    @Expose
    private String eventTime;
    @Expose
    private String userUuid;
    @Expose
    private String status;

    public String getStatus() {
        return status;
    }
}
