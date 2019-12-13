package com.aylanetworks.aylasdk;/*
 * {PROJECT_NAME}
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.google.gson.annotations.Expose;

/**
 * Represents a "grant" of permission to access a device registered to another user account.
 * {@link AylaDevice} objects will have an AylaGrant if the device was shared with this account
 * from another account. Devices owned by the current account will not have an AylaGrant.
 * <p>
 * AylaGrant objects may be obtained via a call to the device's {@link AylaDevice#getGrant()}
 * method.
 */
public class AylaGrant {

    // Example JSON:
    // "grant":{"user_id":1, "start_date_at":"2014-06-17T23:14:33Z",
    // "end_date_at":null, "operation":"write"}

    @Expose
    public String operation;    // Access permissions allowed: either read or write. Used with
                                // create/POST & update/PUT operations. Ex: 'write', Optional
                                // If omitted, the default access permitted is read only

    @Expose
    public String startDateAt;  // When this named resource will be shared. Used with create/POST
                                // & update/PUT operations. Ex: '2014-03-17 12:00:00', Optional
                                // If omitted, the resource will be shared immediately.
                                // UTC DateTime value.

    @Expose
    public String endDateAt;    // When this named resource will stop being shared. Used with
                                // create/POST & update/PUT operations. Ex:
                                // '2020-03-17 12:00:00', Optional

    @Expose
    String userId;              // The target user id that created the original share.
                                // Returned with create/POST & update/PUT operations
                                // If omitted, the resource will be shared until the share or named
                                // resource is deleted. UTC DateTime value
    @Expose
    public String role;         // Role the share was created with.

    // -------------------------- Support Methods ------------------------
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        String NEW_LINE = System.getProperty("line.separator");

        result.append(this.getClass().getName()).append(" Object {").append(NEW_LINE)
                .append(" userId: ").append(userId).append(NEW_LINE)
                .append(" operation: ").append(operation).append(NEW_LINE)
                .append(" startDateAt: ").append(startDateAt).append(NEW_LINE)
                .append(" endDateAt: ").append(endDateAt).append(NEW_LINE)
                .append(" role: ").append(role).append(NEW_LINE)
                .append("}");
        return result.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AylaGrant)) {
            return false;
        }

        AylaGrant otherGrant = (AylaGrant)other;
        return ObjectUtils.equals(this.userId, otherGrant.userId) &&
                ObjectUtils.equals(this.operation, otherGrant.operation) &&
                ObjectUtils.equals(this.startDateAt, otherGrant.startDateAt) &&
                ObjectUtils.equals(this.endDateAt, otherGrant.endDateAt) &&
                ObjectUtils.equals(this.role, otherGrant.role);
    }
}
