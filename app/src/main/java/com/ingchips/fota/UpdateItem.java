package com.ingchips.fota;

/**
 * Representation of a file (an Item) to be updated
 */
public class UpdateItem {
    String name;
    long writeAddr;
    long loadAddr;
    public byte []data;
}
