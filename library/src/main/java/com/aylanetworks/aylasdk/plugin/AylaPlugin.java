package com.aylanetworks.aylasdk.plugin;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */


import com.aylanetworks.aylasdk.AylaSessionManager;

/**
 * AylaPlugin is the base interface for the Ayla Mobile SDK plugin system. Plugins may be
 * installed in the SDK via calls to
 * {@link com.aylanetworks.aylasdk.AylaNetworks#installPlugin(String, AylaPlugin)}.
 *
 * Any object implementing this interface may be installed as a plugin. The objects will be
 * called via the AylaPlugin interfaces when the SDK is initialized, paused, resumed or shut down.
 *
 * Installed plugins may be obtained from the AylaNetworks singleton object by calling
 * {@link com.aylanetworks.aylasdk.AylaNetworks#getPlugin(String)}.
 */
public interface AylaPlugin {
    String pluginName();

    /**
     * Called when the provided AylaSessionManager has successfully started a session (signed in)
     * @param pluginId ID the plugin was registered with
     * @param sessionManager Session manager for the session that just signed in
     */
    void initialize(String pluginId, AylaSessionManager sessionManager);

    /**
     * Called when the SDK is paused
     * @param pluginId ID the plugin was registered with
     * @param sessionManager SessionManager for the session that is being paused
     */
    void onPause(String pluginId, AylaSessionManager sessionManager);

    /**
     * Called when the SDK is resumed
     * @param pluginId ID the plugin was registered with
     * @param sessionManager SessionManager for the session that is being resumed
     */
    void onResume(String pluginId, AylaSessionManager sessionManager);

    /**
     * Called when a session is shut down (signed out)
     * @param pluginId ID the plugin was registered with
     * @param sessionManager SessionManager for the session that is being ended
     */
    void shutDown(String pluginId, AylaSessionManager sessionManager);
}
