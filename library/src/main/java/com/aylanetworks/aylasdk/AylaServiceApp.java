package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */
import com.google.gson.Gson;
import com.google.gson.annotations.Expose;

/**
 * Abstract class for Applications performed by the service.
 */
public class AylaServiceApp {
    @Expose
    String applicationId;
    @Expose
    String contactId;
    @Expose
    String username;
    @Expose
    String message;
    @Expose
    String emailAddress;
    @Expose
    String countryCode;
    @Expose
    String phoneNumber;
    @Expose
    String registrationId;
    @Expose
    String channelId;
    @Expose
    String pushSound;
    @Expose
    String pushMdata;
    @Expose
    String retrievedAt;
    @Expose
    String nickname;          // User assigned name for this notification app

    AylaEmailTemplate emailTemplate;
    NotificationType _notificationType;

    public String getContactId() { return contactId; }

    public String getUsername() { return username; }

    public String getMessage() { return message; }

    public String getEmailAddress() { return emailAddress; }

    public String getCountryCode() { return countryCode; }

    public String getPhoneNumber() { return phoneNumber; }

    public String getPushSound() { return pushSound; }

    public String getPushMdata() { return pushMdata; }

    public String getRetrievedAt() { return retrievedAt; }

    public String getRegistrationId() { return registrationId; }

    public String getChannelId() { return channelId; }

    public String getNickname() { return nickname; }

    public AylaEmailTemplate getEmailTemplate() { return emailTemplate; }

    public NotificationType getNotificationType() { return _notificationType; }

    public void setNickname(String nickname) { this.nickname = nickname; }

    public void setEmailAddress(String emailAddress) { this.emailAddress=emailAddress; }

    public void setEmailTemplate(AylaEmailTemplate emailTemplate) {
        this.emailTemplate = emailTemplate;
    }

    public void setNotificationType(NotificationType notificationType) {
        this._notificationType = notificationType;
    }

    public String toJSON() {
        Gson gson = AylaNetworks.sharedInstance().getGson();
        return gson.toJson(this);
    }

    public void populateFields() { }

    /**
     * Configures the receiver as AylaNotificationTypeSMS for the specified AylaContact and message
     *
     * @param contact The AylaContact that will receive the app
     * @param message The message of the SMS
     */
    public void configureAsSMS(AylaContact contact, String message) {
        if (contact.getId() != null) {
            contactId = contact.getId().toString();
        } else {
            contactId = null;
        }
        countryCode = contact.getPhoneCountryCode();
        phoneNumber = contact.getPhoneNumber();
        username = contact.getDisplayName();

        this.message = message;
        _notificationType = NotificationType.SMS;
    }

    /**
     * Configures the receiver as AylaNotificationTypeSMS for the specified parameters. This is an
     * alternative method instead of using AylaContact.
     * @param countryCode
     * @param phoneNumber The phone number
     * @param username The username of the app
     * @param message The message of the SMS
     */
    public void configureAsSMS(String countryCode, String phoneNumber,String username,
                               String message) {
        this.countryCode = countryCode;
        this.phoneNumber = phoneNumber;
        this.username = username;
        this.message = message;
        _notificationType = NotificationType.SMS;
        contactId = null;
    }

    /**
     * Configures the receiver as AylaNotificationTypeEmail for the specified AylaContact with the
     * specified message and AylaEmailTemplate
     *
     * @param contact       The AylaContact that will receive the app
     * @param message       The message of the email
     * @param username      The username of the app
     * @param emailTemplate An AylaEmailTemplate with the email specifications
     */
    public void configureAsEmail(AylaContact contact, String message, String username,
                                 AylaEmailTemplate emailTemplate) {
        if (contact != null && contact.getId() != null) {
            this.contactId = contact.getId().toString();
        }
        this.message = message;
        this.username = username;
        this.emailTemplate = emailTemplate;
        _notificationType = NotificationType.EMail;
    }


    /**
     * Configures the receiver as AylaNotificationType for FCM Push defaulting to the
     * application ID specified in the System Settings
     *
     * @param registrationId The registration ID needed for FCM
     * @param message        The Push message for FCM
     * @param pushSound      The sound file name present on mobile app eg: my_sound_file.mp3
     * @param pushMetaData Comma separated data needed by app eg: {"key1":"value1", "key2":"value2"}
     */
    public void configureAsPushFCM(String registrationId, String message,
                                   String pushSound, String pushMetaData) {
        configureAsPushFCM(AylaNetworks.sharedInstance().getSystemSettings().appId,
                registrationId, message, pushSound, pushMetaData);
    }

    /**
     * Configures the receiver as AylaNotificationType for FCM Push
     *
     * @param applicationId Application ID for this app, provided by Ayla Networks
     * @param registrationId The registration ID needed for FCM
     * @param message        The Push message for FCM
     * @param pushSound      The sound file name present on mobile app eg: my_sound_file.mp3
     * @param pushMetaData Comma separated data needed by app eg: {"key1":"value1", "key2":"value2"}
     */
    public void configureAsPushFCM(String applicationId, String registrationId, String message,
                                   String pushSound, String pushMetaData) {
        this.applicationId = applicationId;
        this.registrationId = registrationId;
        this.message = message;
        this.pushSound = pushSound;
        this.pushMdata = pushMetaData;
        _notificationType = NotificationType.FCMPush;
    }

    /**
     * Configures the receiver as AylaNotificationType for Baidu
     *
     * @param applicationId The Application ID
     * @param channelId     The Channel ID
     * @param message       The Push Message
     * @param pushSound     The sound the Phone should emit on receiving this Notification
     * @param pushMetaData  The optional Meta Data to be included in the message
     */
    public void configureAsPushBaidu(String applicationId, String channelId, String message,
                                     String pushSound, String pushMetaData) {
        this.applicationId = applicationId;
        this.channelId = channelId;
        this.message = message;
        this.pushSound = pushSound;
        this.pushMdata = pushMetaData;
        _notificationType = NotificationType.BaiduPush;
    }

    public enum NotificationType {
        SMS("sms"),
        EMail("email"),
        GooglePush("push_android"),
        BaiduPush("push_baidu"),
        FCMPush("push_android_fcm"),
        IOSPush("push_ios");

        NotificationType(String value) {
            _stringValue = value;
        }

        public final String stringValue() {
            return _stringValue;
        }

        public static NotificationType fromString(String name) {
            if (name != null) {
                for (NotificationType notificationType : NotificationType.values()) {
                    if (name.equalsIgnoreCase(notificationType.stringValue())) {
                        return notificationType;
                    }
                }
            }
            return null;
        }

        private final String _stringValue;
    }

    public enum PushType {
        GooglePush("push_android"),
        FCMPush("push_fcm"),
        BaiduPush("push_baidu");

        PushType(String value) {
            _stringValue = value;
        }

        public final String stringValue() {
            return _stringValue;
        }

        public static NotificationType fromString(String name) {
            if (name != null) {
                for (NotificationType notificationType : NotificationType.values()) {
                    if (name.equalsIgnoreCase(notificationType.stringValue())) {
                        return notificationType;
                    }
                }
            }
            return null;
        }

        private final String _stringValue;
    }
}