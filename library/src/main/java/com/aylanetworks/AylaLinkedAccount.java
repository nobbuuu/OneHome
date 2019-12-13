package com.aylanetworks;

import com.google.gson.annotations.Expose;

/**
 * Describes an account linked to the current user. One of email, phone, or username should
 * be provided.
 */
public class AylaLinkedAccount {
    public AylaLinkedAccount() {

    }

    public AylaLinkedAccount(String email, String username, String phone, String oemName,
                             String oemString) {
       this.email = email;
       this.username = username;
       this.phone = phone;
       this.oemName = oemName;
       this.oemString = oemString;
    }

    @Expose public String email;
    @Expose public String username;
    @Expose public String phone;
    @Expose public String oemName;
    @Expose public String oemString;
}
