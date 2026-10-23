package com.aetherianartificer.townstead.client.gui.common;

import com.aetherianartificer.townstead.client.species.RigCamera;
import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import net.conczin.mca.client.render.DynamicSkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Draws the same root-aware head rig the world renderer uses inside a small person portrait. */
public final class PersonPortrait {
    private static final int MCA_FACE_TEXTURE_SIZE = 24;
    private static final float DEFAULT_FACE_BLOCKS = 0.5f;
    private static final Map<UUID, WeakReference<LivingEntity>> SUBJECTS = new HashMap<>();
    private static Object cachedLevel;

    private PersonPortrait() {}

    public static void drawVillager(GuiGraphics graphics, UUID uuid, int x, int y, int size) {
        LivingEntity entity = findVisibleEntity(uuid);
        if (entity != null && drawActualHead(graphics, entity, x, y, size)) return;

        ResourceLocation face = entity == null ? null : DynamicSkinCache.getOrCreateCroppedFace(entity);
        if (face != null) {
            graphics.blit(face, x, y, size, size, 0, 0,
                    MCA_FACE_TEXTURE_SIZE, MCA_FACE_TEXTURE_SIZE,
                    MCA_FACE_TEXTURE_SIZE, MCA_FACE_TEXTURE_SIZE);
            return;
        }
        drawUnavailableVillager(graphics, x, y, size);
    }

    public static void drawPlayer(GuiGraphics graphics, UUID uuid, int x, int y, int size) {
        LivingEntity entity = findVisibleEntity(uuid);
        if (entity != null && drawActualHead(graphics, entity, x, y, size)) return;

        Minecraft minecraft = Minecraft.getInstance();
        PlayerInfo info = minecraft.getConnection() == null
                ? null : minecraft.getConnection().getPlayerInfo(uuid);
        //? if >=1.21 {
        PlayerFaceRenderer.draw(graphics,
                info == null ? DefaultPlayerSkin.get(uuid) : info.getSkin(), x, y, size);
        //?} else {
        /*PlayerFaceRenderer.draw(graphics,
                info == null ? DefaultPlayerSkin.getDefaultSkin(uuid) : info.getSkinLocation(),
                x, y, size);
        *///?}
    }

    /**
     * Renders the entity itself rather than sampling a skin. The normal entity renderer therefore
     * supplies MCA hair/face layers and Townstead's active humanoid or non-humanoid root rig.
     */
    private static boolean drawActualHead(
            GuiGraphics graphics, LivingEntity entity, int x, int y, int size) {
        if (entity.isRemoved()) return false;

        float anchor = entity.getEyeHeight();
        Float rigEye = RigCamera.eyeHeight(entity);
        if (rigEye != null) anchor = rigEye;

        float faceBlocks = DEFAULT_FACE_BLOCKS;
        String rigBase = RigModels.rigBaseFor(entity);
        RigDefinition rig = RigModels.definition(rigBase);
        if (rig != null && rig.face() != null && rig.face().size().length >= 2) {
            float facePixels = Math.max(rig.face().size()[0], rig.face().size()[1]);
            if (facePixels > 0f) faceBlocks = facePixels / 16f * RigModels.scaleFor(entity);
        }
        float renderScale = Math.max(16f, Math.min(48f, size * 0.86f / faceBlocks));

        float oldBody = entity.yBodyRot;
        float oldYaw = entity.getYRot();
        float oldPitch = entity.getXRot();
        float oldHead = entity.yHeadRot;
        float oldHeadPrevious = entity.yHeadRotO;
        try {
            entity.yBodyRot = 180f;
            entity.setYRot(180f);
            entity.setXRot(0f);
            entity.yHeadRot = 180f;
            entity.yHeadRotO = 180f;

            graphics.enableScissor(x, y, x + size, y + size);
            Quaternionf modelRotation = new Quaternionf().rotateZ((float) Math.PI);
            Quaternionf cameraRotation = new Quaternionf();
            //? if >=1.21 {
            InventoryScreen.renderEntityInInventory(
                    graphics, x + size / 2f, y + size / 2f + 0.5f, renderScale,
                    new Vector3f(0f, anchor, 0f), modelRotation, cameraRotation, entity);
            //?} else {
            /*InventoryScreen.renderEntityInInventory(
                    graphics, x + size / 2,
                    Math.round(y + size / 2f + anchor * renderScale),
                    Math.round(renderScale), modelRotation, cameraRotation, entity);
            *///?}
            graphics.disableScissor();
            return true;
        } catch (RuntimeException ignored) {
            graphics.disableScissor();
            return false;
        } finally {
            entity.yBodyRot = oldBody;
            entity.setYRot(oldYaw);
            entity.setXRot(oldPitch);
            entity.yHeadRot = oldHead;
            entity.yHeadRotO = oldHeadPrevious;
        }
    }

    private static LivingEntity findVisibleEntity(UUID uuid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        if (cachedLevel != minecraft.level) {
            cachedLevel = minecraft.level;
            SUBJECTS.clear();
        }
        WeakReference<LivingEntity> reference = SUBJECTS.get(uuid);
        LivingEntity cached = reference == null ? null : reference.get();
        if (cached != null && !cached.isRemoved()) return cached;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && entity.getUUID().equals(uuid)) {
                SUBJECTS.put(uuid, new WeakReference<>(living));
                return living;
            }
        }
        return null;
    }

    /** No egg: an unloaded villager gets a quiet face placeholder until their MCA skin is available. */
    private static void drawUnavailableVillager(GuiGraphics graphics, int x, int y, int size) {
        int scale = Math.max(1, size / 8);
        int face = 0xFF9A704C;
        int shade = 0xFF5B3D29;
        graphics.fill(x + scale, y + scale, x + size - scale, y + size - scale, face);
        graphics.fill(x + scale * 2, y + scale * 3, x + scale * 3, y + scale * 4, shade);
        graphics.fill(x + size - scale * 3, y + scale * 3,
                x + size - scale * 2, y + scale * 4, shade);
        graphics.fill(x + size / 2 - scale, y + size / 2,
                x + size / 2 + scale, y + size - scale * 2, shade);
    }
}
