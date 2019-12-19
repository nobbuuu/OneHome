package com.dream.onehome.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetBroadcastReceiver extends BroadcastReceiver {

    private NetConnectedListener netConnectedListener;
    
    @Override
    public void onReceive(Context context, Intent intent) {
    
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        NetworkInfo mobNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        
        NetworkInfo wifiNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (!wifiNetInfo.isConnected()) {
            //WIFI和移动网络均未连接
            netConnectedListener.netContent(false,wifiNetInfo);
           
        } else {
            //WIFI连接或者移动网络连接
            netConnectedListener.netContent(true,wifiNetInfo);
        }

    }

    public void setNetConnectedListener(NetConnectedListener netConnectedListener) {
        this.netConnectedListener = netConnectedListener;
    }

    public interface NetConnectedListener {
        void netContent(boolean isConnected,NetworkInfo networkInfo);
    }



}

