package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.clothing.ClothingDefs;
import com.aetherianartificer.townstead.clothing.ClothingEntry;
import com.aetherianartificer.townstead.clothing.ClothingSources;
import com.aetherianartificer.townstead.clothing.ClothingThermal;
import com.aetherianartificer.townstead.clothing.WornPiece;
import com.aetherianartificer.townstead.compat.temperature.AmbientTemperatureBridge;
import com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * What the entity wears, in degrees, summed over every worn piece {@link ClothingSources} reports.
 *
 * <p>One stack resolves in a fixed order and the first opinion wins: an installed temperature
 * backend, then garment data mods ship for a backend that is absent, then Townstead clothing
 * documents, then the two tags, then nothing. A skin resolves through clothing documents and
 * MCA's own climate field. The caller clamps the sum so a full outfit moves one tier and no
 * further.</p>
 */
public final class Insulation {
    //? if >=1.21 {
    public static final TagKey<Item> WARM_CLOTHING = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "warm_clothing"));
    public static final TagKey<Item> COOL_CLOTHING = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "cool_clothing"));
    //?} else {
    /*public static final TagKey<Item> WARM_CLOTHING = TagKey.create(Registries.ITEM,
            new ResourceLocation(Townstead.MOD_ID, "warm_clothing"));
    public static final TagKey<Item> COOL_CLOTHING = TagKey.create(Registries.ITEM,
            new ResourceLocation(Townstead.MOD_ID, "cool_clothing"));
    *///?}

    private static final float TAGGED_PIECE = 0.5f;

    private Insulation() {}

    /** Collect flat offsets and directional resistance separately, across every worn layer. */
    public static ThermalProtection clothingProtection(LivingEntity entity) {
        if (entity == null) return ThermalProtection.NONE;
        ThermalProtection[] total = {ThermalProtection.NONE};
        Level level = entity.level();
        ClothingSources.collect(entity, piece -> total[0] = total[0].plus(pieceProtection(level, piece)));
        return total[0];
    }

    public static ThermalProtection pieceProtection(@Nullable Level level, WornPiece piece) {
        if (piece == null) return ThermalProtection.NONE;
        if (piece.isStack()) return itemProtection(level, piece.stack(), piece.entry());
        if (piece.isSkin()) return ClothingThermal.skinProtection(piece.skin());
        return piece.entry() != null && piece.entry().thermal() != null ? piece.entry().thermal() : ThermalProtection.NONE;
    }

    public static ThermalProtection itemProtection(ItemStack stack) {
        return itemProtection(null, stack, null);
    }

    /**
     * @param entry the clothing entry already resolved for this stack by the source, or null to
     *              look it up here
     */
    public static ThermalProtection itemProtection(@Nullable Level level, ItemStack stack, @Nullable ClothingEntry entry) {
        if (stack == null || stack.isEmpty()) return ThermalProtection.NONE;
        for (AmbientTemperatureBridge bridge : TemperatureBridgeResolver.installed()) {
            ThermalProtection protection = bridge.itemProtection(stack);
            if (protection != null) return protection;
        }
        ThermalProtection provided = ClothingThermal.fromProviders(stack);
        if (provided != null) return provided;
        ClothingEntry resolved = entry != null ? entry : ClothingDefs.forStack(level, stack);
        if (resolved != null && resolved.thermal() != null) return resolved.thermal();
        ThermalProtection tagged = taggedProtection(stack);
        return tagged != null ? tagged : ThermalProtection.NONE;
    }

    /** One tagged piece's worth of warmth or cooling, or null when the stack carries neither tag. */
    public static @Nullable ThermalProtection taggedProtection(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.is(WARM_CLOTHING)) return new ThermalProtection(TAGGED_PIECE, 0, 0, 0);
        if (stack.is(COOL_CLOTHING)) return new ThermalProtection(-TAGGED_PIECE, 0, 0, 0);
        return null;
    }
}
