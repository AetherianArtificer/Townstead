package com.aetherianartificer.townstead.clothing;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.item.ItemCondition;
import com.aetherianartificer.townstead.pheno.condition.item.ItemConditions;
import com.aetherianartificer.townstead.profession.def.ClothingChoice;
import com.aetherianartificer.townstead.temperature.ThermalProtection;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * One thing a villager can wear, described from Townstead's side: what it is, where it sits, how
 * warm it is, what it is made of, and what it expresses.
 *
 * <p>An entry names exactly one of an item, an item tag, or an MCA skin. Skins are the base layer
 * and are not stacks, which is why the entry, not the stack, is the unit the rest of the engine
 * reasons about.</p>
 */
public record ClothingEntry(ResourceLocation id,
                            @Nullable ResourceLocation item,
                            @Nullable ResourceLocation tag,
                            @Nullable String skin,
                            @Nullable ItemCondition stack,
                            ClothingLayer layer,
                            ClothingChannel slot,
                            @Nullable ThermalProtection thermal,
                            Set<String> materials,
                            Set<String> occasions,
                            Set<String> spirits,
                            @Nullable ClothingChoice.HairPolicy hair) {

    /** The mod that supplies the piece: an item namespace, or {@code mca_skin} for a skin. */
    public static final String MCA_SKIN_SOURCE = "mca_skin";

    public ClothingEntry {
        materials = materials == null ? Set.of() : Set.copyOf(materials);
        occasions = occasions == null ? Set.of() : Set.copyOf(occasions);
        spirits = spirits == null ? Set.of() : Set.copyOf(spirits);
        if (layer == null) layer = skin != null ? ClothingLayer.BASE : ClothingLayer.OUTERWEAR;
        if (slot == null) slot = skin != null ? ClothingChannel.ALL : ClothingChannel.BODY;
    }

    public boolean isSkin() {
        return skin != null;
    }

    public String source() {
        if (skin != null) return MCA_SKIN_SOURCE;
        if (item != null) return item.getNamespace();
        return tag != null ? tag.getNamespace() : "";
    }

    public boolean isWarm() {
        return thermal != null && (thermal.offset() > 0 || thermal.coldResistance() > 0 || thermal.thermalResistance() > 0);
    }

    public boolean isCool() {
        return thermal != null && (thermal.offset() < 0 || thermal.heatResistance() > 0 || thermal.thermalResistance() > 0);
    }

    public ClothingEntry withThermal(ThermalProtection protection) {
        return new ClothingEntry(id, item, tag, skin, stack, layer, slot, protection,
                materials, occasions, spirits, hair);
    }

    /** Whether this entry describes the stack. Skin entries never match a stack. */
    public boolean matches(@Nullable Level level, ItemStack candidate) {
        if (candidate == null || candidate.isEmpty() || skin != null) return false;
        if (item != null) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(candidate.getItem());
            if (!item.equals(key)) return false;
        } else if (tag != null) {
            if (!candidate.is(TagKey.create(Registries.ITEM, tag))) return false;
        } else {
            return false;
        }
        return stack == null || stack.test(level, candidate);
    }

    /**
     * Whether this entry describes the MCA skin. A pattern ending in {@code *} matches by prefix,
     * so one entry can cover a whole authored set; anything else is an exact id.
     */
    public boolean matchesSkin(@Nullable String skinId) {
        if (skin == null || skinId == null || skinId.isEmpty()) return false;
        if (skin.endsWith("*")) return skinId.startsWith(skin.substring(0, skin.length() - 1));
        return skin.equals(skinId);
    }

    /**
     * Reads one entry. Returns null when the entry names nothing wearable; the caller logs it. The
     * {@code ownerNamespace} and index give an entry without an {@code id} a stable identity.
     */
    public static @Nullable ClothingEntry parse(ResourceLocation owner, int index, JsonObject json) {
        if (json == null) return null;
        String itemRaw = GsonHelper.getAsString(json, "item", "").trim();
        String tagRaw = GsonHelper.getAsString(json, "tag", "").trim();
        String skinRaw = GsonHelper.getAsString(json, "skin", "").trim();
        int named = (itemRaw.isEmpty() ? 0 : 1) + (tagRaw.isEmpty() ? 0 : 1) + (skinRaw.isEmpty() ? 0 : 1);
        if (named != 1) return null;

        ResourceLocation item = itemRaw.isEmpty() ? null : DataPackLang.parseId(itemRaw);
        ResourceLocation tag = tagRaw.isEmpty() ? null
                : DataPackLang.parseId(tagRaw.startsWith("#") ? tagRaw.substring(1) : tagRaw);
        if (!itemRaw.isEmpty() && item == null) return null;
        if (!tagRaw.isEmpty() && tag == null) return null;
        String skin = skinRaw.isEmpty() ? null : skinRaw;

        ItemCondition stack = null;
        if (json.has("stack")) {
            if (skin != null) return null;
            stack = ItemConditions.parse(json.get("stack"));
        }

        String entryId = GsonHelper.getAsString(json, "id", "").trim();
        if (entryId.isEmpty()) entryId = Integer.toString(index);
        ResourceLocation id = DataPackLang.parseId(owner.getNamespace() + ":" + owner.getPath() + "/" + entryId);
        if (id == null) return null;

        ClothingLayer layer = ClothingLayer.parse(GsonHelper.getAsString(json, "layer", null));
        ClothingChannel slot = ClothingChannel.parse(GsonHelper.getAsString(json, "slot", null));
        ThermalProtection thermal = parseThermal(json.has("thermal") && json.get("thermal").isJsonObject()
                ? json.getAsJsonObject("thermal") : null);
        ClothingChoice.HairPolicy hair = json.has("hair")
                ? ClothingChoice.HairPolicy.fromString(GsonHelper.getAsString(json, "hair", "normal")) : null;

        return new ClothingEntry(id, item, tag, skin, stack, layer, slot, thermal,
                ClothingQuery.strings(json.get("material")),
                ClothingQuery.strings(json.get("occasion")),
                ClothingQuery.strings(json.get("spirit")),
                hair);
    }

    static @Nullable ThermalProtection parseThermal(@Nullable JsonObject json) {
        if (json == null) return null;
        ThermalProtection protection = new ThermalProtection(
                GsonHelper.getAsFloat(json, "offset", 0f),
                GsonHelper.getAsFloat(json, "cold_resistance", 0f),
                GsonHelper.getAsFloat(json, "heat_resistance", 0f),
                GsonHelper.getAsFloat(json, "thermal_resistance", 0f));
        return protection.equals(ThermalProtection.NONE) ? null : protection;
    }
}
