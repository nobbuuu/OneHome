package com.aylanetworks.aylasdk;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.setup.AylaRegistration;
import com.aylanetworks.aylasdk.setup.AylaRegistrationCandidate;
import com.aylanetworks.aylasdk.util.AylaPredicate;
import com.aylanetworks.aylasdk.util.URLHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class representing a Gateway. Gateways provide a bridge between their nodes, which appear as
 * devices to the system, and the local network. Any communication between the mobile application
 * and a device node needs to go through a gateway.
 * <p>
 * The AylaDeviceGateway class provides mechanisms for interacting with the nodes managed by the
 * gateway, including adding and removing nodes from the system.
 */
public class AylaDeviceGateway extends AylaDevice {
    @Override
    public boolean isGateway() {
        return true;
    }

    /**
     * Returns a list of the nodes owned by this gateway.
     *
     * @return A list of the nodes owned by this gateway.
     */
    public List<AylaDeviceNode> getNodes() {
        AylaDeviceManager dm = getDeviceManager();
        if (dm == null) {
            return null;
        }

        List<AylaDevice> deviceList = dm.getDevices(new AylaPredicate<AylaDevice>() {
            @Override
            public boolean test(AylaDevice aylaDevice) {
                if (aylaDevice.isNode()) {
                    AylaDeviceNode node = (AylaDeviceNode) aylaDevice;
                    return node.getGatewayDsn().equals(getDsn());
                }
                return false;
            }
        });

        // Convert to a list of AylaDeviceNodes from our list of AylaDevices
        List<AylaDeviceNode> nodeList = new ArrayList<>();
        for (AylaDevice device : deviceList) {
            nodeList.add((AylaDeviceNode) device);
        }

        return nodeList;
    }

    /**
     * This method fetches an array of registration candidates for nodes visible to this gateway.
     *
     * @param successListener Listener to receive  Registration Candidates
     * @param errorListener   Listener to receive an AylaError should one occur
     * @return the AylaAPIRequest object used to get Registration Candidates
     */
    public AylaAPIRequest fetchRegistrationCandidates(
            final Response.Listener<AylaRegistrationCandidate[]> successListener,
            final ErrorListener errorListener) {

        //Check if it is a Gateway device
        AylaSessionManager sessionManager = getSessionManager();
        if (sessionManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                        "No session is active"));
            }
            return null;
        }

        AylaDeviceManager deviceManager = sessionManager.getDeviceManager();
        if (deviceManager == null) {
            if (errorListener != null) {
                errorListener.onErrorResponse(new AylaError(AylaError.ErrorType.AylaError,
                        "No device manager is available"));
            }
            return null;
        }

        // String url = "http://ads-dev.aylanetworks.com/apiv1/devices/register.json";
        String url = deviceManager.deviceServiceUrl("apiv1/devices/register.json");
        Map<String, String> params = new HashMap<>();

        String dsn = getDsn();
        if (dsn != null) {
            params.put(AylaRegistration.AYLA_REGISTRATION_TARGET_DSN, dsn);
        }

        params.put(AylaRegistration.AYLA_REGISTRATION_TYPE, RegistrationType.Node.stringValue());
        url = URLHelper.appendParameters(url, params);

        AylaAPIRequest request = new AylaAPIRequest<>(
                Request.Method.GET,
                url,
                null,
                AylaRegistrationCandidate.Wrapper[].class,
                sessionManager,
                new Response.Listener<AylaRegistrationCandidate.Wrapper[]>() {
                    @Override
                    public void onResponse(AylaRegistrationCandidate.Wrapper[] candidates) {
                        AylaRegistrationCandidate[] registrationCandidates =
                                AylaRegistrationCandidate.Wrapper.unwrap(candidates);

                        //The returned registrationCandidate does not have the RegistrationType
                        for (AylaRegistrationCandidate candidate : registrationCandidates) {
                            candidate.setHardwareAddress(candidate.getMacAddress());
                            candidate.setRegistrationType(RegistrationType.Node);
                        }
                        successListener.onResponse(registrationCandidates);
                    }
                },
                errorListener);

        deviceManager.sendDeviceServiceRequest(request);
        return request;
    }
}
