package com.aylanetworks.aylasdk.error;
/*
 * Android_AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * An AppPermissionError is returned to the caller when permission for an operation has not been
 * given by the app. The application is responsible for presenting UI to the users requesting
 * permission for various types of actions in Android 6.0 and above. If the user denies
 * permission or the app does not ask the user for permission, some APIs may return this error.
 *
 * The {@link #getFailedPermission()} method can be called to return the actual permission that
 * was requested.
 */
public class AppPermissionError extends AylaError {
    public AppPermissionError(String permission) {
        super(ErrorType.AppPermission, permission);
    }

    public String getFailedPermission() {
        return getMessage();
    }

    public String toString() {
        return "AppPermissionError: " + getFailedPermission() + " has not been granted";
    }
}
