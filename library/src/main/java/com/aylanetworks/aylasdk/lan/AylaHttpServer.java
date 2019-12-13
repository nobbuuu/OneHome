package com.aylanetworks.aylasdk.lan;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.text.TextUtils;

import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaDeviceManager;
import com.aylanetworks.aylasdk.ota.AylaLanOTADevice;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.setup.AylaSetupDevice;

import java.io.IOException;
import java.lang.ref.WeakReference;

import fi.iki.elonen.router.RouterNanoHTTPD;

/**
 * The AylaHttpServer is used as part of the communication protocol with LAN mode devices. The
 * server listens for events from devices and routes them to the appropriate LAN module.
 */
public class AylaHttpServer extends RouterNanoHTTPD {
    private final static String LOG_TAG = "AylaHTTPD";

    /**
     * HTTP header containing the IP address of the connecting client
     */
    public final static String HEADER_CLIENT_IP = "http-client-ip";
    /**
     * Default listening port for HTTP requests
     */
    public final static int DEFAULT_PORT = 10275;

    /**
     * Default timeout reading from a socket. Our keep-alive interval is 30 seconds, we will give
     * some additional leeway here.
     */
    public final static int DEFAULT_READ_TIMEOUT = 35000;

    public final static String MIME_JSON = "application/json";

    private WeakReference<AylaSessionManager>_sessionManagerRef;

    private AylaSetupDevice _setupDevice;

    private AylaLanOTADevice _otaDevice;

    /**
     * Some Ayla-specific HTTP response status codes. These codes implement the IStatus interface
     * to be compatible with NanoHTTPD.
     */
    public enum Status implements Response.IStatus {
        PRECONDITION_FAILED(412, "Precondition Failed"),
        METHOD_FAILURE(424, "Method Failure"),
        UPGRADE_REQUIRED(426, "Upgrade Required"),
        CERT_ERROR(495, "Cert Error"),
        NOT_IMPLEMENTED(501, "Not Implemented"),
        SERVICE_UNAVAILABLE(503, "Service Unavailable");

        private final int requestStatus;

        private final String description;

        Status(int requestStatus, String description) {
            this.requestStatus = requestStatus;
            this.description = description;
        }

        @Override
        public String getDescription() {
            return "" + this.requestStatus + " " + this.description;
        }

        @Override
        public int getRequestStatus() {
            return this.requestStatus;
        }
    }

    /**
     * Constructor
     *
     * @param port Port to listen on
     * @param sessionManager the {@link AylaSessionManager} that owns this object
     * @throws IOException if the server could not be started
     */
    public AylaHttpServer(int port, AylaSessionManager sessionManager) throws IOException {
        super(port);
        _sessionManagerRef = new WeakReference<AylaSessionManager>(sessionManager);
        addMappings();
        start(DEFAULT_READ_TIMEOUT);
        AylaLog.d(LOG_TAG, "Server started on " + getHostname() + ":" + getListeningPort());
    }

    /**
     * Creates a default HTTP server on the default port, if possible. If the port is
     * unavailable, the OS will be asked to provide a port.
     *
     * @param sessionManager the AylaSessionManager responsible for devices talking to this server
     * @return the HTTP server
     * @throws IOException if the socket could not be created
     */
    public static AylaHttpServer createDefault(AylaSessionManager sessionManager)
            throws IOException {
        AylaHttpServer server = null;
        try {
            server = new AylaHttpServer(DEFAULT_PORT, sessionManager);
        } catch (IOException e) {
            AylaLog.e(LOG_TAG, "Failed to create server on port " + DEFAULT_PORT);
        }

        if (server == null) {
            // Try again, letting the OS choose a port for us. We will let the exception
            // propogate up if it is thrown.
            server = new AylaHttpServer(0, sessionManager);
        }

        return server;
    }

