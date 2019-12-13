package com.aylanetworks.aylasdk.auth;

import android.content.Context;

import com.aylanetworks.aylasdk.AylaLoginManager;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.error.AuthError;
import com.tencent.mm.opensdk.modelmsg.SendAuth;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;

import android.webkit.WebView;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * This class is used to authenticate login credentials via WeChat OAuth provider.
 */
public class AylaWeChatAuthProvider extends AylaOAuthProvider {
    private static AylaWeChatAuthProvider __myInstance;
    private String _wechatAppId;
    private final String WECHAT_AUTH = "wechat_provider";
    private static String AUTH_URI_WECHAT;

    /**
     * Constructor for AylaWeChatAuthProvider
     *
     * @param wechatAppId WeChat App ID
     */
    public AylaWeChatAuthProvider(String wechatAppId) {
        if (__myInstance != null) {
            throw new IllegalStateException("Still waiting for response...");
        }
        __myInstance = this;
        _wechatAppId = wechatAppId;
        AylaLoginManager loginManager = AylaNetworks.sharedInstance().getLoginManager();
        AUTH_URI_WECHAT = loginManager.userServiceUrl("sessions/post_process_provider_auth");
    }

    /**
     * This method is called when we get a response token from WeChat service
     *
     * @param authWeChat WeChat oAuthCode
     */
    public static void activityDidAuthenticate(String authWeChat) {
        if (__myInstance != null) {
            __myInstance.authenticateToService(authWeChat, null);
            __myInstance = null;
        }
    }

    public static void activityCancelAuth(String message) {
        if (__myInstance != null) {
            if(__myInstance._listener != null) {
                __myInstance._listener.didFailAuthentication(new AuthError(message, null));
            }
            __myInstance = null;
        }
    }

    @Override
    public void authenticate(final AuthProviderListener listener) {
        //Just make sure listener is not null
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        setListener(listener);
        SendAuth.Req req = new SendAuth.Req();
        req.scope = "snsapi_userinfo";
        req.state = "none";
        final Context context = AylaNetworks.sharedInstance().getContext();
        IWXAPI api = WXAPIFactory.createWXAPI(context, _wechatAppId, true);
        api.sendReq(req);
    }

    public String getAuthType() {
        return WECHAT_AUTH;
    }

    public String getAuthURL() {
        return AUTH_URI_WECHAT;
    }

    public WebView getWebView() {
        return null;
    }
}
