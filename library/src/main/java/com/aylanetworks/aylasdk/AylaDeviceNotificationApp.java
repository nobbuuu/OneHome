package com.aylanetworks.aylasdk;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import android.text.TextUtils;

import com.google.gson.annotations.Expose;

/**
 * AylaDeviceNotificationApp objects represent actions that are taken in response to an
 * {@link AylaDeviceNotification} being fired. These apps may be used to notify users via email,
 * SMS message or push notification for the appropriate platform.
 */
@Deprecated
public class AylaDeviceNotificationApp extends AylaServiceApp {
    @Expose
    private String appType;
    @Expose
    AylaDeviceNotificationAppParams notification_app_parameters;
    @Expose
    Number notificationId;    // The ID of the owner device notification
    @Expose
    Number id;                // The ID of this notification application

    Number getNotificationId() {
        return notificationId;
    }

    Number getId() {
        return id;
    }

    public AylaDeviceNotificationAppParams getParameters() {
        if(notification_app_parameters == null){
            notification_app_parameters = new AylaDeviceNotificationAppParams();
        }
        return notification_app_parameters;
    }

    @Override
    public String toJSON() {
        appType = _notificationType.stringValue();
        if(notification_app_parameters == null){
            notification_app_parameters = new AylaDeviceNotificationAppParams();
        }
        notification_app_parameters.contact_id = contactId;
        switch (_notificationType) {
            case SMS:
                if (TextUtils.isEmpty(countryCode)) {
                    notification_app_parameters.country_code = "1";// country code is required by
                    // service, By default US.
                } else {
                    notification_app_parameters.country_code = countryCode.replaceFirst("^0*", "");
                }
                notification_app_parameters.phone_number = phoneNumber;
                notification_app_parameters.message = message;
                notification_app_parameters.username = username;
                break;
            case EMail:
                if (emailTemplate != null) {
                    notification_app_parameters.email_template_id = emailTemplate.
                            getEmailTemplateId();
                    notification_app_parameters.email_subject = emailTemplate.getEmailSubject();
                    notification_app_parameters.email_body_html = emailTemplate.getEmailBodyHtml();
                    notification_app_parameters.email = emailAddress;
                }
                notification_app_parameters.message = message;
                notification_app_parameters.username = username;
                break;
            case FCMPush:
                notification_app_parameters.app_id = applicationId;
                // pass-through
            case GooglePush:
                notification_app_parameters.registration_id = registrationId;
                notification_app_parameters.push_mdata = pushMdata;
                notification_app_parameters.push_sound = pushSound;
                notification_app_parameters.message = message;
                break;
            case BaiduPush:
                notification_app_parameters.app_id = this.applicationId;
                notification_app_parameters.push_mdata = pushMdata;
                notification_app_parameters.push_sound = pushSound;
                notification_app_parameters.channel_id = channelId;
                break;
            default:
                return null;
        }
        Wrapper notifAppWrapper = new Wrapper();
        notifAppWrapper.notification_app = this;
        return AylaNetworks.sharedInstance().getGson().toJson(notifAppWrapper, Wrapper.class);
    }

    @Override
    public void populateFields() {
        if (appType == null || notification_app_parameters == null)
            return;

        contactId = notification_app_parameters.contact_id;
        _notificationType = NotificationType.fromString(appType);
        if (_notificationType == null)
            return;

        switch (_notificationType) {
            case SMS:
                countryCode = notification_app_parameters.country_code;
                phoneNumber = notification_app_parameters.phone_number;
                message = notification_app_parameters.message;
                username = notification_app_parameters.username;
                break;
            case EMail:
                emailAddress = notification_app_parameters.email;
                emailTemplate = new AylaEmailTemplate();
                emailTemplate.setEmailTemplateId(notification_app_parameters.email_template_id);
                emailTemplate.setEmailSubject(notification_app_parameters.email_subject);
                emailTemplate.setEmailBodyHtml(notification_app_parameters.email_body_html);
                username = notification_app_parameters.username;
                message = notification_app_parameters.message;
                break;
            case GooglePush:
            case FCMPush:
                registrationId = notification_app_parameters.registration_id;
                pushMdata = notification_app_parameters.push_mdata;
                pushSound = notification_app_parameters.push_sound;
                message = notification_app_parameters.message;
                break;
            case BaiduPush:
                applicationId = notification_app_parameters.app_id;
                pushMdata = notification_app_parameters.push_mdata;
                pushSound = notification_app_parameters.push_sound;
                channelId = notification_app_parameters.channel_id;
                break;
            case IOSPush:
                applicationId = notification_app_parameters.app_id;
                pushMdata = notification_app_parameters.push_mdata;
                pushSound = notification_app_parameters.push_sound;
                registrationId = notification_app_parameters.registration_id;
                break;
            default:
                break;
        }
    }

    public static class Wrapper {
        @Expose
        AylaDeviceNotificationApp notification_app;

        public AylaDeviceNotificationApp getNotificationApp() {
            notification_app.populateFields();
            return notification_app;
        }

        public static AylaDeviceNotificationApp[] unwrap(Wrapper[] wrappedNotifications) {
            int size = 0;
            if (wrappedNotifications != null) {
                size = wrappedNotifications.length;
            }

            AylaDeviceNotificationApp[] notificationApps = new AylaDeviceNotificationApp[size];
            for (int i = 0; i < size; i++) {
                notificationApps[i] = wrappedNotifications[i].notification_app;
                notificationApps[i].populateFields();
            }
            return notificationApps;
        }
    }

    public static class AylaDeviceNotificationAppParams {
        @Expose
        public String contact_id;
        @Expose
        public String email;
        @Expose
        public String email_template_id;
        @Expose
        public String email_subject;
        @Expose
        public String email_body_html;
        @Expose
        public String registration_id;
        @Expose
        public String push_mdata;
        @Expose
        public String push_sound;
        @Expose
        public String country_code;
        @Expose
        public String phone_number;
        @Expose
        public String app_id;
        @Expose
        public String channel_id;
        @Expose
        public String message;
        @Expose
        public String username;

    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        String NEW_LINE = System.getProperty("line.separator");

        result.append(this.getClass().getName() + " Object {" + NEW_LINE);
        result.append(" appType: " + appType + NEW_LINE);
        result.append(" nickname: " + nickname + NEW_LINE);
        result.append(" notificationId: " + notificationId + NEW_LINE);
        result.append(" username: " + username + NEW_LINE);
        result.append(" pushSound: " + pushSound + NEW_LINE);
        result.append(" pushMdata: " + pushMdata + NEW_LINE);
        result.append(" message: " + message + NEW_LINE);
        result.append("}");
        return result.toString();
    }

}
