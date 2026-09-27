package com.aetherianartificer.townstead.client.livery;

import com.aetherianartificer.townstead.livery.LiveryView;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Draws livery without touching the worn item: the armour layer is handed a display copy carrying
 * the style's trims and dye, and each armour texture is swapped for the style's own art when a pack
 * provides it. Art lives at {@code <ns>:textures/livery/<style>/<material>_layer_<n>.png}, with
 * {@code any_layer_<n>.png} for materials that have none of their own. A culture data pack ships it
 * under {@code data/<ns>/textures/livery/...}, synced from the server; a resource pack may ship it
 * under {@code assets/} instead.
 */
public final class LiveryRender {
    private static final Pattern ARMOUR = Pattern.compile("(?:.*/)?([a-z0-9_.-]+)_layer_([12])(_[a-z0-9_]+)?\\.png");
    /** A texture a style has no art for is looked up again after this long, in case a pack was added. */
    private static final long RECHECK_MS = 10_000L;
    //? if >=1.21 {
    private static final ResourceLocation BLANK = ResourceLocation.fromNamespaceAndPath("townstead", "textures/livery/blank.png");
    //?} else {
    /*private static final ResourceLocation BLANK = new ResourceLocation("townstead", "textures/livery/blank.png");
    *///?}

    private record Display(ItemStack original, LiveryView view, ItemStack copy) {}

    private static final Map<Long, Display> DISPLAYS = new HashMap<>();
    private static final Map<ResourceLocation, Long> MISSING = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> PRESENT = new HashMap<>();
    /** The tint for the texture just swapped, read by the draw that follows it. */
    private static final ThreadLocal<Integer> PENDING_TINT = ThreadLocal.withInitial(() -> 0);

    private LiveryRender() {}

    static void forget(int entity) {
        DISPLAYS.keySet().removeIf(key -> (int) (key >> 3) == entity);
    }

    static void forgetAll() {
        DISPLAYS.clear();
    }

    /** What the armour layer draws for this slot: the worn stack, or a copy dressed in the livery. */
    public static ItemStack displayStack(Entity entity, EquipmentSlot slot, ItemStack worn) {
        if (worn.isEmpty() || !(worn.getItem() instanceof ArmorItem)) return worn;
        LiveryView view = LiveryClientStore.of(entity.getId());
        if (view == null) return worn;
        long key = ((long) entity.getId() << 3) | slot.ordinal();
        Display cached = DISPLAYS.get(key);
        if (cached != null && cached.original() == worn && cached.view() == view) return cached.copy();
        ItemStack copy = dress(worn.copy(), slot, view);
        DISPLAYS.put(key, new Display(worn, view, copy));
        return copy;
    }

    private static ItemStack dress(ItemStack copy, EquipmentSlot slot, LiveryView view) {
        var level = Minecraft.getInstance().level;
        if (level == null) return copy;
        LiveryView.Trim trim = view.trims().get(slotName(slot));
        if (trim != null) {
            ResourceLocation patternId = ResourceLocation.tryParse(trim.pattern());
            ResourceLocation materialId = ResourceLocation.tryParse(trim.material());
            var access = level.registryAccess();
            var pattern = patternId == null ? null : access.registryOrThrow(Registries.TRIM_PATTERN)
                    .getHolder(ResourceKey.create(Registries.TRIM_PATTERN, patternId)).orElse(null);
            var material = materialId == null ? null : access.registryOrThrow(Registries.TRIM_MATERIAL)
                    .getHolder(ResourceKey.create(Registries.TRIM_MATERIAL, materialId)).orElse(null);
            if (pattern != null && material != null) {
                //? if >=1.21 {
                copy.set(net.minecraft.core.component.DataComponents.TRIM, new ArmorTrim(material, pattern));
                //?} else {
                /*ArmorTrim.setTrim(access, copy, new ArmorTrim(material, pattern));
                *///?}
            }
        }
        //? if >=1.21 {
        if (copy.is(net.minecraft.tags.ItemTags.DYEABLE)) {
            copy.set(net.minecraft.core.component.DataComponents.DYED_COLOR,
                    new net.minecraft.world.item.component.DyedItemColor(view.primary(), false));
        }
        //?} else {
        /*if (copy.getItem() instanceof net.minecraft.world.item.DyeableLeatherItem dyeable) dyeable.setColor(copy, view.primary());
        *///?}
        return copy;
    }

    private static String slotName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "head";
            case CHEST -> "chest";
            case LEGS -> "legs";
            default -> "feet";
        };
    }

    /**
     * The texture to draw for one armour layer. With the style's art present it replaces the
     * material's, and the next draw is tinted with the primary colour if the style asks. A layer the
     * style replaces but has no matching extra (a leather overlay, say) is drawn blank.
     */
    public static ResourceLocation texture(Entity entity, ResourceLocation original) {
        PENDING_TINT.set(0);
        LiveryView view = LiveryClientStore.of(entity.getId());
        if (view == null) return original;
        Matcher match = ARMOUR.matcher(original.getPath());
        if (!match.matches()) return original;
        String material = match.group(1), layer = match.group(2), extra = match.group(3) == null ? "" : match.group(3);
        ResourceLocation base = art(view.style(), material + "_layer_" + layer);
        if (base == null) base = art(view.style(), "any_layer_" + layer);
        if (base == null) return original;
        if (!extra.isEmpty()) {
            ResourceLocation own = art(view.style(), material + "_layer_" + layer + extra);
            return own != null ? own : BLANK;
        }
        if (view.tint()) PENDING_TINT.set(0xFF000000 | view.primary());
        return base;
    }

    /** The tint for the draw that follows {@link #texture}; {@code fallback} when it has none. */
    public static int tint(int fallback) {
        int tint = PENDING_TINT.get();
        PENDING_TINT.set(0);
        return tint == 0 ? fallback : tint;
    }

    private static @Nullable ResourceLocation art(ResourceLocation style, String file) {
        //? if >=1.21 {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(style.getNamespace(), "textures/livery/" + style.getPath() + "/" + file + ".png");
        //?} else {
        /*ResourceLocation id = new ResourceLocation(style.getNamespace(), "textures/livery/" + style.getPath() + "/" + file + ".png");
        *///?}
        // Art synced from the server's data packs comes first: that is how a culture pack ships it.
        ResourceLocation synced = com.aetherianartificer.townstead.client.attachment.AttachmentClient.namedTexture(id.toString());
        if (synced != null) return synced;
        if (PRESENT.containsKey(id)) return id;
        Long checked = MISSING.get(id);
        long now = Util.getMillis();
        if (checked != null && now - checked < RECHECK_MS) return null;
        if (Minecraft.getInstance().getResourceManager().getResource(id).isPresent()) {
            PRESENT.put(id, true);
            MISSING.remove(id);
            return id;
        }
        MISSING.put(id, now);
        return null;
    }
}
