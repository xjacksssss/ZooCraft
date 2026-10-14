package com.zoocraft.dashboard;

/** Buttons on the dashboard. The payload's number and text fields carry the values noted here. */
public enum DashboardAction {
    OPEN_ZOO,
    CLOSE_ZOO,
    /** number = price */
    SET_PRICE,
    /** number = 1 on, 0 off */
    KEEP_LOADED,
    /** number = emeralds */
    DEPOSIT,
    /** number = emeralds */
    WITHDRAW,
    /** text = player name */
    ADD_MANAGER,
    /** text = manager UUID */
    REMOVE_MANAGER,
    /** text = player name */
    TRANSFER,
    /** text = "shopId|itemId", number = price */
    SHOP_PRICE,
    /** text = shopId, number = 1 open, 0 closed */
    SHOP_OPEN,
    /** text = "shopId|new name" */
    SHOP_RENAME
}
