package com.aylanetworks.aylasdk;

import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.google.gson.annotations.Expose;

import java.io.Serializable;

/*
 * Android_AylaSDK
 *
 * Copyright 2016 Ayla Networks, all rights reserved
 */

/**
 * Contains information about a device that is shared between accounts.
 */
public class AylaShare implements Serializable{

    @Expose
    private String id;          //Unique share id
    @Expose
    private String createdAt;
    @Expose
    private String updatedAt;
    @Expose
    private String startDateAt;     // UTC DateTime at which the share begins in the format
    // YYYY-MM-DDTHH:MM:SSZ . If this field is empty, the device is shared immediately
    @Expose
    private String endDateAt;       //UTC DateTime at which the share ends in the format
    // YYYY-MM-DDTHH:MM:SSZ If this field is empty, the device will be shared
    // till the share or device is deleted.
    @Expose
    private String status;
    @Expose
    private String operation;       // Represents access permission for the share. Value is either
    // read or write. If this field is empty, this share is read-only
    @Expose
    private String ownerId;         // User id of the device owner that craeted this share.
    @Expose
    private String resourceId;      // Unique identifier for the resource being shared.
    // eg. AC000W0000001234
    @Expose
    private String resourceName;    // Name of the resource being shared.
    @Expose
    private String userId;          // Id of the user that received the share
    @Expose
    private Role role;

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    @Expose
    private String userEmail;       // Email of the user that received the share
    @Expose
    private String grantId;         // Unique grant id associated with this share.
    @Expose
    private AylaShareOwnerProfile ownerProfile;
    @Expose
    private AylaShareUserProfile userProfile;

    public class Role {
        @Expose
        public String name;
        public Role() {
            name = null;
        }
        public Role(String roleName) {
            name = roleName;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "role.name: " + name;
        }
    }

    public AylaShare(){
        this.role = new Role();
    }

    public AylaShare(String userEmail, String operation, String resourceId, String resourceName,
                     String roleName, String startDateAt, String endDateAt) {
        this.operation = operation;
        this.resourceId = resourceId;
        this.resourceName = resourceName;
        this.userEmail = userEmail;
        this.role = new Role(roleName);
        this.startDateAt = startDateAt;
        this.endDateAt = endDateAt;
    }

    public void setStartDateAt(String startDateAt) {
        this.startDateAt = startDateAt;
    }

    public void setEndDateAt(String endDateAt) {
        this.endDateAt = endDateAt;
    }

    public void setRoleName(String roleName) {
        if (role == null) {
            role = new Role(roleName);
        } else {
            role.name = roleName;
        }
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public enum ShareAccessLevel{
        READ("read"),
        WRITE("write");

        private String _stringValue;

        ShareAccessLevel(String value) {
            _stringValue = value;
        }

        public String stringValue() {
            return _stringValue;
        }
    }

    public static class Wrapper{
        @Expose
        AylaShare share;
        public static AylaShare[] unwrap(Wrapper[] container){
            AylaShare shares[] = new AylaShare[container.length];
            for(int i=0; i< shares.length; i++){
                shares[i] = container[i].share;
            }
            return shares;
        }
    }

    /**
     * Returns the profile of the owner of this share
     * @return the owner profile
     */
    public AylaShareOwnerProfile getOwnerProfile() {
        return ownerProfile;
    }

    /**
     * Returns the profile of the recipient of this share
     * @return the recipient profile
     */
    public AylaShareUserProfile getUserProfile() {
        return userProfile;
    }

    public String getId() {
        return id;
    }
    public String getResourceName() {
        return resourceName;
    }

    public String getResourceId() {
        return resourceId;
    }

    public Role getRole() {
        return role;
    }

    public String getRoleName() {
        if (role == null) {
            return null;
        }

        return role.name;
    }

    public String getStartDateAt() {
        return startDateAt;
    }

    public String getEndDateAt() {
        return endDateAt;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        final String NEW_LINE = System.getProperty("line.separator");

        result.append(this.getClass().getName()).append("Object {").append(NEW_LINE)
                .append(" id: " + id).append(NEW_LINE)
                .append(" status "+status).append(NEW_LINE)
                .append(" operation: "+operation).append(NEW_LINE)
                .append(" grantId: "+grantId).append(NEW_LINE)
                .append(" email: " + ObjectUtils.getAnonymizedText((String) userEmail))
                .append(NEW_LINE)
                .append(" resourceId: "+resourceId).append(NEW_LINE)
                .append(" resourceName: "+resourceName).append(NEW_LINE)
                .append(" role: "+role).append(NEW_LINE)
                .append(" startDateAt: "+startDateAt).append(NEW_LINE)
                .append(" updated_at: " + updatedAt).append(NEW_LINE)
                .append(" endDateAt: "+endDateAt).append(NEW_LINE)
                .append("}");
        return result.toString();
    }
}
