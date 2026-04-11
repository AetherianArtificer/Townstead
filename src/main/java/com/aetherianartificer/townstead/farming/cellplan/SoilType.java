package com.aetherianartificer.townstead.farming.cellplan;

public enum SoilType {
    NONE,        // explicitly leave bare / revert
    FARMLAND,    // vanilla minecraft:farmland
    RICH_SOIL,   // farmersdelight:rich_soil
    WATER,       // place water source block
    PROTECTED;   // do not touch this cell at all

    public static SoilType fromName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
