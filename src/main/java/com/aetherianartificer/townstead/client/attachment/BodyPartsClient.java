package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.mixin.client.ClientLevelPartsAccessor;
import com.aetherianartificer.townstead.root.attachment.AttachmentPart;
import com.aetherianartificer.townstead.root.attachment.BodyParts;
import com.aetherianartificer.townstead.root.attachment.BodyPartsS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** A client's copy of hittable parts, so the crosshair and the attack packet can find them. */
public final class BodyPartsClient {

    private static final Map<Integer, List<AttachmentPart>> PARTS = new HashMap<>();

    private BodyPartsClient() {}

    public static void receive(BodyPartsS2CPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        remove(level, payload.entityId());
        if (payload.parts().isEmpty()) return;
        Entity entity = level.getEntity(payload.entityId());
        if (!(entity instanceof LivingEntity living)) return;
        List<AttachmentPart> parts = new ArrayList<>();
        for (BodyPartsS2CPayload.Entry entry : payload.parts()) {
            AttachmentPart part = BodyParts.clientPart(living, entry);
            ((ClientLevelPartsAccessor) level).townstead$parts().put(part.getId(), part);
            parts.add(part);
        }
        PARTS.put(payload.entityId(), parts);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (minecraft.player != null) BodyParts.setLocalPlayer(minecraft.player.getId());
        LiveBoxes.tick();
        if (level == null || PARTS.isEmpty()) return;
        Iterator<Map.Entry<Integer, List<AttachmentPart>>> entries = PARTS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, List<AttachmentPart>> entry = entries.next();
            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living && !living.isRemoved()) {
                BodyParts.place(living, entry.getValue());
                continue;
            }
            for (AttachmentPart part : entry.getValue()) {
                ((ClientLevelPartsAccessor) level).townstead$parts().remove(part.getId());
            }
            entries.remove();
        }
    }

    private static void remove(ClientLevel level, int entityId) {
        List<AttachmentPart> old = PARTS.remove(entityId);
        if (old == null) return;
        for (AttachmentPart part : old) ((ClientLevelPartsAccessor) level).townstead$parts().remove(part.getId());
    }

    public static void clear() {
        PARTS.clear();
        LiveBoxes.clear();
    }
}
