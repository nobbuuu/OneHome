package com.aylanetworks.aylasdk.change;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import com.aylanetworks.aylasdk.AylaDatapoint;
import com.aylanetworks.aylasdk.AylaProperty;

import java.util.Set;

/**
 * A PropertyChange is used to notify listeners of the details of a Device's property changing.
 * Changes can include the property's value, timestamp, or any other field.
 *
 * The PropertyChange object inherits from FieldChange, and adds the getPropertyName method to
 * indicate which property changed.
 */
public class PropertyChange<T> extends FieldChange {

    /**
     * Property name.
     */
    final private String _propertyName;

    /**
     * The value at the moment the property was changed.
     */
    final private T _value;

    final private AylaDatapoint<T> _datapoint;

    /**
     * Constructs a PropertyChange with the given property name and field change. If the
     * fieldChange parameter is null, the property itself was added or removed rather than a
     * field within it changing.
     *
     * @param propertyName Name of the property that changed
     * @param changedFieldNames Set of names of fields that changed within this object.
     *                          Properties generally will only change their value and timestamps.
     * @param value The value at the moment the property was changed.
     */
    public PropertyChange(String propertyName, Set<String> changedFieldNames, T value) {
        super(ChangeType.Property, changedFieldNames);
        _propertyName = propertyName;
        _value = value;
        _datapoint = null;
    }

    /**
     * Constructs a PropertyChange with the given property name and field change. If the
     * fieldChange parameter is null, the property itself was added or removed rather than a
     * field within it changing.
     *
     * @param propertyName Name of the property that changed
     * @param changedFieldNames Set of names of fields that changed within this object.
     *                          Properties generally will only change their value and timestamps.
     */
    public PropertyChange(String propertyName, Set<String> changedFieldNames) {
        this(propertyName, changedFieldNames, (T) null);
    }

    /**
     * Constructs a PropertyChange indicating that the value field has changed
     * @param propertyName Name of the property whose value changed
     * @param value The value at the moment the property was changed.
     */
    public PropertyChange(String propertyName, T value) {
        super(ChangeType.Property, "value");
        _propertyName = propertyName;
        _value = value;
        _datapoint = null;
    }

    public PropertyChange(String propertyName, Set<String> changedFieldNames, AylaDatapoint<T> datapoint) {
        super(ChangeType.Property, changedFieldNames);
        _propertyName = propertyName;
        _value = datapoint.getValue();
        _datapoint = datapoint;
    }

    /**
     * Constructs a PropertyChange indicating that the value field has changed
     * @param propertyName Name of the property whose value changed
     */
    public PropertyChange(String propertyName) {
        this(propertyName, (T) null);
    }


    public String getPropertyName() {
        return _propertyName;
    }

    /**
     * Returns the recieved AylaDatapoint, if one was received. If the change was a result of an
     * operation that did not include a datapoint, such as polling properties or some LAN-mode
     * updates, this method will return null.
     *
     * @return The AylaDatapoint received to generate this change, or null if no datapoint was
     *         received.
     */
    public AylaDatapoint<T> getDatapoint() {
        return _datapoint;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("PropertyChange: [");
        sb.append(_propertyName);
        sb.append("], ");
        sb.append("changedValue:");
        sb.append(_value);
        sb.append(", ");
        sb.append(super.toString());
        return sb.toString();
    }

    /**
     * Get the value accompanied with this property change.
     * @return the state value of the property, not necessarily the most recent property value,
     * which can be fetched via a call to {@link AylaProperty#getValue()}
     */
    public T getValue() {
        return _value;
    }
}
