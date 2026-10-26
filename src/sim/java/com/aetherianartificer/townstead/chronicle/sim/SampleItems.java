package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Stand-in contents for an item tag the harness cannot resolve. Sample data for
 * the terminal, never shipped: what a feast actually consists of is decided by
 * the {@code townstead:cook_output} tag in game.
 */
public final class SampleItems {

    public static final List<ResourceLocation> DEFAULT = ids(
            "minecraft:bread", "minecraft:pumpkin_pie", "minecraft:cooked_beef",
            "minecraft:mushroom_stew", "minecraft:cake", "minecraft:baked_potato");

    private SampleItems() {}

    public static List<ResourceLocation> ids(String... values) {
        List<ResourceLocation> ids = new ArrayList<>(values.length);
        for (String value : values) {
            ResourceLocation id = DataPackLang.parseId(value.trim());
            if (id != null) ids.add(id);
        }
        return List.copyOf(ids);
    }
}
