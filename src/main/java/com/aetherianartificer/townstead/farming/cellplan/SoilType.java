package com.aetherianartificer.townstead.farming.cellplan;

public enum SoilType {
    NONE,               // explicitly leave bare / revert
    FARMLAND,           // vanilla minecraft:farmland
    RICH_SOIL,          // farmersdelight:rich_soil (untilled — for mushrooms, saplings, wild growth)
    RICH_SOIL_TILLED,   // farmersdelight:rich_soil_farmland (tilled — for crops that grow faster on rich farmland)
    WATER,              // place water source block
    PROTECTED,          // do not touch this cell at all
    CLAIM;              // placeholder — server resolves this into a real soil+seed based on live world state

    public static SoilType fromName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
