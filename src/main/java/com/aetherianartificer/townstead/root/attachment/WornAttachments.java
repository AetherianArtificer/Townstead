package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The attachment definitions an entity has on, on either side. A server reads its powers and its own
 * loaded definitions; a client reads what it was sent. Only people can wear them, so this stays cheap
 * enough for the movement path.
 */
public final class WornAttachments {

    private static Function<LivingEntity, List<AttachmentDef>> client = entity -> List.of();

    private WornAttachments() {}

    /** Set once by the client's setup: the render layer already resolves this, per individual. */
    public static void setClientSource(Function<LivingEntity, List<AttachmentDef>> source) {
        client = source;
    }

    /** What the entity has on now, a wounded or lost part showing as whatever replaced it. */
    public static List<AttachmentDef> of(LivingEntity entity) {
        if (entity.level().isClientSide) return client.apply(entity);
        List<AttachmentDef> out = new ArrayList<>();
        for (AttachmentDef def : declared(entity)) {
            AttachmentDef shown = BodyParts.shown(entity, def, AttachmentServerData::definition);
            if (shown != null) out.add(shown);
        }
        return out;
    }

    /** What the entity's genes grant, whatever state those parts are in. Server only. */
    public static List<AttachmentDef> declared(LivingEntity entity) {
        List<AttachmentDef> out = new ArrayList<>();
        for (Power power : Powers.active(entity)) {
            if (!(power.component() instanceof AttachmentGeneType.Instance instance)) continue;
            for (String id : instance.attachments()) {
                AttachmentDef def = AttachmentServerData.definition(id);
                if (def != null) out.add(def);
            }
        }
        return out;
    }
}
