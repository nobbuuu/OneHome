package com.aylanetworks.aylasdk;
/*
 * Android_AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.util.ServiceUrls;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/**
 * AylaConnectivity monitors the network state and provides listeners with notifications when the
 * state changes. Listeners may register with AylaConnectivity by first obtaining the global
 * AylaConnectivity object from {@link AylaNetworks#getConnectivity()}, and calling {@link
 * #registerListener(AylaConnectivityListener)} on the returned object.
 * <p>
 * Listeners will be called via
 * {@link AylaConnectivity.AylaConnectivityListener#connectivityChanged}
 * whenever the network state has changed.
 */
public class AylaConnectivity {
    private final static String LOG_TAG = "AylaConn";

    private final Set<AylaConnectivityListener> _listeners;
    private boolean _wifiEnabled;
    private boolean _cellEnabled;
    private Receiver _receiver;
    private Context _context;
    private final static String PING = "ping.json";

    // Using deprecated API getNetworkInfo, as the right APIs are SDK 21+.
    @SuppressWarnings("deprecation")
    public AylaConnectivity(Context context) {
        _listeners = new HashSet<>();
        _context = context;
        ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context .CONNECTIVITY_SERVICE);

        NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        NetworkInfo cellInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        _wifiEnabled = (wifiInfo != null) && wifiInfo.isConnected();
        _cellEnabled = (cellInfo != null) && cellInfo.isConnected();
    }

    /**
     * Starts monitoring for network change events
     * @param context Context used to register the broadcast receiver
     */
    public synchronized void startMonitoring(Context context) {
        if (_receiver == null) {
            _context = context;
            _receiver = new Receiver();
            _context.registerReceiver(_receiver,
                    new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE"));
        }
    }

    /**
     * Stops monitoring for network change events.
     */
    public synchronized void stopMonitoring() {
        if (_receiver != null) {
            try {
                _context.unregisterReceiver(_receiver);
            } catch (IllegalArgumentException e) {
                AylaLog.e(LOG_TAG, "Exception caught trying to unregister: " + e.toString());
            }
            
            _receiver = null;
        }
    }

    /**
     * Determine Ayla Service Reachability for device service
     * @param successListener Listener to receive if the service is reachable
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to determineServiceReachability
     */
    public static AylaAPIRequest determineServiceReachability(Response.Listener<AylaAPIRequest
                                                                      .EmptyResponse> successListener,
                                                              ErrorListener errorListener) {

        String url = AylaNetworks.sharedInstance().getServiceUrl(ServiceUrls.CloudService.Device,
                PING);
        AylaAPIRequest<AylaAPIRequest.EmptyResponse> request = new AylaAPIRequest<>(Request.Method
                .GET,
                url, null, AylaAPIRequest.EmptyResponse.class, null, successListener,
                errorListener);
        return AylaNetworks.sharedInstance().sendUserServiceRequest(request);
    }

    /**
     * Listener interface to receive events when network connectivity changes
     */
    public interface AylaConnectivityListener {
        void connectivityChanged(boolean wifiEnabled, boolean cellularEnabled);
    }

    /**
     * Registers a listener to receive connectivity change events
     * @param listener listener to receive connectivity change events
     */
    public void registerListener(AylaConnectivityListener listener) {
        synchronized (_listeners) {
            _listeners.add(listener);
        }
    }

    /**
     * Unregisters a listener previously registered for connectivity change events via
     * {@link #registerListener}
     * @param listener Listener to unregister
     */
    public void unregisterListener(AylaConnectivityListener listener) {
        synchronized (_listeners) {
            _listeners.remove(listener);
        }
    }

    // Using deprecated API getNetworkInfo, as the right APIs are SDK 21+.
    @SuppressWarnings("deprecation")
    private void onReceive(Context context, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        NetworkInfo cellInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        _wifiEnabled = (wifiInfo != null) && wifiInfo.isConnectedOrConnecting();
        _cellEnabled = (cellInfo != null) && cellInfo.isConnectedOrConnecting();

        synchronized (_listeners) {
            // Make a copy of our list of listeners in case they try to modify it
            List<AylaConnectivityListener> listeners = new ArrayList<>(_listeners);
            for (AylaConnectivityListener listener : listeners) {
                listener.connectivityChanged(_wifiEnabled, _cellEnabled);
            }
        }
    }

    public boolean isWifiEnabled() {
        return _wifiEnabled;
    }

    public boolean isCellEnabled() {
        return _cellEnabled;
    }

    private class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            AylaConnectivity.this.onReceive(context, intent);
        }
    }
}
