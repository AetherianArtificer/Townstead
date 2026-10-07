package com.aetherianartificer.townstead.client.gui.wardrobe;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Draws a villager wearing a given skin. The real villager is used when the client has them
 * loaded, so hair, face and a root's body are theirs; the skin is swapped and their armour and
 * held items are set aside only for the draw.
 * Otherwise a plain MCA villager of their gender stands in.
 */
final class WardrobePreview {

    private static final Map<Gender, LivingEntity> STAND_INS = new EnumMap<>(Gender.class);
    private static Object standInLevel;

    private WardrobePreview() {}

    static @Nullable LivingEntity subject(UUID uuid, int genderOrdinal) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && entity instanceof VillagerLike<?> && uuid.equals(entity.getUUID())) {
                return living;
            }
        }
        if (standInLevel != minecraft.level) {
            standInLevel = minecraft.level;
            STAND_INS.clear();
        }
        Gender[] genders = Gender.values();
        Gender gender = genderOrdinal >= 0 && genderOrdinal < genders.length ? genders[genderOrdinal] : Gender.MALE;
        if (gender != Gender.FEMALE) gender = Gender.MALE;
        Gender key = gender;
        return STAND_INS.computeIfAbsent(key, g -> g.getVillagerType().create(minecraft.level));
    }

    /** Head and chest only, for a grid cell. */
    static void bust(GuiGraphics g, @Nullable LivingEntity entity, String skin, int x0, int y0, int x1, int y1) {
        if (entity == null) return;
        int scale = Math.max(8, Math.round((y1 - y0) / 0.8f));
        draw(g, entity, skin, x0, y0, x1, y1, scale, entity.getBbHeight() * 0.32f);
    }

    private static void draw(GuiGraphics g, LivingEntity entity, String skin, int x0, int y0, int x1, int y1,
                             int scale, float lift) {
        VillagerLike<?> villager = entity instanceof VillagerLike<?> v ? v : null;
        String worn = villager == null ? null : villager.getClothes();
        EquipmentSlot[] slots = EquipmentSlot.values();
        ItemStack[] held = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            held[i] = entity.getItemBySlot(slots[i]);
            if (!held[i].isEmpty()) entity.setItemSlot(slots[i], ItemStack.EMPTY);
        }
        try {
            if (villager != null && skin != null && !skin.isEmpty()) villager.setClothes(skin);
            float cx = (x0 + x1) / 2f;
            float cy = (y0 + y1) / 2f;
            //? if >=1.21 {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, scale, lift, cx, cy, entity);
            //?} else {
            /*g.enableScissor(x0, y0, x1, y1);
            int feet = Math.round(cy + (entity.getBbHeight() / 2f + lift) * scale);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, Math.round(cx), feet, scale, 0f, 0f, entity);
            g.disableScissor();
            *///?}
        } catch (RuntimeException ignored) {
        } finally {
            if (villager != null && worn != null) villager.setClothes(worn);
            for (int i = 0; i < slots.length; i++) {
                if (!held[i].isEmpty()) entity.setItemSlot(slots[i], held[i]);
            }
        }
    }

    /** A readable name for a skin id, from its file path. */
    static String label(String skin) {
        if (skin == null || skin.isEmpty()) return "";
        String path = skin.contains(":") ? skin.substring(skin.indexOf(':') + 1) : skin;
        if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);
        String[] parts = path.split("/");
        String last = parts[parts.length - 1];
        String text = last.chars().allMatch(Character::isDigit) && parts.length >= 2
                ? parts[parts.length - 2] + " " + last : last;
        text = text.replace('_', ' ').trim();
        return text.isEmpty() ? skin : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
