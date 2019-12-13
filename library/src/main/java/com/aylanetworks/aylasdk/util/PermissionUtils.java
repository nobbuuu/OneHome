package com.aylanetworks.aylasdk.util;
/*
 * Android_AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.content.Context;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

import com.aylanetworks.aylasdk.error.AppPermissionError;
import com.aylanetworks.aylasdk.error.AylaError;

public class PermissionUtils {
    /**
     * Checks the array of permissions. If all permissions have been granted, returns null.
     * Otherwise an AppPermissionError will be returned for the first permission that was not
     * granted.
     *
     * @param context Context to check permissions in
     * @param permissions Array of permissions to check
     * @return Null if all permissions were granted, or an AppPermissionError for the first
     * failed permission.
     */
    public static AppPermissionError checkPermissions(Context context, String[] permissions) {
        for ( String permission : permissions ) {
            if (ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                return new AppPermissionError(permission);
            }
        }

        return null;
    }
}
