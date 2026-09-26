package com.aetherianartificer.townstead.clothing;

import com.aetherianartificer.townstead.temperature.ThermalProtection;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.data.skin.Clothing;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Thermal opinions that sit between the temperature backends and Townstead's own documents.
 *
 * <p>A provider source answers for stacks from data a mod ships for a temperature mod that is
 * not installed, so a pack without that mod still reads the garments the way their author
 * intended. Skins answer through MCA's own {@code temperature} field, which says which climate a
 * skin is used in: a cold-climate skin is warm clothing.</p>
 */
public final class ClothingThermal {

    /**
     * Degrees of body offset per step of MCA's skin temperature (-2 cold to 2 hot). A cold-climate
     * skin is worth one tagged piece, so a whole outfit still sits inside the clothing clamp.
     */
    public static final float MCA_SKIN_DEGREES_PER_STEP = 0.25f;

    private static final List<Function<ItemStack, ThermalProtection>> STACK_SOURCES = new CopyOnWriteArrayList<>();

    private ClothingThermal() {}

    /** A source answers null when it has no opinion. Later registrations are asked later. */
    public static void registerStackSource(Function<ItemStack, ThermalProtection> source) {
        if (source != null && !STACK_SOURCES.contains(source)) STACK_SOURCES.add(source);
    }

    public static @Nullable ThermalProtection fromProviders(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (Function<ItemStack, ThermalProtection> source : STACK_SOURCES) {
            ThermalProtection protection = source.apply(stack);
            if (protection != null) return protection;
        }
        return null;
    }


    /** Curios slot tags, most specific first, so a stack's channel can be read without a slot. */
    private static final String[] CURIO_SLOT_TAGS = {"head", "hat", "necklace", "choker_trinket", "back", "cape",
            "belt", "hands", "gloves", "bracelet", "ring", "body", "upperwear", "legs", "pants", "legwear",
            "feet", "shoes"};

    /** Drops cached synthetic stack entries; every reload of documents or provider data calls it. */
    public static void invalidate() {
    }

    /**
     * An entry for a stack no document describes but a provider knows the warmth of, so the
     * dress rules see a Weavers' Paradise sweater the way insulation already does. The channel
     * comes from the armor slot or the Curios slot tags; body, legs, and feet are outerwear, the
     * rest accessories, armor items armour. The warm and cool clothing tags count as an opinion.
     * Null when nothing has one.
     */
    public static @Nullable ClothingEntry syntheticStackEntry(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) return null;
        // Protection can depend on lining/components, not just item id. Do not cache stack opinions.
        ResourceLocation id = itemId;
            ThermalProtection thermal = com.aetherianartificer.townstead.temperature.Insulation.resolve(stack, null);
            if (thermal.equals(ThermalProtection.NONE)) return null;
            boolean armor = stack.getItem() instanceof ArmorItem;
            ClothingChannel channel = null;
            if (armor) {
                channel = ClothingChannel.of(((ArmorItem) stack.getItem()).getEquipmentSlot());
            } else {
                for (String slot : CURIO_SLOT_TAGS) {
                    ResourceLocation tag = com.aetherianartificer.townstead.data.DataPackLang.parseId("curios:" + slot);
                    if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) {
                        channel = ClothingChannel.ofCurioSlot(slot);
                        break;
                    }
                }
            }
            return syntheticStackEntry(id, thermal, channel, armor);
    }

    /** The pure part of {@link #syntheticStackEntry(ItemStack)}. */
    public static @Nullable ClothingEntry syntheticStackEntry(ResourceLocation itemId, @Nullable ThermalProtection thermal,
                                                              @Nullable ClothingChannel channel, boolean armor) {
        if (itemId == null || thermal == null || thermal.equals(ThermalProtection.NONE)) return null;
        ResourceLocation id = com.aetherianartificer.townstead.data.DataPackLang.parseId(
                "townstead:provider/" + itemId.getNamespace() + "/" + itemId.getPath());
        if (id == null) return null;
        ClothingChannel slot = channel == null ? ClothingChannel.ALL : channel;
        ClothingLayer layer;
        if (armor) layer = ClothingLayer.ARMOUR;
        else if (slot == ClothingChannel.BODY || slot == ClothingChannel.LEGS || slot == ClothingChannel.FEET
                || slot == ClothingChannel.ALL) layer = ClothingLayer.OUTERWEAR;
        else layer = ClothingLayer.ACCESSORY;
        return new ClothingEntry(id, itemId, null, null, null, layer, slot, thermal,
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), null);
    }

    /** A skin's warmth: a clothing document first, then MCA's own climate field, else none. */
    public static ThermalProtection skinProtection(@Nullable String skinId) {
        if (skinId == null || skinId.isEmpty()) return ThermalProtection.NONE;
        ClothingEntry entry = ClothingDefs.forSkin(skinId);
        if (entry != null && entry.thermal() != null) return entry.thermal();
        Integer climate = mcaSkinClimate(skinId);
        return climate == null ? ThermalProtection.NONE : fromMcaClimate(climate);
    }

    /** MCA's climate step for a catalogue skin, or null when the catalogue does not know it. */
    static @Nullable Integer mcaSkinClimate(String skinId) {
        try {
            ClothingList list = ClothingList.getInstance();
            if (list == null || list.clothing == null) return null;
            Clothing clothing = list.clothing.get(skinId);
            return clothing == null ? null : clothing.temperature;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Cold-climate skins warm the wearer; hot-climate skins cool them. */
    public static ThermalProtection fromMcaClimate(int climate) {
        if (climate == 0) return ThermalProtection.NONE;
        return new ThermalProtection(-climate * MCA_SKIN_DEGREES_PER_STEP, 0, 0, 0);
    }

    /**
     * An entry for a catalogue skin no document describes, carrying only what MCA itself says
     * about it, so queries such as "any warm base" see the whole catalogue and not only the
     * skins a pack wrote up. Null when the catalogue does not know the skin.
     */
    public static @Nullable ClothingEntry syntheticSkinEntry(@Nullable String skinId) {
        if (skinId == null || skinId.isEmpty()) return null;
        Integer climate = mcaSkinClimate(skinId);
        if (climate == null) return null;
        ThermalProtection thermal = fromMcaClimate(climate);
        ResourceLocation id = com.aetherianartificer.townstead.data.DataPackLang.parseId(
                "townstead:mca_skin/" + Integer.toHexString(skinId.hashCode()));
        return new ClothingEntry(id, null, null, skinId, null, ClothingLayer.BASE, ClothingChannel.ALL,
                thermal.equals(ThermalProtection.NONE) ? null : thermal, java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of(), null);
    }
}
