package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */
/**
 * Represents a contact to be used with AylaDeviceNotificationApp or AylaPropertyTriggerApp
 *
 */
import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.google.gson.annotations.Expose;

public class AylaContact {
    @Expose
    private Integer id;
    @Expose
    private String displayName;
    @Expose
    private String firstname;
    @Expose
    private String lastname;
    @Expose
    private String email;
    @Expose
    private String phoneCountryCode;
    @Expose
    private String phoneNumber;
    @Expose
    private String streetAddress;
    @Expose
    private String zipCode;
    @Expose
    private String country;
    @Expose
    private String emailAccept;
    @Expose
    private String smsAccept;
    @Expose
    private boolean emailNotification;
    @Expose
    private boolean smsNotification;
    @Expose
    private String metadata;
    @Expose
    private String notes;
    @Expose
    private String updatedAt;
    @Expose
    private String[] oemModels;


    private AylaServiceApp.PushType _pushType = AylaServiceApp.PushType.GooglePush;

    public Integer getId() { return id; }

    public String getDisplayName() { return displayName; }

    public String getFirstname() { return firstname; }

    public String getLastname() { return lastname; }

    public String getEmail() { return email; }

    public String getPhoneCountryCode() { return phoneCountryCode; }

    public String getPhoneNumber() { return phoneNumber; }

    public String getStreetAddress() { return streetAddress; }

    public String getZipCode() { return zipCode; }

    public String getCountry() { return country; }

    public String getEmailAccept() { return emailAccept; }

    public String getSmsAccept() { return smsAccept; }

    public boolean getWantsEmailNotification() { return emailNotification; }

    public boolean getWantsSmsNotification() { return smsNotification; }

    public void setWantsEmailNotification(boolean emailNotification) {
        this.emailNotification = emailNotification;
    }

    public void setWantsSmsNotification(boolean smsNotification) {
        this.smsNotification = smsNotification;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setOemModels(String[] oemModels) {
        this.oemModels = oemModels;
    }

    public String getMetadata() { return metadata; }

    public String getNotes() { return notes; }

    public String getUpdatedAt() { return updatedAt; }

    public String[] getOemModels() { return oemModels; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public void setFirstname(String firstname) { this.firstname = firstname; }

    public void setLastname(String lastname) { this.lastname = lastname; }

    public void setEmail(String email) { this.email = email; }

    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public void setStreetAddress(String streetAddress) { this.streetAddress = streetAddress; }

    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public void setCountry(String country) { this.country = country; }

    public void setPhoneCountryCode(String phoneCountryCode) {
        this.phoneCountryCode = phoneCountryCode;
    }

    public AylaServiceApp.PushType getPushType() {
        return _pushType;
    }

    public void setPushType(AylaServiceApp.PushType _pushType) {
        this._pushType = _pushType;
    }

    public static class Wrapper{
        @Expose
        public AylaContact contact;

        public static AylaContact[] unwrap(Wrapper[] container){
            AylaContact[] aylaContacts = new AylaContact[container.length];
            for( int i = 0; i< container.length; i++){
                aylaContacts[i] = container[i].contact;
            }
            return aylaContacts;
        }
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        final String NEW_LINE = System.getProperty("line.separator");

        result.append(this.getClass().getName()).append("Object {").append(NEW_LINE)
                .append(" id: " + id).append(NEW_LINE)
                .append(" email: " + ObjectUtils.getAnonymizedText(email)).append(NEW_LINE)
                .append(" phone_country_code: " + phoneCountryCode).append(NEW_LINE)
                .append(" phone_number: " + ObjectUtils.getAnonymizedText(phoneNumber))
                .append(NEW_LINE)
                .append(" street_address: " + ObjectUtils.getAnonymizedText(streetAddress))
                .append(NEW_LINE)
                .append(" zip_code: " + ObjectUtils.getAnonymizedText(zipCode)).append(NEW_LINE)
                .append(" country: " + country).append(NEW_LINE)
                .append(" emailAccept: " + emailAccept).append(NEW_LINE)
                .append(" smsAccept: " + smsAccept).append(NEW_LINE)
                .append(" emailNotification: " + emailNotification).append(NEW_LINE)
                .append(" smsNotification: " + smsNotification).append(NEW_LINE)
                .append(" metadata: " + metadata).append(NEW_LINE)
                .append(" notes: " + notes).append(NEW_LINE)
                .append(" updated_at: " + updatedAt).append(NEW_LINE)
                .append("}");
        return result.toString();
    }
}
