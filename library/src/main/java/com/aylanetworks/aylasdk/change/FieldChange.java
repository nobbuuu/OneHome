package com.aylanetworks.aylasdk.change;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import java.util.HashSet;
import java.util.Set;

/**
 * A FieldChange object contains a list of the names of fields of an object that have changed.
 * The list of field names that changed may be acquired via the {@link #getChangedFieldNames} method
 */
public class FieldChange extends Change {
    Set<String> _changedFieldNames;

    /**
     * Constructor for FieldChange objects
     * @param changedFieldNames Set of names of fields that changed
     */
    public FieldChange(Set<String> changedFieldNames) {
        super(ChangeType.Field);
        _changedFieldNames = changedFieldNames;
    }

    /**
     * Constructor for FieldChange objects with a single changed field
     * @param changedFieldName Name of the field that changed
     */
    public FieldChange(String changedFieldName) {
        super(ChangeType.Field);
        _changedFieldNames = new HashSet<>(1);
        _changedFieldNames.add(changedFieldName);
    }

    /**
     * Protected constructor for derived classes
     * @param changeType ChangeType for this change class
     * @param changedFieldNames Set of names of fields that changed
     */
    protected FieldChange(ChangeType changeType, Set<String> changedFieldNames) {
        super(changeType);
        _changedFieldNames = changedFieldNames;
    }

    protected FieldChange(ChangeType changeType, String changedFieldName) {
        super(changeType);
        _changedFieldNames = new HashSet<>(1);
        _changedFieldNames.add(changedFieldName);
    }

    public Set<String> getChangedFieldNames() {
        return _changedFieldNames;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("FieldChange: [");
        boolean first = true;
        if ( _changedFieldNames != null ) {
            for (String fieldName : _changedFieldNames) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                sb.append(fieldName);
            }
        }
        sb.append("]");

        return sb.toString();
    }
}
