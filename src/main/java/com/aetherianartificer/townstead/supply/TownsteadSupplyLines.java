package com.aetherianartificer.townstead.supply;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** The supply lines the mod itself ships. Registered once at startup. */
public final class TownsteadSupplyLines {

    //? if >=1.21 {
    public static final ResourceLocation IMPURE_WATER =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "impure_water_container");
    public static final ResourceLocation FURNACE_FUEL =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "furnace_fuel");
    //?} else {
    /*public static final ResourceLocation IMPURE_WATER =
            new ResourceLocation(Townstead.MOD_ID, "impure_water_container");
    public static final ResourceLocation FURNACE_FUEL =
            new ResourceLocation(Townstead.MOD_ID, "furnace_fuel");
    *///?}

    private TownsteadSupplyLines() {}

    public static void bootstrap() {
        SupplyLines.register(new ImpureWaterLine());
        SupplyLines.register(new FurnaceFuelLine());
    }

    /**
     * Water a thirst mod scores as unclean, which the cook boils into something drinkable. Spread
     * across many container ids and readable only through the bridge, which is why it cannot be
     * an ordinary ingredient.
     */
    private static final class ImpureWaterLine implements SupplyLines.Line {

        @Override
        public ResourceLocation id() {
            return IMPURE_WATER;
        }

        @Override
        public boolean active() {
            return ThirstBridgeResolver.get() != null;
        }

        @Override
        public boolean matches(ItemStack stack, ServerLevel level) {
            ThirstCompatBridge bridge = ThirstBridgeResolver.get();
            if (bridge == null) return false;
            return com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler
                    .impureWaterScore(stack, bridge) > 0;
        }
    }

    /**
     * Anything that burns. Always active: the furnace family is vanilla, so this line exists even
     * in a pack with no cooking mods at all.
     */
    private static final class FurnaceFuelLine implements SupplyLines.Line {

        @Override
        public ResourceLocation id() {
            return FURNACE_FUEL;
        }

        @Override
        public boolean active() {
            return true;
        }

        @Override
        public boolean matches(ItemStack stack, ServerLevel level) {
            return !stack.isEmpty() && AbstractFurnaceBlockEntity.isFuel(stack);
        }

        @Override
        public int preference(ItemStack stack, ServerLevel level) {
            // Keep reusable recipe containers out of the fire whenever ordinary fuel exists.
            if (stack.is(Items.BOWL)) return -1_000;
            if (stack.is(Items.COAL_BLOCK)) return 1_000;
            if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return 900;
            if (stack.is(Items.BLAZE_ROD)) return 800;
            if (stack.is(Items.DRIED_KELP_BLOCK)) return 700;
            return 0;
        }
    }
}