    public AylaDevice deviceWithDsn(String dsn) {
        if (_setupDevice != null && TextUtils.equals(dsn, _setupDevice.getDsn())) {
            return _setupDevice;
        }

        AylaSessionManager sm = _sessionManagerRef.get();
        if ( sm != null ) {
            AylaDeviceManager dm = sm.getDeviceManager();
            if ( dm != null ) {
                return dm.deviceWithDSN(dsn);
            }
        }
        return null;
    }

    @Override
    public void addMappings() {
        super.addMappings();

        AylaSessionManager sm = _sessionManagerRef.get();

        // Remove the default routes set by the call to super above
        removeRoute("/");
        removeRoute("/index.html");

        // Remove any existing routes we may have
        removeRoute("/local_lan/key_exchange.json");
        removeRoute("/local_lan/commands.json");
        removeRoute("/local_lan/property/datapoint.json");
        removeRoute("/local_lan/property/datapoint/ack.json");
        removeRoute("/local_lan/node/property/datapoint.json");
        removeRoute("/local_lan/node/property/datapoint/ack.json");
        removeRoute("/local_lan/node/conn_status.json");
        removeRoute("/local_lan/status.json");
        removeRoute("/local_lan/wifi_scan.json");
        removeRoute("/local_lan/wifi_scan_results.json");
        removeRoute("/local_lan/connect_status");
        removeRoute("/local_lan/wifi_status.json");
        removeRoute("/local_lan/regtoken.json");
        removeRoute("/local_lan/wifi_stop_ap.json");

        // Add handlers for the various URLs we serve
        addRoute("/local_lan/key_exchange.json",
                KeyExchangeHandler.class, sm, _setupDevice);

        addRoute("/local_lan/commands.json",
                CommandHandler.class, sm, _setupDevice);

        addRoute("/local_lan/property/datapoint.json",
                PropertyUpdateHandler.class, sm, _setupDevice);

        addRoute("/local_lan/property/datapoint/ack.json",
                PropertyUpdateHandler.class, sm, _setupDevice);

        addRoute("/local_lan/node/property/datapoint.json",
                PropertyUpdateHandler.class, sm, _setupDevice);

        addRoute("/local_lan/node/property/datapoint/ack.json",
                PropertyUpdateHandler.class, sm, _setupDevice);

        addRoute("/local_lan/node/conn_status.json",
                ConnectionStatusHandler.class, sm, _setupDevice);

        addRoute("/local_lan/connect_status",
                ModuleRequestHandler.class, sm, _setupDevice);

        addRoute("/local_lan/status.json",
                SetupDeviceDetailsHandler.class, sm, _setupDevice);

        addRoute("/local_lan/wifi_scan.json",
                ModuleRequestHandler.class, sm, _setupDevice);

        addRoute("/local_lan/wifi_scan_results.json",
                ModuleRequestHandler.class, sm, _setupDevice);

        addRoute("/local_lan/wifi_status.json",
                ModuleRequestHandler.class, sm, _setupDevice);

        addRoute("/local_lan/regtoken.json",
                ModuleRequestHandler.class, sm, _setupDevice);

        addRoute("/local_lan/wifi_stop_ap.json",
                ModuleRequestHandler.class, sm, _setupDevice);
    }

    public void setSetupDevice(AylaSetupDevice setupDevice) {
        _setupDevice = setupDevice;
        // Call addMappings again to update the setupDevice parameter since it has changed
        addMappings();
    }
    public void setOTADevice(AylaLanOTADevice otaDevice) {
        _otaDevice = otaDevice;
        removeRoute(otaDevice.getOTAPath());
        removeRoute("/ota_status.json");

        AylaSessionManager sm = _sessionManagerRef.get();
        addRoute(otaDevice.getOTAPath(),
                LanOTAHandler.class, sm, _otaDevice);

        addRoute("/ota_status.json",
                LanOTAHandler.class, sm, _otaDevice);
    }


    public AylaSetupDevice getSetupDevice() {
        return _setupDevice;
    }

    @Override
    public Response serve(IHTTPSession session) {
        AylaLog.d(LOG_TAG, "Request: " + session.getUri());
        return super.serve(session);
    }
}
