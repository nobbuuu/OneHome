package com.aylanetworks.aylasdk;

import com.google.gson.annotations.Expose;

/*
 * Android_AylaSDK
 * <p>
 * Copyright 2016 Ayla Networks, all rights reserved
 */

/**
 * Information about a user a device is shared with
 */
public class AylaShareUserProfile {
    @Expose
    public String firstname;
    @Expose
    public String lastname;
    @Expose
    public String email;
}
