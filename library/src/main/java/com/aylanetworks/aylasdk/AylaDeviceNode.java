package com.aylanetworks.aylasdk;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */


import com.aylanetworks.aylasdk.change.FieldChange;
import com.aylanetworks.aylasdk.lan.AylaHttpServer;
import com.aylanetworks.aylasdk.lan.AylaLanModule;
import com.aylanetworks.aylasdk.util.AylaPredicate;
import com.google.gson.annotations.Expose;

import java.util.List;

public class AylaDeviceNode extends AylaDevice {

    @Expose
    public String nodeDsn;                    // The DSN for this node
    @Expose
    public String action;                    //  The action last action attempted
    @Expose
    public String status;                    // The status of the last action attempted
    @Expose
    public String errorCode;                // The error code of the last action attempted
    @Expose
    public String ackedAt;                    // When the latest action was acknowledged  by an owner gateway
    @Expose
    public String gatewayDsn;                // Owner gateway DSN
    @Expose
    public String address;                    // Node address

    public String getNodeDsn() {
        return nodeDsn;
    }

    public String getAction() {
        return action;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getAckedAt() {
        return ackedAt;
    }

    public String getGatewayDsn() {
        return gatewayDsn;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public boolean isNode() {
        return true;
    }

    @Override
    public AylaProperty getProperty(String propertyName) {
        // First see if we have a property of the given name
        AylaProperty property = super.getProperty(propertyName);
        if (property != null) {
            return property;
        }

        // Nodes may have properties updated from the gateway, in which case the name will look
        // something like this:
        // 01:0006_S:0000
        // We need to extract the last portion of that name to use as our property name
        String[] components = propertyName.split(":");
        if (components.length == 3) {
            return super.getProperty(components[2]);
        }
        return null;
    }

    /**
     * Returns the gateway that owns this node
     * @return our gateway, or null if we don't know what it is
     */
    public AylaDeviceGateway getGateway() {
        AylaDeviceManager dm = getDeviceManager();
        List<AylaDevice> devices = dm.getDevices(new AylaPredicate<AylaDevice>() {
            @Override
            public boolean test(AylaDevice aylaDevice) {
                return aylaDevice.isGateway() && aylaDevice.getDsn().equals(getGatewayDsn());
            }
        });

        if (devices.size() == 0) {
            return null;
        }
        return (AylaDeviceGateway) devices.get(0);
    }

    /**
     * Our gateway controls our LAN mode.
     * @return our gateway's response to this method
     */
    @Override
    public boolean isLanModePermitted() {
        AylaDeviceGateway gw = getGateway();
        return gw != null && gw.isLanModePermitted();
    }

    /**
     * Our gateway controls our LAN mode.
     * @return our gateway's response to this method
     */
    @Override
    public boolean isLanModeActive() {
        AylaDeviceGateway gw = getGateway();
        return gw != null && gw.isLanModeActive();
    }

    /**
     * Our gateway controls our LAN mode.
     * @return our gateway's response to this method
     */
    @Override
    public AylaLanModule getLanModule() {
        if (getGateway() == null) {
            return null;
        }
        return getGateway().getLanModule();
    }

    @Override
    public void startLanSession(AylaHttpServer server) {
        // Nodes do not start LAN sessions- they use their gateway's LAN session.
    }

    /**
     * Updates the connection status for the node. If the connection status has changed, this
     * method will notify listeners of the change.
     * @param isConnected True if connected, false otherwise.
     */
    public void updateConnectionStatus(boolean isConnected, DataSource source) {
        boolean shouldNotify = isConnected ? (getConnectionStatus() == ConnectionStatus.Offline) :
                (getConnectionStatus() == ConnectionStatus.Online);

        if (isConnected) {
            connectionStatus = ConnectionStatus.Online.toString();
        } else {
            connectionStatus = ConnectionStatus.Offline.toString();
        }

        if (shouldNotify) {
            notifyDeviceChanged(new FieldChange("connectionStatus"), source);
        }
    }

    /**
     * Class representing the JSON object containing the node status, and its wrapper class.
     */
    public static class NodeConnectionStatus {
        @Expose
        public String dsn;
        @Expose
        public boolean status;

        public static class Wrapper {
            @Expose
            public NodeConnectionStatus[] connection;
        }
    }
}
