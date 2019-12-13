package com.aylanetworks.aylasdk.localdevice;

import com.google.gson.annotations.Expose;

/*
 * Ayla SDK
 *
 * Copyright 2017 Ayla Networks, all rights reserved
 */

public class AylaLocalRegistrationCandidate {
    @Expose
    public Device device = new Device();

    public class Device {
        @Expose public String device_type = "Node";
        @Expose public String unique_hardware_id;
        @Expose public String oem_model;
        @Expose public String oem;
        @Expose public String model;
        @Expose public String sw_version;
        @Expose public Subdevice[] subdevices;
    }

    public static class Subdevice {
        @Expose public String subdevice_key;
        @Expose public Template[] templates;
    }

    public static class Template {
        @Expose public String template_key;
        @Expose public String version;
    }
}
