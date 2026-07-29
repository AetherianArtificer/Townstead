package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.root.gene.Gene;
import com.aetherianartificer.townstead.root.gene.GeneRegistry;
import com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.GsonHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Attachment health checks, surfaced through the loader log and the
 * {@code /townstead attachment doctor} command (deliberately NOT {@code /pheno} —
 * attachments are their own authoring surface).
 *
 * <p>{@link #selfChecks()} needs only the attachment data (safe at reload time):
 * morph bones that don't exist in the geometry, texture PNG dimensions that
 * disagree with the geo's declared texture size, targets that resolve to nothing.
 * {@link #crossChecks()} joins against the gene registry (run on demand, when
 * everything is loaded): genes pointing at missing attachments, sized genes whose
 * attachment declares no morph, and morphing attachments no sized gene grants.</p>
 */
public final class AttachmentDoctor {

    private AttachmentDoctor() {}

    public static List<String> selfChecks() {
        List<String> out = new ArrayList<>();
        Set<String> allTags = new HashSet<>();
        Set<String> allPoints = new HashSet<>();
        for (AttachmentPointDef slot : AttachmentServerData.slots()) {
            allPoints.add(slot.id());
            allTags.addAll(slot.tags());
        }
        for (AttachmentDef def : AttachmentServerData.definitions()) {
            GeoInfo geo = geoInfo(def.geoSha1());
            // Morphs, visibility, poses, physics, and animations may all address bones
            // that only exist in a stage's swapped geometry (an adult's extra tails),
            // so bone checks validate against the union of base + stage geometry.
            Set<String> stageBones = geo == null ? null : new LinkedHashSet<>(geo.bones);
            if (stageBones != null) {
                for (AttachmentDef.StageOverride stage : def.stages().values()) {
                    if (stage.geoSha1() == null) continue;
                    GeoInfo stageGeo = geoInfo(stage.geoSha1());
                    if (stageGeo != null) stageBones.addAll(stageGeo.bones);
                }
            }
            if (geo == null) {
                out.add(def.id() + ": geometry blob missing or unparseable");
            } else {
                for (AttachmentDef.MorphChannel channel : def.morph()) {
                    for (String bone : channel.bones()) {
                        if (!stageBones.contains(bone)) {
                            out.add(def.id() + ": morph bone '" + bone + "' not in geometry (has: "
                                    + String.join(", ", stageBones) + ")");
                        }
                    }
                    if (channel.rotate() != null && channel.bones().isEmpty()) {
                        out.add(def.id() + ": morph channel '" + channel.channel()
                                + "' rotates but names no bones");
                    }
                }
                for (AttachmentDef.VisibilityRule rule : def.visibility()) {
                    for (String bone : rule.bones()) {
                        if (!stageBones.contains(bone)) {
                            out.add(def.id() + ": visibility bone '" + bone + "' not in geometry (has: "
                                    + String.join(", ", stageBones) + ")");
                        }
                    }
                    if (Float.isNaN(rule.below()) && Float.isNaN(rule.above())) {
                        out.add(def.id() + ": visibility rule on channel '" + rule.channel()
                                + "' declares neither below nor above");
                    }
                }
                int[] png = pngSize(def.textureSha1());
                if (png != null && geo.texWidth > 0 && geo.texHeight > 0
                        && (png[0] != geo.texWidth || png[1] != geo.texHeight)) {
                    out.add(def.id() + ": texture is " + png[0] + "x" + png[1]
                            + " but geometry declares texture_width/height " + geo.texWidth + "x" + geo.texHeight
                            + " (UVs will be scaled)");
                }
                // A mask is sampled pixel-for-pixel against the base texture, so a size
                // mismatch is ignored at bake time rather than sampled wrong — which reads
                // as "the mask does nothing" unless we say why.
                int[] maskPng = def.tintMaskSha1().isEmpty() ? null : pngSize(def.tintMaskSha1());
                if (maskPng != null && png != null && (maskPng[0] != png[0] || maskPng[1] != png[1])) {
                    out.add(def.id() + ": tint_mask is " + maskPng[0] + "x" + maskPng[1]
                            + " but the texture is " + png[0] + "x" + png[1]
                            + " (the mask is ignored; sizes must match)");
                }
            }
            if (!def.tintMaskSha1().isEmpty() && def.tintSource() == AttachmentDef.TINT_FLAT
                    && def.tint() == 0xFFFFFF) {
                out.add(def.id() + ": tint_mask is set but the tint is white with no source, "
                        + "so nothing is tinted (set tint to a colour, or skin/hair/eyes/gene)");
            }
            for (String slot : def.hidesUnder()) {
                if (!AttachmentDef.EQUIPMENT_SLOTS.contains(slot)) {
                    out.add(def.id() + ": hides_under slot '" + slot + "' is not a known armor slot (known: "
                            + String.join(", ", AttachmentDef.EQUIPMENT_SLOTS) + ")");
                }
            }
            if (!def.whenJson().isEmpty()) {
                boolean parses;
                try {
                    parses = com.aetherianartificer.townstead.pheno.condition.Conditions
                            .parse(JsonParser.parseString(def.whenJson())) != null;
                } catch (Exception e) {
                    parses = false;
                }
                if (!parses) {
                    out.add(def.id() + ": when condition does not parse (the attachment renders "
                            + "unconditionally): " + def.whenJson());
                }
            }
            if (def.targetTag() != null && !allTags.contains(def.targetTag())) {
                out.add(def.id() + ": target tag '" + def.targetTag() + "' matches no attachment point");
            }
            if (def.targetPoint() != null && !allPoints.contains(def.targetPoint())) {
                out.add(def.id() + ": target point '" + def.targetPoint() + "' does not exist");
            }
            checkPoses(def, stageBones, out);
            checkPhysics(def, stageBones, out);
            checkAnimations(def, stageBones, out);
        }
        return out;
    }

    /**
     * Animation sanity: trigger states known, conditions parse, the referenced clip
     * exists in its file, clip bones exist in the base+stage geometry union, and the
     * parser's own warnings (Molang values, empty tracks) are surfaced.
     */
    private static void checkAnimations(AttachmentDef def, Set<String> knownBones, List<String> out) {
        java.util.Map<String, java.util.Map<String, AttachmentAnimation.Clip>> parsed = new java.util.HashMap<>();
        for (AttachmentDef.AnimationEntry entry : def.animations()) {
            if (!entry.idle() && !entry.state().isEmpty()
                    && !AttachmentDef.POSE_STATES.contains(entry.state())) {
                out.add(def.id() + ": animation state '" + entry.state() + "' is not a known state (known: "
                        + String.join(", ", AttachmentDef.POSE_STATES) + ")");
            }
            if (!entry.conditionJson().isEmpty()) {
                boolean parses;
                try {
                    parses = com.aetherianartificer.townstead.pheno.condition.Conditions
                            .parse(JsonParser.parseString(entry.conditionJson())) != null;
                } catch (Exception e) {
                    parses = false;
                }
                if (!parses) out.add(def.id() + ": animation condition does not parse: " + entry.conditionJson());
            }
            var clips = parsed.computeIfAbsent(entry.animSha(), sha -> {
                AttachmentServerData.Blob blob = AttachmentServerData.blob(sha);
                if (blob == null) return java.util.Map.of();
                try {
                    List<String> warnings = new ArrayList<>();
                    var result = AttachmentAnimation.parse(JsonParser.parseString(
                            new String(blob.bytes(), StandardCharsets.UTF_8)).getAsJsonObject(), warnings);
                    for (String warning : warnings) out.add(def.id() + ": animation " + warning);
                    return result;
                } catch (Exception e) {
                    return java.util.Map.of();
                }
            });
            AttachmentAnimation.Clip clip = resolveClip(clips, entry.clip());
            if (clip == null) {
                out.add(def.id() + ": animation clip '" + (entry.clip().isEmpty() ? "(first)" : entry.clip())
                        + "' not found in its file (has: " + String.join(", ", clips.keySet()) + ")");
                continue;
            }
            if (knownBones != null) {
                for (String bone : clip.bones.keySet()) {
                    if (!knownBones.contains(bone)) {
                        out.add(def.id() + ": animation clip '" + clip.name + "' bone '" + bone
                                + "' not in geometry (has: " + String.join(", ", knownBones) + ")");
                    }
                }
            }
        }
    }

    /** The same clip resolution the client uses: exact name, empty = first, or Blockbench suffix. */
    private static AttachmentAnimation.Clip resolveClip(
            java.util.Map<String, AttachmentAnimation.Clip> clips, String name) {
        if (clips.isEmpty()) return null;
        if (name.isEmpty()) return clips.values().iterator().next();
        AttachmentAnimation.Clip exact = clips.get(name);
        if (exact != null) return exact;
        for (var entry : clips.entrySet()) {
            if (entry.getKey().endsWith("." + name)) return entry.getValue();
        }
        return null;
    }

    /** Physics sanity: chain bones must exist; 0..1 parameters warned; segments constraints. */
    private static void checkPhysics(AttachmentDef def, Set<String> knownBones, List<String> out) {
        for (AttachmentDef.PhysicsChain chain : def.physics()) {
            if (knownBones != null) {
                for (String bone : chain.bones()) {
                    if (!knownBones.contains(bone)) {
                        out.add(def.id() + ": physics chain bone '" + bone + "' not in geometry (has: "
                                + String.join(", ", knownBones) + ")");
                    }
                }
            }
            if (chain.stiffness() < 0f || chain.stiffness() > 1f) {
                out.add(def.id() + ": physics stiffness " + chain.stiffness() + " outside 0..1");
            }
            if (chain.damping() < 0f || chain.damping() > 1f) {
                out.add(def.id() + ": physics damping " + chain.damping() + " outside 0..1");
            }
            if (chain.gravity() < 0f || chain.gravity() > 1f) {
                out.add(def.id() + ": physics gravity " + chain.gravity() + " outside 0..1");
            }
            if (chain.follow() < 0f || chain.follow() > 1f) {
                out.add(def.id() + ": physics follow " + chain.follow() + " outside 0..1");
            }
            if (chain.snap() < 0f || chain.snap() > 4f) {
                out.add(def.id() + ": physics snap " + chain.snap() + " outside 0..4");
            }
            if (chain.segments() != 0) {
                if (chain.segments() < 2 || chain.segments() > 8) {
                    out.add(def.id() + ": physics segments " + chain.segments() + " outside 2..8");
                }
                if (chain.bones().size() != 1) {
                    out.add(def.id() + ": physics segments needs exactly one listed bone (the one to slice), got "
                            + chain.bones().size());
                }
                String axis = chain.axis().toLowerCase(java.util.Locale.ROOT);
                if (!axis.equals("auto") && !axis.equals("x") && !axis.equals("y") && !axis.equals("z")) {
                    out.add(def.id() + ": physics axis '" + chain.axis() + "' is not auto/x/y/z");
                }
            }
        }
    }

    /** Pose sanity: state keys must be known, conditions must parse, bones must exist. */
    private static void checkPoses(AttachmentDef def, Set<String> knownBones, List<String> out) {
        for (AttachmentDef.PoseEntry pose : def.poses()) {
            if (!pose.state().isEmpty() && !AttachmentDef.POSE_STATES.contains(pose.state())) {
                out.add(def.id() + ": pose state '" + pose.state() + "' is not a known state (known: "
                        + String.join(", ", AttachmentDef.POSE_STATES) + ")");
            }
            if (!pose.conditionJson().isEmpty()) {
                boolean parses;
                try {
                    parses = com.aetherianartificer.townstead.pheno.condition.Conditions
                            .parse(JsonParser.parseString(pose.conditionJson())) != null;
                } catch (Exception e) {
                    parses = false;
                }
                if (!parses) {
                    out.add(def.id() + ": pose condition " + pose.conditionJson()
                            + " is malformed or its type is unknown");
                }
            }
            if (knownBones != null) {
                for (String bone : pose.boneRotations().keySet()) {
                    if (!knownBones.contains(bone)) {
                        out.add(def.id() + ": pose bone '" + bone + "' not in geometry (has: "
                                + String.join(", ", knownBones) + ")");
                    }
                }
            }
        }
    }

    public static List<String> crossChecks() {
        List<String> out = new ArrayList<>();
        Set<String> defIds = new HashSet<>();
        for (AttachmentDef def : AttachmentServerData.definitions()) defIds.add(def.id());

        // Every variant of every gene can wear its own attachment (a style-swap gene),
        // and a composite grant wears a whole set — so the grant map covers every id
        // each variant instance lists, with each option's channel names and whether
        // the option rolls a heritable tint.
        Set<String> grantedWithSize = new HashSet<>();
        Set<String> grantedAtAll = new HashSet<>();
        Set<String> grantedWithTint = new HashSet<>();
        java.util.Map<String, Set<String>> grantedChannels = new java.util.HashMap<>();
        for (Gene gene : GeneRegistry.all()) {
            for (com.aetherianartificer.townstead.root.gene.GeneVariant variant : gene.variants()) {
                if (!(variant.instance() instanceof AttachmentGeneType.Instance att)) continue;
                for (String attachmentId : att.attachments()) {
                    grantedAtAll.add(attachmentId);
                    if (att.sized()) {
                        grantedWithSize.add(attachmentId);
                        Set<String> names = grantedChannels.computeIfAbsent(attachmentId, k -> new HashSet<>());
                        for (AttachmentGeneType.Channel channel : att.channels()) names.add(channel.name());
                    }
                    if (att.tinted()) grantedWithTint.add(attachmentId);
                    if (!defIds.contains(attachmentId)) {
                        out.add("gene " + gene.id() + ": attachment '" + attachmentId + "' does not exist");
                    }
                }
                if (att.tinted() && att.palette().isEmpty()) {
                    out.add("gene " + gene.id() + ": tint block declares no palette; newborn colours fall back to white");
                }
                // The canonical payload encoding can't mix the anonymous channel with named
                // ones, so a tint block silently drops an anonymous size roll on every save.
                if (att.tinted() && att.channel("") != null) {
                    out.add("gene " + gene.id() + ": tint block combined with the anonymous size shorthand; "
                            + "move the size roll into size.channels with a name or it cannot persist");
                }
            }
        }
        for (AttachmentDef def : AttachmentServerData.definitions()) {
            boolean morphs = !def.morph().isEmpty();
            if (morphs && !grantedWithSize.contains(def.id())) {
                out.add(def.id() + ": declares morph but no granting gene carries a size roll");
            }
            if (!morphs && grantedWithSize.contains(def.id())) {
                out.add(def.id() + ": a granting gene carries a size roll but the attachment declares no morph");
            }
            if (!grantedAtAll.contains(def.id())) {
                out.add(def.id() + ": no gene grants this attachment (unreachable)");
            }
            if (def.tintSource() == AttachmentDef.TINT_GENE && grantedAtAll.contains(def.id())
                    && !grantedWithTint.contains(def.id())) {
                out.add(def.id() + ": tint is \"gene\" but no granting gene declares a tint block "
                        + "(the attachment renders untinted)");
            }
            // Channel names the def reads must exist on some granting gene (the anonymous
            // legacy channel answers for a single named one, so it passes anywhere).
            Set<String> names = grantedChannels.get(def.id());
            if (names == null || names.isEmpty()) continue;
            for (AttachmentDef.MorphChannel channel : def.morph()) {
                if (!channel.channel().isEmpty() && !names.contains(channel.channel())) {
                    out.add(def.id() + ": morph channel '" + channel.channel()
                            + "' matches no granting gene's size channels (has: " + String.join(", ", names) + ")");
                }
            }
            for (AttachmentDef.VisibilityRule rule : def.visibility()) {
                if (!rule.channel().isEmpty() && !names.contains(rule.channel())) {
                    out.add(def.id() + ": visibility channel '" + rule.channel()
                            + "' matches no granting gene's size channels (has: " + String.join(", ", names) + ")");
                }
            }
        }
        return out;
    }

    private record GeoInfo(Set<String> bones, int texWidth, int texHeight) {}

    private static GeoInfo geoInfo(String sha1) {
        AttachmentServerData.Blob blob = AttachmentServerData.blob(sha1);
        if (blob == null) return null;
        try {
            JsonObject root = JsonParser.parseString(new String(blob.bytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            JsonArray geometries = GsonHelper.getAsJsonArray(root, "minecraft:geometry");
            if (geometries.isEmpty()) return null;
            JsonObject geometry = geometries.get(0).getAsJsonObject();
            JsonObject description = GsonHelper.getAsJsonObject(geometry, "description", new JsonObject());
            Set<String> bones = new LinkedHashSet<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(geometry, "bones", new JsonArray())) {
                bones.add(GsonHelper.getAsString(element.getAsJsonObject(), "name", ""));
            }
            return new GeoInfo(bones,
                    GsonHelper.getAsInt(description, "texture_width", 0),
                    GsonHelper.getAsInt(description, "texture_height", 0));
        } catch (Exception e) {
            return null;
        }
    }

    /** PNG pixel size from the IHDR chunk, or null when the blob is absent/not a PNG. */
    private static int[] pngSize(String sha1) {
        AttachmentServerData.Blob blob = AttachmentServerData.blob(sha1);
        if (blob == null) return null;
        byte[] b = blob.bytes();
        if (b.length < 24 || b[0] != (byte) 0x89 || b[1] != 'P' || b[2] != 'N' || b[3] != 'G') return null;
        int width = ((b[16] & 0xFF) << 24) | ((b[17] & 0xFF) << 16) | ((b[18] & 0xFF) << 8) | (b[19] & 0xFF);
        int height = ((b[20] & 0xFF) << 24) | ((b[21] & 0xFF) << 16) | ((b[22] & 0xFF) << 8) | (b[23] & 0xFF);
        return new int[]{width, height};
    }
}
