package com.zoocraft.zoo;

/** Actions on a zoo, matching the permission table in spec section 6.4. */
public enum ZooPermission {
    VIEW(false),
    EDIT(false),
    SETTINGS(false),
    FINANCES(false),
    MANAGE_MANAGERS(true),
    TRANSFER(true),
    DELETE(true);

    private final boolean ownerOnly;

    ZooPermission(boolean ownerOnly) {
        this.ownerOnly = ownerOnly;
    }

    public boolean ownerOnly() {
        return ownerOnly;
    }
}
