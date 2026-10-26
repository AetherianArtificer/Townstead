package com.aetherianartificer.townstead.client.render;

import com.aetherianartificer.townstead.villager.DrownedPersonS2CPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a drowned villager as the zombie they were: a client-only MCA zombie villager carrying the
 * Drowned's id (so Root looks keyed by id apply to it), posed from the Drowned every frame.
 */
public final class DrownedPersonRender {
    private static final EquipmentSlot[] WORN = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private record Body(DrownedPersonS2CPayload look, Level level, ZombieVillagerEntityMCA zombie) {}

    private static final Map<Integer, Body> BODIES = new HashMap<>();

    private DrownedPersonRender() {}

    /** Draws {@code mob} as its person's zombie body; false when it isn't a drowned villager. */
    static boolean render(LivingEntity mob, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (mob instanceof ZombieVillagerEntityMCA) return false;
        DrownedPersonS2CPayload look = DrownedPersonS2CPayload.of(mob.getId());
        if (look == null) return false;
        ZombieVillagerEntityMCA zombie = body(mob, look);
        if (zombie == null) return false;
        WildCostumeRender.follow(mob, zombie);
        // The name shows when the Drowned is looked at; the stand-in is never the one picked.
        zombie.setCustomNameVisible(mob.isCustomNameVisible() || Minecraft.getInstance().crosshairPickEntity == mob);
        for (EquipmentSlot slot : WORN) {
            ItemStack worn = mob.getItemBySlot(slot);
            if (!ItemStack.matches(zombie.getItemBySlot(slot), worn)) zombie.setItemSlot(slot, worn.copy());
        }
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(zombie).render(zombie,
                Mth.lerp(partial, mob.yRotO, mob.getYRot()), partial, pose, buffers, light);
        return true;
    }

    static void forget(int entityId) {
        DrownedPersonS2CPayload.forget(entityId);
        BODIES.remove(entityId);
    }

    static void clear() {
        DrownedPersonS2CPayload.clear();
        BODIES.clear();
    }

    private static @Nullable ZombieVillagerEntityMCA body(LivingEntity mob, DrownedPersonS2CPayload look) {
        Body known = BODIES.get(mob.getId());
        if (known != null && known.look() == look && known.level() == mob.level()) return known.zombie();
        ResourceLocation typeId = ResourceLocation.tryParse(look.zombieType());
        EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        Entity created = type == null ? null : type.create(mob.level());
        if (!(created instanceof ZombieVillagerEntityMCA zombie)) {
            DrownedPersonS2CPayload.forget(mob.getId());
            return null;
        }
        zombie.setId(mob.getId());
        if (look.data().hasUUID("UUID")) zombie.setUUID(look.data().getUUID("UUID"));
        zombie.readAdditionalSaveData(look.data());
        if (mob.getCustomName() != null) zombie.setCustomName(mob.getCustomName());
        BODIES.put(mob.getId(), new Body(look, mob.level(), zombie));
        return zombie;
    }
}
