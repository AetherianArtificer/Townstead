package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.root.attachment.AttachmentAnimation;
import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.aetherianartificer.townstead.root.attachment.AttachmentPointDef;
import com.aetherianartificer.townstead.root.attachment.BodyPose;
import com.aetherianartificer.townstead.root.gene.AllelePayload;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Where a client keeps what {@link BodyPose} reads. */
public final class BodyPoseClient implements BodyPose.ClientSide {

    @Override
    public JsonObject geometry(String sha) {
        return AttachmentClient.geometryJson(sha);
    }

    @Override
    public JsonObject rigGeometry(String ref) {
        return com.aetherianartificer.townstead.client.species.RigAssets.geometry(ref);
    }

    @Override
    public java.util.Map<String, AttachmentAnimation.Clip> rigAnimations(String ref) {
        return com.aetherianartificer.townstead.client.species.RigAssets.animations(ref);
    }

    @Override
    public List<AttachmentPointDef> points() {
        return AttachmentClient.allPoints();
    }

    @Override
    public String rigId(LivingEntity entity) {
        return RigModels.rigBaseFor(entity);
    }

    @Override
    public RigDefinition rig(String id) {
        return RootCatalogClient.rig(id);
    }

    @Override
    public AllelePayload rolled(LivingEntity entity, AttachmentDef def) {
        return AttachmentRenderLayer.rolled(entity, def);
    }

    @Override
    public String[] stage(LivingEntity entity) {
        return AttachmentRenderLayer.stageNames(entity);
    }

    @Override
    public AttachmentAnimation.Clip clip(String sha, String name) {
        return AttachmentClient.animationClip(sha, name);
    }
}
