package com.aetherianartificer.townstead.switchboard;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Which of Townstead's blocks and items belong to a system that is switched off. Those stay
 * registered, since a world cannot unregister content, but they leave the creative tab and recipe
 * viewers, cannot be crafted, and do nothing when placed or used.
 */
public final class ContentGates {
    private ContentGates() {}

    public static boolean enabled(ItemLike item) {
        return item == null || enabled(BuiltInRegistries.ITEM.getKey(item.asItem()));
    }

    public static boolean enabled(ItemStack stack) {
        return stack == null || stack.isEmpty() || enabled(stack.getItem());
    }

    public static boolean enabled(Block block) {
        return block == null || enabled(BuiltInRegistries.BLOCK.getKey(block));
    }

    /** Townstead's items that belong to a switched-off system right now. */
    public static List<Item> gatedItems() {
        List<Item> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (Townstead.MOD_ID.equals(id.getNamespace()) && !enabled(id)) out.add(item);
        }
        return out;
    }

    public static boolean enabled(ResourceLocation id) {
        if (id == null || !Townstead.MOD_ID.equals(id.getNamespace())) return true;
        String path = id.getPath();
        if (path.equals("calendar")) return Systems.on(Systems.CALENDAR);
        if (path.startsWith("field_post")) return Systems.on(Systems.FARMING);
        if (path.equals("order_sheet") || path.equals("room_ownership_tag")) return Systems.on(Systems.WORK);
        if (path.equals("serving_plate")) return Systems.on(Systems.HOSPITALITY);
        if (path.equals("scarf")) return Systems.on(Systems.CLOTHING);
        if (path.equals("journal")) return Systems.on(Systems.CAREERS);
        if (path.startsWith("room_thermo")) return TownsteadConfig.isVillagerTemperatureEnabled();
        return true;
    }
}
