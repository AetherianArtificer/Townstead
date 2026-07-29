package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: the attachment manifest (every {@link AttachmentDef} and slot,
 * each def carrying the SHA-1 of its geometry and texture). The client diffs this
 * against its on-disk cache and requests only the blobs it lacks.
 */
//? if neoforge {
public record AttachmentManifestS2CPayload(List<AttachmentDef> defs, List<AttachmentPointDef> slots,
                                           java.util.Map<String, String> namedTextures,
                                           java.util.Map<String, String> namedGeo)
        implements CustomPacketPayload {
//?} else {
/*public record AttachmentManifestS2CPayload(java.util.List<AttachmentDef> defs, java.util.List<AttachmentPointDef> slots,
                                           java.util.Map<String, String> namedTextures,
                                           java.util.Map<String, String> namedGeo) {
*///?}

    //? if neoforge {
    public static final Type<AttachmentManifestS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "attachment_manifest_s2c"));

    public static final StreamCodec<FriendlyByteBuf, AttachmentManifestS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), AttachmentManifestS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "attachment_manifest_s2c");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "attachment_manifest_s2c");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(defs.size());
        for (AttachmentDef def : defs) {
            buf.writeUtf(def.id());
            buf.writeUtf(def.geoSha1());
            buf.writeUtf(def.textureSha1());
            buf.writeUtf(def.targetTag() == null ? "" : def.targetTag());
            buf.writeUtf(def.targetPoint() == null ? "" : def.targetPoint());
            buf.writeUtf(def.bone());
            writeVec(buf, def.offset());
            writeVec(buf, def.rotation());
            buf.writeFloat(def.scale());
            buf.writeInt(def.tint());
            buf.writeVarInt(def.tintSource());
            buf.writeVarInt(def.tintBlend());
            buf.writeFloat(def.tintStrength());
            buf.writeUtf(def.tintMaskSha1());
            buf.writeUtf(def.emissiveSha1());
            buf.writeBoolean(def.translucent());
            buf.writeVarInt(def.hidesUnder().size());
            for (String slot : def.hidesUnder()) buf.writeUtf(slot);
            buf.writeUtf(def.whenJson());
            buf.writeVarInt(def.morph().size());
            for (AttachmentDef.MorphChannel channel : def.morph()) {
                buf.writeUtf(channel.channel());
                buf.writeVarInt(channel.bones().size());
                for (String morphBone : channel.bones()) buf.writeUtf(morphBone);
                buf.writeBoolean(channel.axes() != null);
                if (channel.axes() != null) writeVec(buf, channel.axes());
                buf.writeBoolean(channel.rotate() != null);
                if (channel.rotate() != null) writeVec(buf, channel.rotate());
            }
            buf.writeVarInt(def.visibility().size());
            for (AttachmentDef.VisibilityRule rule : def.visibility()) {
                buf.writeVarInt(rule.bones().size());
                for (String ruleBone : rule.bones()) buf.writeUtf(ruleBone);
                buf.writeUtf(rule.channel());
                buf.writeFloat(rule.below());
                buf.writeFloat(rule.above());
            }
            buf.writeVarInt(def.stages().size());
            for (java.util.Map.Entry<String, AttachmentDef.StageOverride> stage : def.stages().entrySet()) {
                buf.writeUtf(stage.getKey());
                buf.writeFloat(stage.getValue().scale());
                writeVec(buf, stage.getValue().offset());
                buf.writeUtf(stage.getValue().geoSha1() == null ? "" : stage.getValue().geoSha1());
            }
            buf.writeVarInt(def.poses().size());
            for (AttachmentDef.PoseEntry pose : def.poses()) {
                buf.writeUtf(pose.state());
                buf.writeUtf(pose.conditionJson());
                buf.writeBoolean(pose.rotation() != null);
                if (pose.rotation() != null) writeVec(buf, pose.rotation());
                buf.writeVarInt(pose.boneRotations().size());
                for (java.util.Map.Entry<String, float[]> bone : pose.boneRotations().entrySet()) {
                    buf.writeUtf(bone.getKey());
                    writeVec(buf, bone.getValue());
                }
                buf.writeFloat(pose.transitionTicks());
            }
            buf.writeVarInt(def.physics().size());
            for (AttachmentDef.PhysicsChain chain : def.physics()) {
                buf.writeVarInt(chain.bones().size());
                for (String chainBone : chain.bones()) buf.writeUtf(chainBone);
                buf.writeFloat(chain.stiffness());
                buf.writeFloat(chain.damping());
                buf.writeFloat(chain.gravity());
                buf.writeFloat(chain.maxAngle());
                buf.writeFloat(chain.sway());
                buf.writeFloat(chain.follow());
                buf.writeFloat(chain.droopAngle());
                buf.writeFloat(chain.swaySpeed());
                buf.writeFloat(chain.snap());
                for (int r = 0; r < 4; r++) buf.writeFloat(chain.response()[r]);
                buf.writeVarInt(chain.segments());
                buf.writeUtf(chain.axis());
            }
            buf.writeVarInt(def.animations().size());
            for (AttachmentDef.AnimationEntry anim : def.animations()) {
                buf.writeUtf(anim.state());
                buf.writeUtf(anim.conditionJson());
                buf.writeUtf(anim.animSha());
                buf.writeUtf(anim.clip());
                buf.writeFloat(anim.transitionTicks());
                buf.writeFloat(anim.weight());
                buf.writeVarInt(anim.cooldownTicks());
                buf.writeBoolean(anim.idle());
            }
        }
        buf.writeVarInt(slots.size());
        for (AttachmentPointDef point : slots) {
            buf.writeUtf(point.id());
            buf.writeUtf(point.bone());
            writeVec(buf, point.offset());
            buf.writeVarInt(point.tags().size());
            for (String tag : point.tags()) buf.writeUtf(tag);
            writeVec(buf, point.rotation());
            buf.writeBoolean(point.mirror());
            buf.writeUtf(point.rig());
        }
        buf.writeVarInt(namedTextures.size());
        for (java.util.Map.Entry<String, String> e : namedTextures.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeUtf(e.getValue());
        }
        buf.writeVarInt(namedGeo.size());
        for (java.util.Map.Entry<String, String> e : namedGeo.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeUtf(e.getValue());
        }
    }

    public static AttachmentManifestS2CPayload read(FriendlyByteBuf buf) {
        int defCount = buf.readVarInt();
        List<AttachmentDef> defs = new ArrayList<>(defCount);
        for (int i = 0; i < defCount; i++) {
            String id = buf.readUtf();
            String geo = buf.readUtf();
            String tex = buf.readUtf();
            String targetTag = buf.readUtf();
            String targetPoint = buf.readUtf();
            String bone = buf.readUtf();
            float[] offset = readVec(buf);
            float[] rotation = readVec(buf);
            float scale = buf.readFloat();
            int tint = buf.readInt();
            int tintSource = buf.readVarInt();
            int tintBlend = buf.readVarInt();
            float tintStrength = buf.readFloat();
            String tintMaskSha = buf.readUtf();
            String emissiveSha = buf.readUtf();
            boolean translucent = buf.readBoolean();
            int hideCount = buf.readVarInt();
            List<String> hidesUnder = new ArrayList<>(hideCount);
            for (int h = 0; h < hideCount; h++) hidesUnder.add(buf.readUtf());
            String whenJson = buf.readUtf();
            int morphCount = buf.readVarInt();
            List<AttachmentDef.MorphChannel> morph = new ArrayList<>(morphCount);
            for (int c = 0; c < morphCount; c++) {
                String channelName = buf.readUtf();
                int morphBoneCount = buf.readVarInt();
                List<String> morphBones = new ArrayList<>(morphBoneCount);
                for (int b = 0; b < morphBoneCount; b++) morphBones.add(buf.readUtf());
                float[] axes = buf.readBoolean() ? readVec(buf) : null;
                float[] rotate = buf.readBoolean() ? readVec(buf) : null;
                morph.add(new AttachmentDef.MorphChannel(channelName, morphBones, axes, rotate));
            }
            int visibilityCount = buf.readVarInt();
            List<AttachmentDef.VisibilityRule> visibility = new ArrayList<>(visibilityCount);
            for (int v = 0; v < visibilityCount; v++) {
                int ruleBoneCount = buf.readVarInt();
                List<String> ruleBones = new ArrayList<>(ruleBoneCount);
                for (int b = 0; b < ruleBoneCount; b++) ruleBones.add(buf.readUtf());
                String ruleChannel = buf.readUtf();
                float below = buf.readFloat();
                float above = buf.readFloat();
                visibility.add(new AttachmentDef.VisibilityRule(ruleBones, ruleChannel, below, above));
            }
            int stageCount = buf.readVarInt();
            java.util.Map<String, AttachmentDef.StageOverride> stages = new java.util.LinkedHashMap<>();
            for (int s = 0; s < stageCount; s++) {
                String key = buf.readUtf();
                float stageScale = buf.readFloat();
                float[] stageOffset = readVec(buf);
                String stageGeo = buf.readUtf();
                stages.put(key, new AttachmentDef.StageOverride(stageScale, stageOffset,
                        stageGeo.isEmpty() ? null : stageGeo));
            }
            int poseCount = buf.readVarInt();
            List<AttachmentDef.PoseEntry> poses = new ArrayList<>(poseCount);
            for (int p = 0; p < poseCount; p++) {
                String state = buf.readUtf();
                String conditionJson = buf.readUtf();
                float[] poseRotation = buf.readBoolean() ? readVec(buf) : null;
                int boneCount = buf.readVarInt();
                java.util.Map<String, float[]> boneRotations = new java.util.LinkedHashMap<>();
                for (int b = 0; b < boneCount; b++) {
                    String boneName = buf.readUtf();
                    boneRotations.put(boneName, readVec(buf));
                }
                poses.add(new AttachmentDef.PoseEntry(state, conditionJson, poseRotation,
                        boneRotations, buf.readFloat()));
            }
            int chainCount = buf.readVarInt();
            List<AttachmentDef.PhysicsChain> physics = new ArrayList<>(chainCount);
            for (int c = 0; c < chainCount; c++) {
                int chainBoneCount = buf.readVarInt();
                List<String> chainBones = new ArrayList<>(chainBoneCount);
                for (int b = 0; b < chainBoneCount; b++) chainBones.add(buf.readUtf());
                float stiffness = buf.readFloat();
                float damping = buf.readFloat();
                float gravity = buf.readFloat();
                float maxAngle = buf.readFloat();
                float sway = buf.readFloat();
                float follow = buf.readFloat();
                float droopAngle = buf.readFloat();
                float swaySpeed = buf.readFloat();
                float snap = buf.readFloat();
                float[] response = {buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()};
                int segments = buf.readVarInt();
                String axis = buf.readUtf();
                physics.add(new AttachmentDef.PhysicsChain(chainBones, stiffness, damping, gravity,
                        maxAngle, sway, follow, droopAngle, swaySpeed, snap, response, segments, axis));
            }
            int animCount = buf.readVarInt();
            List<AttachmentDef.AnimationEntry> animations = new ArrayList<>(animCount);
            for (int a = 0; a < animCount; a++) {
                String animState = buf.readUtf();
                String animCondition = buf.readUtf();
                String animSha = buf.readUtf();
                String clip = buf.readUtf();
                float transition = buf.readFloat();
                float weight = buf.readFloat();
                int cooldown = buf.readVarInt();
                boolean idle = buf.readBoolean();
                animations.add(new AttachmentDef.AnimationEntry(animState, animCondition, animSha, clip,
                        transition, weight, cooldown, idle));
            }
            defs.add(new AttachmentDef(id, geo, tex, targetTag.isEmpty() ? null : targetTag,
                    targetPoint.isEmpty() ? null : targetPoint, bone, offset, rotation, scale, tint,
                    tintSource, tintBlend, tintStrength, tintMaskSha, emissiveSha, translucent, hidesUnder, whenJson,
                    morph, visibility, stages, poses, physics, animations));
        }
        int slotCount = buf.readVarInt();
        List<AttachmentPointDef> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            String id = buf.readUtf();
            String bone = buf.readUtf();
            float[] offset = readVec(buf);
            int tagCount = buf.readVarInt();
            List<String> tags = new ArrayList<>(tagCount);
            for (int t = 0; t < tagCount; t++) tags.add(buf.readUtf());
            float[] rotation = readVec(buf);
            boolean mirror = buf.readBoolean();
            String rig = buf.readUtf();
            slots.add(new AttachmentPointDef(id, bone, offset, tags, rotation, mirror, rig));
        }
        int texCount = buf.readVarInt();
        java.util.Map<String, String> namedTextures = new java.util.LinkedHashMap<>();
        for (int i = 0; i < texCount; i++) {
            String key = buf.readUtf();
            namedTextures.put(key, buf.readUtf());
        }
        int geoCount = buf.readVarInt();
        java.util.Map<String, String> namedGeo = new java.util.LinkedHashMap<>();
        for (int i = 0; i < geoCount; i++) {
            String key = buf.readUtf();
            namedGeo.put(key, buf.readUtf());
        }
        return new AttachmentManifestS2CPayload(defs, slots, namedTextures, namedGeo);
    }

    private static void writeVec(FriendlyByteBuf buf, float[] v) {
        buf.writeFloat(v[0]);
        buf.writeFloat(v[1]);
        buf.writeFloat(v[2]);
    }

    private static float[] readVec(FriendlyByteBuf buf) {
        return new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat()};
    }
}
