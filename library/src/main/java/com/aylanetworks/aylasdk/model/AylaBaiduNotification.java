package com.aylanetworks.aylasdk.model;

import com.google.gson.annotations.Expose;

/**
 * Aura
 * <p/>
 * Copyright 2016 Ayla Networks Inc, all rights reserved
 */

/**
 * Model class for Baidu Notification object.
 * Data model exposed to app level for parsing.
 **/
public class AylaBaiduNotification {

    @Expose
    private String title;

    @Expose
    private String description;

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
