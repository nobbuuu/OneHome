package com.aylanetworks.aylasdk.auth;

import android.webkit.WebView;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * This class is used to authenticate login credentials via FaceBook OAuth provider.
 */

public class FaceBookOAuthProvider extends AylaOAuthProvider {
    private final WebView _webView;
    public final static String FACEBOOK_AUTH = "facebook_provider";
    private final static String AUTH_URI_REMOTE = "https://" + MOBILE_HOST_URL + "/";

    public FaceBookOAuthProvider(final WebView webView) {
        _webView = webView;
    }
    public String getAuthType() {
        return FACEBOOK_AUTH;
    }
    public String getAuthURL() {
        return AUTH_URI_REMOTE;
    }
    public WebView getWebView() {
        return _webView;
    }
}

