package com.aetherianartificer.townstead.compat.mca;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Expands stateful, same-tile item stacks into the units represented by that block. */
public final class BuildingBlockQuantity {
    private static final String STACK_PROPERTY = "stack";

    private BuildingBlockQuantity() {}

    public static int units(BlockState state) {
        if (state == null || state.isAir()) return 0;
        for (Property<?> property : state.getProperties()) {
            Object value = state.getValue(property);
            int units = units(property.getName(), value);
            if (units > 1) return units;
        }
        return 1;
    }

    static int units(String propertyName, Object value) {
        if (!STACK_PROPERTY.equals(propertyName) || !(value instanceof Integer count)) return 1;
        return Math.max(1, count);
    }
}
