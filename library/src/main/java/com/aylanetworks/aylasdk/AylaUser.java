package com.aylanetworks.aylasdk;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.change.Change;
import com.aylanetworks.aylasdk.change.FieldChange;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.InvalidArgumentError;
import com.aylanetworks.aylasdk.error.JsonError;
import com.aylanetworks.aylasdk.util.ObjectUtils;
import com.google.gson.annotations.Expose;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * Class containing information about the owner of the signed-in account. May be obtained via
 * {@link AylaSessionManager#fetchUserProfile(Response.Listener, ErrorListener)} or modified via
 * {@link AylaSessionManager#updateUserProfile(AylaUser, Response.Listener, ErrorListener)}.
 */
public class AylaUser {
    // JSON variables
    @Expose
    private String email;
    @Expose
    private String password;
    @Expose
    private String firstname;
    @Expose
    private String lastname;
    @Expose
    private String country;
    @Expose
    private String street;
    @Expose
    private String city;
    @Expose
    private String state;
    @Expose
    private String zip;
    @Expose
    private String phoneCountryCode;
    @Expose
    private String phone;
    @Expose
    private String aylaDevKitNum;
    @Expose
    private String uuid;
    @Expose
    private String company;

    /**
     * Public empty constructor
     */
    public AylaUser() {}

    /**
     * Public copy constructor
     * @param other object to copy
     */
    public AylaUser(AylaUser other) {
        email = other.email;
        password = other.password;
        firstname = other.firstname;
        lastname = other.lastname;
        country = other.country;
        street = other.street;
        city = other.city;
        state = other.state;
        zip = other.zip;
        phoneCountryCode = other.phoneCountryCode;
        phone = other.phone;
        aylaDevKitNum = other.aylaDevKitNum;
        uuid = other.getUuid();
        company = other.company;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getPhoneCountryCode() {
        return phoneCountryCode;
    }

    public void setPhoneCountryCode(String phoneCountryCode) {
        this.phoneCountryCode = phoneCountryCode;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getAylaDevKitNum() {
        return aylaDevKitNum;
    }

    public void setAylaDevKitNum(String aylaDevKitNum) {
        this.aylaDevKitNum = aylaDevKitNum;
    }

    public synchronized Change updateFrom(AylaUser other) {
        Set<String> changedFields = new HashSet<>();

        AylaUser user = (AylaUser) other;
        if (!ObjectUtils.equals(user.email, email)) {
            this.email = user.email;
            changedFields.add("email");
        }
        if (!ObjectUtils.equals(user.password, password)) {
            this.password = user.password;
            changedFields.add("password");
        }
        if (!ObjectUtils.equals(user.city, city)) {
            this.city = user.city;
            changedFields.add("city");
        }
        if (!ObjectUtils.equals(user.firstname, firstname)) {
            this.firstname = user.firstname;
            changedFields.add("firstname");
        }
        if (!ObjectUtils.equals(user.lastname, lastname)) {
            this.lastname = user.lastname;
            changedFields.add("lastname");
        }
        if (!ObjectUtils.equals(user.country, country)) {
            this.country = user.country;
            changedFields.add("country");
        }
        if (!ObjectUtils.equals(user.street, street)) {
            this.street = user.street;
            changedFields.add("street");
        }
        if (!ObjectUtils.equals(user.state, state)) {
            this.state = user.state;
            changedFields.add("state");
        }
        if (!ObjectUtils.equals(user.zip, zip)) {
            this.zip = user.zip;
            changedFields.add("zip");
        }
        if (!ObjectUtils.equals(user.phoneCountryCode, phoneCountryCode)) {
            this.phoneCountryCode = user.phoneCountryCode;
            changedFields.add("phoneCountryCode");
        }
        if (!ObjectUtils.equals(user.phone, phone)) {
            this.phone = user.phone;
            changedFields.add("phone");
        }
        if (!ObjectUtils.equals(user.aylaDevKitNum, aylaDevKitNum)) {
            this.aylaDevKitNum = user.aylaDevKitNum;
            changedFields.add("aylaDevKitNum");
        }
        if (!ObjectUtils.equals(user.company, company)) {
            this.company = user.company;
            changedFields.add("company");
        }

        if (changedFields.isEmpty()) {
            return null;
        }

        return new FieldChange(changedFields);
    }

    /**
     * Fills out the supplied argumentMap with values that should be sent to the server for an
     * update profile operation.
     *
     * @param userJson JSONObject of arguments to be filled out by this method
     * @return An AylaError if encountered, or null if no error occurred
     */
    public AylaError prepareUpdateArguments(JSONObject userJson) {
        try {
            if (getPassword() != null) {
                return new InvalidArgumentError("Password must be null. To " +
                        "update the password, call AylaLoginManager.changePassword instead.");
            }

            if (getFirstname() == null) {
                return new InvalidArgumentError("First name may not be null");
            }
            if (getFirstname().length() < 2) {
                return new InvalidArgumentError("First name must be greater than one character");
            }
            userJson.put("firstname", getFirstname());

            if (getLastname() == null) {
                return new InvalidArgumentError("Last name may not be null");
            }
            if (getLastname().length() < 2) {
                return new InvalidArgumentError("Last name must be greater than one character");
            }
            userJson.put("lastname", getLastname());

            // These fields are all included, even if they are null
            userJson.put("country", getCountry());
            userJson.put("zip", getZip());
            userJson.put("phone_country_code", getPhoneCountryCode());
            userJson.put("phone", getPhone());
            userJson.put("ayla_dev_kit_num", getAylaDevKitNum());
            userJson.put("street", getStreet());
            userJson.put("city", getCity());
            userJson.put("state", getState());
            userJson.put("company", getCompany());
        } catch (JSONException e) {
            return new JsonError(null, "JSONException while creating arguments for AylaUser", e);
        }

        return null;
    }

    /**
     * @return Returns UUID for this user.
     */
    public String getUuid() {
        return uuid;
    }
}
