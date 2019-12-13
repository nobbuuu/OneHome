package com.aylanetworks.aylasdk.auth;
/**
 * AylaSDK
 * <p>
 * Copyright 2017 Ayla Networks, all rights reserved
 */

import com.android.volley.Request;
import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.util.URLHelper;
import com.google.gson.annotations.Expose;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides partner cloud authentication through Ayla as IDP.
 * This class will host partner authorization objects per parter Id
 * for all partner clouds that app is configured to use.
 */
public class AylaIDPAuthProvider {
    private final String LOG_TAG = "AylaIDPAuthProvider";

    private final WeakReference<AylaSessionManager> _sessionManagerRef;

    /**
     * Initializes partner auth provider.
     *
     * @param sessionManager AylaSessionManager after user has successully signed into the App
     */
    public AylaIDPAuthProvider(AylaSessionManager sessionManager) {
        _sessionManagerRef = new WeakReference<>(sessionManager);
    }

    /**
     * Retrieves short-lived partnerTokens from Ayla IDP service for given partnerIds.
     * These partner tokens are used to login to partner cloud.
     *
     * @param partnerIds      Array of partner Ids. If no specific partner Ids, all available partner token will be returned.
     * @param successListener Listener executed after successfully fetching partner tokens
     * @param errorListener   Listener executed in case of an error.
     */
    public AylaAPIRequest getPartnerTokensForPartnerIds(List<String> partnerIds, final Response.Listener<List<IDPPartnerToken>> successListener, final ErrorListener errorListener) {

        if (partnerIds == null) {
            partnerIds = new ArrayList<>();
        }

        AylaSessionManager sessionManager = _sessionManagerRef.get();
        StringBuilder urlBuilder = new StringBuilder(sessionManager.userServiceUrl("api/v1/partner_tokens.json"));
        urlBuilder.append(URLHelper.parameterizeArray("partner_ids", partnerIds));
        Response.Listener<IDPPartnerToken.Wrapper> success = new Response.Listener<IDPPartnerToken.Wrapper>() {
            @Override
            public void onResponse(IDPPartnerToken.Wrapper wrapper) {
                successListener.onResponse(wrapper.partnerTokens);
            }
        };

        AylaAPIRequest request = new AylaAPIRequest(Request.Method.GET,
                urlBuilder.toString(),
                null,
                IDPPartnerToken.Wrapper.class,
                sessionManager,
                success,
                errorListener);
        sessionManager.sendUserServiceRequest(request);

        return request;
    }

    /**
     * Object stand for partner token.
     */
    public static class IDPPartnerToken {
        @Expose
        private String partnerId;
        @Expose
        private String partnerToken;

        public String getPartnerId() {
            return partnerId;
        }

        public String getPartnerToken() {
            return partnerToken;
        }

        public static class Wrapper {
            @Expose
            public List<IDPPartnerToken> partnerTokens;
        }
    }
}