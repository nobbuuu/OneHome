package com.aylanetworks.aylasdk.change;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

/**
 * Change objects are provided to the application whenever the SDK has detected changes to
 * objects, and the applicaiton has requested to listen for changes. The base class defines the
 * various ChangeTypes, which represent the type of Change subclass that the object implements.
 *
 * Each type of Change object contains information specific to what has changed.
 */
public class Change {
    protected ChangeType _changeType;

    /**
     * Constructs a Change object of the given type and no details.
     *
     * @param type Type of Change object
     */
    protected Change(ChangeType type) {
        _changeType = type;
    }

    public ChangeType getType() {
        return _changeType;
    }

    /**
     * Enumeration of the various types of changes that are reported by the Change objects.
     */
    public enum ChangeType {
        Field,      /** Change is a {@link FieldChange} object */
        List,       /** Change is a {@link ListChange} object */
        Property,   /** Change is a {@link PropertyChange} object */
    }
}
