package com.aylanetworks.aylasdk.change;
/*
 * AylaSDK
 *
 * Copyright 2015 Ayla Networks, all rights reserved
 */

import java.util.List;
import java.util.Set;

/**
 * A ListChange is issued when a list managed by the SDK has had items added or removed to or
 * from it.
 * <p>
 * The change object contains sets of strings identifying the objects that were added or removed
 * to or from the list.
 */
public class ListChange extends Change {
    private List _addedItems;
    private Set<String> _removedIdentifiers;

    public ListChange(List addedItems, Set<String> removedItems) {
        super(ChangeType.List);
        _addedItems = addedItems;
        _removedIdentifiers = removedItems;
    }

    public List getAddedItems() {
        return _addedItems;
    }

    public Set<String> getRemovedIdentifiers() {
        return _removedIdentifiers;
    }
}
