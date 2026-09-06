package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server → client: the selectable assignment profiles (lineage names + granted gene ids) plus
 * a gene dictionary (id → display data) covering every gene any profile grants, so
 * the picker renders fully on a client whose datapack-driven registries are empty.
 * Sent on login and datapack reload.
 */
//? if neoforge {
public record RootCatalogSyncPayload(List<RootCatalogEntry> entries, List<GeneCatalogEntry> genes,
                                       List<TraitCatalogEntry> traits, List<RigDefinition> rigs,
                                       List<String> entityGroups)
        implements CustomPacketPayload {
//?} else {
/*public record RootCatalogSyncPayload(List<RootCatalogEntry> entries, List<GeneCatalogEntry> genes,
                                       List<TraitCatalogEntry> traits, List<RigDefinition> rigs,
                                       List<String> entityGroups) {
*///?}

    //? if neoforge {
    public static final Type<RootCatalogSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "origin_catalog_sync"));

    public static final StreamCodec<FriendlyByteBuf, RootCatalogSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), RootCatalogSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "origin_catalog_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "origin_catalog_sync");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (RootCatalogEntry e : entries) {
            buf.writeUtf(e.id());
            buf.writeUtf(e.name());
            buf.writeUtf(e.demonymSingular());
            buf.writeUtf(e.demonymPlural());
            buf.writeUtf(e.backstory());
            buf.writeUtf(e.speciesName());
            buf.writeUtf(e.ancestryName());
            buf.writeUtf(e.lineageName());
            buf.writeVarInt(e.inheritedGenes().size());
            for (RootCatalogEntry.Inherited in : e.inheritedGenes()) {
                buf.writeUtf(in.geneId());
                buf.writeFloat(in.occurrence());
            }
            buf.writeVarInt(e.geneRanges().size());
            for (RootCatalogEntry.GeneRangeView r : e.geneRanges()) {
                buf.writeUtf(r.key());
                buf.writeFloat(r.min());
                buf.writeFloat(r.max());
            }
            buf.writeUtf(e.nameKey());
            buf.writeUtf(e.demonymSingularKey());
            buf.writeUtf(e.demonymPluralKey());
            buf.writeUtf(e.backstoryKey());
            buf.writeUtf(e.speciesNameKey());
            buf.writeUtf(e.ancestryNameKey());
            buf.writeUtf(e.lineageNameKey());
            buf.writeUtf(e.rigBase());
            buf.writeFloat(e.rigScale());
            writeAnimations(buf, e.animations());
            buf.writeBoolean(e.breasts());
            buf.writeVarInt(e.stageRigs().size());
            for (String r : e.stageRigs()) buf.writeUtf(r == null ? "" : r);
            writeCharacterEditor(buf, e.characterEditor());
            buf.writeBoolean(e.blocked());
        }
        buf.writeVarInt(genes.size());
        for (GeneCatalogEntry g : genes) {
            buf.writeUtf(g.id());
            buf.writeUtf(g.name());
            buf.writeUtf(g.description());
            buf.writeUtf(g.category());
            buf.writeVarInt(g.displayKind());
            buf.writeFloat(g.min());
            buf.writeFloat(g.max());
            buf.writeUtf(g.targetId());
            buf.writeFloat(g.amount());
            buf.writeVarInt(g.dominanceOrdinal());
            buf.writeUtf(g.locus());
            buf.writeVarInt(g.weight());
            buf.writeVarInt(g.variants().size());
            for (GeneCatalogEntry.Variant v : g.variants()) {
                buf.writeUtf(v.id());
                buf.writeUtf(v.label());
                buf.writeVarInt(v.weight());
                buf.writeUtf(v.labelKey());
                buf.writeInt(v.tint());
                buf.writeUtf(v.texture());
                buf.writeBoolean(v.glow());
                buf.writeUtf(v.attachment());
                writeChannels(buf, v.channels());
                writePalette(buf, v.palette());
            }
            buf.writeUtf(g.nameKey());
            buf.writeUtf(g.descriptionKey());
            buf.writeUtf(g.faceSlot());
            buf.writeUtf(g.sizeLabel());
            buf.writeUtf(g.sizeLabelKey());
            writeChannels(buf, g.channels());
            writePalette(buf, g.palette());
            buf.writeUtf(g.conditionJson());
        }
        buf.writeVarInt(traits.size());
        for (TraitCatalogEntry t : traits) {
            buf.writeUtf(t.id());
            buf.writeFloat(t.chance());
            buf.writeFloat(t.inherit());
            buf.writeBoolean(t.usableOnPlayer());
            buf.writeBoolean(t.hidden());
        }
        buf.writeVarInt(rigs.size());
        for (RigDefinition r : rigs) writeRig(buf, r);
        buf.writeVarInt(entityGroups.size());
        for (String group : entityGroups) buf.writeUtf(group);
    }

    public static RootCatalogSyncPayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<RootCatalogEntry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf();
            String name = buf.readUtf();
            String singular = buf.readUtf();
            String plural = buf.readUtf();
            String backstory = buf.readUtf();
            String speciesName = buf.readUtf();
            String ancestryName = buf.readUtf();
            String lineageName = buf.readUtf();
            int gn = buf.readVarInt();
            List<RootCatalogEntry.Inherited> inherited = new ArrayList<>(gn);
            for (int j = 0; j < gn; j++) {
                String igid = buf.readUtf();
                float iocc = buf.readFloat();
                inherited.add(new RootCatalogEntry.Inherited(igid, iocc));
            }
            int rn = buf.readVarInt();
            List<RootCatalogEntry.GeneRangeView> ranges = new ArrayList<>(rn);
            for (int j = 0; j < rn; j++) {
                ranges.add(new RootCatalogEntry.GeneRangeView(buf.readUtf(), buf.readFloat(), buf.readFloat()));
            }
            String nameKey = buf.readUtf();
            String singularKey = buf.readUtf();
            String pluralKey = buf.readUtf();
            String backstoryKey = buf.readUtf();
            String speciesNameKey = buf.readUtf();
            String ancestryNameKey = buf.readUtf();
            String lineageNameKey = buf.readUtf();
            String rigBase = buf.readUtf();
            float rigScale = buf.readFloat();
            Animations animations = readAnimations(buf);
            boolean breasts = buf.readBoolean();
            int stageRigCount = buf.readVarInt();
            List<String> stageRigs = new ArrayList<>(stageRigCount);
            for (int s = 0; s < stageRigCount; s++) stageRigs.add(buf.readUtf());
            CharacterEditorLayout characterEditor = readCharacterEditor(buf);
            boolean blocked = buf.readBoolean();
            entries.add(new RootCatalogEntry(id,
                    localize(nameKey, name),
                    localize(singularKey, singular),
                    localize(pluralKey, plural),
                    localize(backstoryKey, backstory),
                    localize(speciesNameKey, speciesName),
                    localize(ancestryNameKey, ancestryName),
                    localize(lineageNameKey, lineageName),
                    inherited, ranges,
                    nameKey, singularKey, pluralKey, backstoryKey,
                    speciesNameKey, ancestryNameKey, lineageNameKey,
                    rigBase, rigScale, animations, breasts, stageRigs, characterEditor, blocked));
        }
        int m = buf.readVarInt();
        List<GeneCatalogEntry> genes = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            String gid = buf.readUtf();
            String gname = buf.readUtf();
            String gdesc = buf.readUtf();
            String gcat = buf.readUtf();
            int kind = buf.readVarInt();
            float gmin = buf.readFloat();
            float gmax = buf.readFloat();
            String gtarget = buf.readUtf();
            float gamount = buf.readFloat();
            int gdom = buf.readVarInt();
            String glocus = buf.readUtf();
            int gweight = buf.readVarInt();
            int vn = buf.readVarInt();
            List<GeneCatalogEntry.Variant> variants = new ArrayList<>(vn);
            for (int j = 0; j < vn; j++) {
                String vid = buf.readUtf();
                String vlabel = buf.readUtf();
                int vweight = buf.readVarInt();
                String vlabelKey = buf.readUtf();
                int vtint = buf.readInt();
                String vtexture = buf.readUtf();
                boolean vglow = buf.readBoolean();
                String vattachment = buf.readUtf();
                List<GeneCatalogEntry.Channel> vchannels = readChannels(buf);
                List<Integer> vpalette = readPalette(buf);
                variants.add(new GeneCatalogEntry.Variant(vid, localize(vlabelKey, vlabel), vweight, vlabelKey,
                        vtint, vtexture, vglow, vattachment, vchannels, vpalette));
            }
            String gNameKey = buf.readUtf();
            String gDescKey = buf.readUtf();
            String gFaceSlot = buf.readUtf();
            String gSizeLabel = buf.readUtf();
            String gSizeLabelKey = buf.readUtf();
            List<GeneCatalogEntry.Channel> gChannels = readChannels(buf);
            List<Integer> gPalette = readPalette(buf);
            String gCondition = buf.readUtf();
            genes.add(new GeneCatalogEntry(gid, localize(gNameKey, gname), localize(gDescKey, gdesc),
                    gcat, kind, gmin, gmax,
                    gtarget, gamount, gdom, glocus, gweight, variants,
                    gNameKey, gDescKey, gFaceSlot,
                    localize(gSizeLabelKey, gSizeLabel), gSizeLabelKey, gChannels, gPalette, gCondition));
        }
        int k = buf.readVarInt();
        List<TraitCatalogEntry> traits = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            traits.add(new TraitCatalogEntry(
                    buf.readUtf(), buf.readFloat(), buf.readFloat(),
                    buf.readBoolean(), buf.readBoolean()));
        }
        int rn = buf.readVarInt();
        List<RigDefinition> rigs = new ArrayList<>(rn);
        for (int i = 0; i < rn; i++) rigs.add(readRig(buf));
        int groupCount = buf.readVarInt();
        List<String> entityGroups = new ArrayList<>(groupCount);
        for (int i = 0; i < groupCount; i++) entityGroups.add(buf.readUtf());
        return new RootCatalogSyncPayload(entries, genes, traits, rigs, entityGroups);
    }

    private static void writeChannels(FriendlyByteBuf buf, List<GeneCatalogEntry.Channel> channels) {
        buf.writeVarInt(channels.size());
        for (GeneCatalogEntry.Channel channel : channels) {
            buf.writeUtf(channel.name());
            buf.writeFloat(channel.min());
            buf.writeFloat(channel.max());
            buf.writeUtf(channel.label());
            buf.writeUtf(channel.labelKey());
        }
    }

    private static void writePalette(FriendlyByteBuf buf, List<Integer> palette) {
        buf.writeVarInt(palette.size());
        for (int color : palette) buf.writeInt(color);
    }

    private static List<Integer> readPalette(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Integer> palette = new ArrayList<>(count);
        for (int i = 0; i < count; i++) palette.add(buf.readInt());
        return palette;
    }

    private static List<GeneCatalogEntry.Channel> readChannels(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<GeneCatalogEntry.Channel> channels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = buf.readUtf();
            float min = buf.readFloat();
            float max = buf.readFloat();
            String label = buf.readUtf();
            String labelKey = buf.readUtf();
            channels.add(new GeneCatalogEntry.Channel(name, min, max, localize(labelKey, label), labelKey));
        }
        return channels;
    }

    /** Serialize a rig definition: model source, texture, bone map, and armor spec. */
    private static void writeRig(FriendlyByteBuf buf, RigDefinition r) {
        buf.writeUtf(r.id());
        buf.writeByte(r.modelType().ordinal());
        buf.writeUtf(r.modelRef());
        buf.writeUtf(r.modelLayer());
        buf.writeUtf(r.texture());
        buf.writeVarInt(r.bones().size());
        for (Map.Entry<String, String> e : r.bones().entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeUtf(e.getValue());
        }
        buf.writeByte(r.armorType().ordinal());
        writeNullableUtf(buf, r.armorInner());
        writeNullableUtf(buf, r.armorOuter());
        buf.writeBoolean(r.face() != null);
        if (r.face() != null) {
            RigDefinition.Face f = r.face();
            buf.writeUtf(f.bone());
            for (float v : f.center()) buf.writeFloat(v);
            for (float v : f.size()) buf.writeFloat(v);
            buf.writeFloat(f.forward());
        }
        writeWornAnchor(buf, r.back());
        writeWornAnchor(buf, r.head());
        buf.writeVarInt(r.boots().size());
        for (RigDefinition.Boot boot : r.boots()) {
            buf.writeUtf(boot.bone());
            buf.writeBoolean(boot.left());
            buf.writeFloat(boot.scale());
            writeAdjust(buf, boot.seat());
        }
        writeGrip(buf, r.hold().mainhand());
        writeGrip(buf, r.hold().offhand());
        buf.writeBoolean(r.hair());
        buf.writeVarInt(r.poses().size());
        for (Map.Entry<String, RigDefinition.PoseState> e : r.poses().entrySet()) {
            buf.writeUtf(e.getKey());
            RigDefinition.BodyPose body = e.getValue().body();
            buf.writeBoolean(body != null);
            if (body != null) {
                buf.writeFloat(body.yaw());
                buf.writeFloat(body.pitch());
                buf.writeFloat(body.roll());
                for (int k = 0; k < 3; k++) buf.writeFloat(body.offset()[k]);
            }
            List<RigDefinition.PoseBone> bones = e.getValue().bones();
            buf.writeVarInt(bones.size());
            for (RigDefinition.PoseBone p : bones) {
                buf.writeUtf(p.bone());
                for (int k = 0; k < 3; k++) buf.writeFloat(p.rotation()[k]);
                for (int k = 0; k < 3; k++) buf.writeFloat(p.offset()[k]);
            }
        }
        RigDefinition.Hitbox hb = r.hitbox();
        buf.writeBoolean(hb != null);
        if (hb != null) {
            buf.writeFloat(hb.width());
            buf.writeFloat(hb.height());
            buf.writeFloat(hb.crouchHeight());
        }
        buf.writeVarInt(r.disabledSlots().size());
        for (net.minecraft.world.entity.EquipmentSlot slot : r.disabledSlots()) buf.writeByte(slot.ordinal());
        buf.writeUtf(r.cameraBone() == null ? "" : r.cameraBone());
        writeEmote(buf, r.emote());
    }

    /** Serialize the emote remap: present flag, body-motion gate, per-channel remaps, and policy. */
    private static void writeEmote(FriendlyByteBuf buf, RigDefinition.EmoteMap m) {
        buf.writeBoolean(m != null);
        if (m == null) return;
        buf.writeFloat(m.bodyMotion().scale());
        buf.writeFloat(m.bodyMotion().floor());
        buf.writeVarInt(m.channels().size());
        for (Map.Entry<String, RigDefinition.EmoteChannel> e : m.channels().entrySet()) {
            buf.writeUtf(e.getKey());
            RigDefinition.EmoteChannel c = e.getValue();
            buf.writeUtf(c.bone());
            buf.writeByte(c.mode().ordinal());
            for (int i = 0; i < 3; i++) buf.writeByte(c.axisPerm()[i]);
            for (int i = 0; i < 3; i++) buf.writeFloat(c.axisSign()[i]);
            for (int i = 0; i < 3; i++) buf.writeFloat(c.euler()[i]);
            for (int i = 0; i < 3; i++) buf.writeFloat(c.gain()[i]);
            buf.writeBoolean(c.translation());
            for (int i = 0; i < 3; i++) buf.writeFloat(c.clampMin()[i]);
            for (int i = 0; i < 3; i++) buf.writeFloat(c.clampMax()[i]);
            buf.writeVarInt(c.also().size());
            for (RigDefinition.EmoteFan f : c.also()) {
                buf.writeUtf(f.bone());
                for (int i = 0; i < 3; i++) buf.writeFloat(f.gain()[i]);
            }
            buf.writeBoolean(c.bend());
            buf.writeFloat(c.bendGain());
        }
        RigDefinition.EmotePolicy p = m.policy();
        buf.writeFloat(p.minCoverage());
        buf.writeUtf(p.fallback() == null ? "" : p.fallback());
        buf.writeVarInt(p.allow().size());
        for (String s : p.allow()) buf.writeUtf(s);
        buf.writeVarInt(p.deny().size());
        for (String s : p.deny()) buf.writeUtf(s);
    }

    private static RigDefinition.EmoteMap readEmote(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        RigDefinition.BodyMotion bodyMotion = new RigDefinition.BodyMotion(buf.readFloat(), buf.readFloat());
        int cn = buf.readVarInt();
        Map<String, RigDefinition.EmoteChannel> channels = new java.util.LinkedHashMap<>();
        for (int i = 0; i < cn; i++) {
            String key = buf.readUtf();
            String bone = buf.readUtf();
            RigDefinition.EmoteMode mode = RigDefinition.EmoteMode.values()[buf.readByte()];
            int[] perm = {buf.readByte(), buf.readByte(), buf.readByte()};
            float[] sign = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
            float[] euler = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
            float[] gain = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
            boolean translation = buf.readBoolean();
            float[] clampMin = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
            float[] clampMax = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
            int fn = buf.readVarInt();
            List<RigDefinition.EmoteFan> also = new ArrayList<>(fn);
            for (int j = 0; j < fn; j++) {
                String fbone = buf.readUtf();
                also.add(new RigDefinition.EmoteFan(fbone,
                        new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat()}));
            }
            boolean bend = buf.readBoolean();
            float bendGain = buf.readFloat();
            channels.put(key, new RigDefinition.EmoteChannel(bone, mode, perm, sign, euler, gain,
                    translation, clampMin, clampMax, List.copyOf(also), bend, bendGain));
        }
        float minCoverage = buf.readFloat();
        String fallback = buf.readUtf();
        int an = buf.readVarInt();
        java.util.Set<String> allow = new java.util.LinkedHashSet<>();
        for (int i = 0; i < an; i++) allow.add(buf.readUtf());
        int dn = buf.readVarInt();
        java.util.Set<String> deny = new java.util.LinkedHashSet<>();
        for (int i = 0; i < dn; i++) deny.add(buf.readUtf());
        return new RigDefinition.EmoteMap(bodyMotion, Map.copyOf(channels),
                new RigDefinition.EmotePolicy(minCoverage, fallback, Set.copyOf(allow), Set.copyOf(deny)));
    }

    private static void writeWornAnchor(FriendlyByteBuf buf, RigDefinition.WornAnchor anchor) {
        buf.writeBoolean(anchor != null);
        if (anchor == null) return;
        writeAdjust(buf, anchor.base());
        buf.writeVarInt(anchor.items().size());
        for (Map.Entry<String, RigDefinition.Adjust> e : anchor.items().entrySet()) {
            buf.writeUtf(e.getKey());
            writeAdjust(buf, e.getValue());
        }
    }

    private static RigDefinition.WornAnchor readWornAnchor(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        RigDefinition.Adjust base = readAdjust(buf);
        int n = buf.readVarInt();
        Map<String, RigDefinition.Adjust> items = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) items.put(buf.readUtf(), readAdjust(buf));
        return new RigDefinition.WornAnchor(base, Map.copyOf(items));
    }

    private static void writeAdjust(FriendlyByteBuf buf, RigDefinition.Adjust a) {
        for (int k = 0; k < 3; k++) buf.writeFloat(a.offset()[k]);
        for (int k = 0; k < 3; k++) buf.writeFloat(a.rotation()[k]);
    }

    private static RigDefinition.Adjust readAdjust(FriendlyByteBuf buf) {
        float[] offset = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
        float[] rotation = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
        return new RigDefinition.Adjust(offset, rotation);
    }

    private static RigDefinition readRig(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        RigDefinition.ModelType modelType = RigDefinition.ModelType.values()[buf.readByte()];
        String modelRef = buf.readUtf();
        String modelLayer = buf.readUtf();
        String texture = buf.readUtf();
        int bn = buf.readVarInt();
        Map<String, String> bones = new java.util.LinkedHashMap<>();
        for (int i = 0; i < bn; i++) bones.put(buf.readUtf(), buf.readUtf());
        RigDefinition.ArmorType armorType = RigDefinition.ArmorType.values()[buf.readByte()];
        String inner = readNullableUtf(buf);
        String outer = readNullableUtf(buf);
        RigDefinition.Face face = null;
        if (buf.readBoolean()) {
            String bone = buf.readUtf();
            float[] center = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
            float[] size = {buf.readFloat(), buf.readFloat()};
            face = new RigDefinition.Face(bone, center, size, buf.readFloat());
        }
        RigDefinition.WornAnchor back = readWornAnchor(buf);
        RigDefinition.WornAnchor head = readWornAnchor(buf);
        int bootCount = buf.readVarInt();
        java.util.List<RigDefinition.Boot> boots = new ArrayList<>(bootCount);
        for (int i = 0; i < bootCount; i++) {
            String bone = buf.readUtf();
            boolean left = buf.readBoolean();
            float scale = buf.readFloat();
            boots.add(new RigDefinition.Boot(bone, left, scale, readAdjust(buf)));
        }
        Hold hold = new Hold(readGrip(buf), readGrip(buf));
        boolean hair = buf.readBoolean();
        int pn = buf.readVarInt();
        Map<String, RigDefinition.PoseState> poses = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pn; i++) {
            String state = buf.readUtf();
            RigDefinition.BodyPose body = buf.readBoolean()
                    ? new RigDefinition.BodyPose(buf.readFloat(), buf.readFloat(), buf.readFloat(),
                            new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat()})
                    : null;
            int cnt = buf.readVarInt();
            List<RigDefinition.PoseBone> list = new ArrayList<>(cnt);
            for (int j = 0; j < cnt; j++) {
                String bone = buf.readUtf();
                float[] rot = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
                float[] off = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
                list.add(new RigDefinition.PoseBone(bone, rot, off));
            }
            poses.put(state, new RigDefinition.PoseState(body, List.copyOf(list)));
        }
        RigDefinition.Hitbox hitbox = null;
        if (buf.readBoolean()) {
            hitbox = new RigDefinition.Hitbox(buf.readFloat(), buf.readFloat(), buf.readFloat());
        }
        int disabledCount = buf.readVarInt();
        java.util.Set<net.minecraft.world.entity.EquipmentSlot> disabledSlots = disabledCount == 0
                ? java.util.Set.of()
                : java.util.EnumSet.noneOf(net.minecraft.world.entity.EquipmentSlot.class);
        for (int i = 0; i < disabledCount; i++) {
            disabledSlots.add(net.minecraft.world.entity.EquipmentSlot.values()[buf.readByte()]);
        }
        String cameraBone = buf.readUtf();
        RigDefinition.EmoteMap emote = readEmote(buf);
        return new RigDefinition(id, modelType, modelRef, modelLayer, texture, bones, armorType, inner, outer, face, back, head, java.util.List.copyOf(boots), hold, hair, Map.copyOf(poses), hitbox, java.util.Set.copyOf(disabledSlots), cameraBone, emote);
    }

    private static void writeNullableUtf(FriendlyByteBuf buf, String value) {
        buf.writeBoolean(value != null);
        if (value != null) buf.writeUtf(value);
    }

    private static String readNullableUtf(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readUtf() : null;
    }

    /** Write one hand's grip: a present flag, then (if present) bone + third-person and first-person vecs. */
    private static void writeGrip(FriendlyByteBuf buf, Hold.Grip grip) {
        buf.writeBoolean(grip != null);
        if (grip == null) return;
        buf.writeUtf(grip.bone());
        for (int k = 0; k < 3; k++) buf.writeFloat(grip.offset()[k]);
        for (int k = 0; k < 3; k++) buf.writeFloat(grip.rotation()[k]);
        for (int k = 0; k < 3; k++) buf.writeFloat(grip.fpOffset()[k]);
        for (int k = 0; k < 3; k++) buf.writeFloat(grip.fpRotation()[k]);
    }

    /** Read one hand's grip, or null when the present flag is false (hand cannot hold). */
    private static Hold.Grip readGrip(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        String bone = buf.readUtf();
        float[] offset = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
        float[] rotation = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
        float[] fpOffset = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
        float[] fpRotation = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
        return new Hold.Grip(bone, offset, rotation, fpOffset, fpRotation);
    }

    /** Write each animation state's resolved source (one byte) then the provider chain. */
    private static void writeAnimations(FriendlyByteBuf buf, Animations animations) {
        Animations a = animations == null ? Animations.DEFAULT : animations;
        for (Animations.State state : Animations.State.values()) buf.writeByte(a.source(state).ordinal());
        buf.writeVarInt(a.providers().size());
        for (String provider : a.providers()) buf.writeUtf(provider);
    }

    private static Animations readAnimations(FriendlyByteBuf buf) {
        Map<Animations.State, Animations.Source> map = new EnumMap<>(Animations.State.class);
        for (Animations.State state : Animations.State.values()) {
            map.put(state, Animations.Source.values()[buf.readByte()]);
        }
        int pn = buf.readVarInt();
        List<String> providers = new ArrayList<>(pn);
        for (int i = 0; i < pn; i++) providers.add(buf.readUtf());
        return new Animations(map, providers);
    }

    /** Write the optional Character-editor layout: a present flag, then native groups + explicit tabs. */
    private static void writeCharacterEditor(FriendlyByteBuf buf, CharacterEditorLayout layout) {
        buf.writeBoolean(layout != null);
        if (layout == null) return;
        buf.writeVarInt(layout.nativeGroups().size());
        for (String g : layout.nativeGroups()) buf.writeUtf(g);
        buf.writeVarInt(layout.tabs().size());
        for (CharacterEditorLayout.Tab t : layout.tabs()) {
            buf.writeUtf(t.id());
            buf.writeUtf(t.label());
            buf.writeUtf(t.labelKey());
            buf.writeVarInt(t.fields().size());
            for (String f : t.fields()) buf.writeUtf(f);
        }
    }

    private static CharacterEditorLayout readCharacterEditor(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        int gn = buf.readVarInt();
        List<String> nativeGroups = new ArrayList<>(gn);
        for (int i = 0; i < gn; i++) nativeGroups.add(buf.readUtf());
        int tn = buf.readVarInt();
        List<CharacterEditorLayout.Tab> tabs = new ArrayList<>(tn);
        for (int i = 0; i < tn; i++) {
            String id = buf.readUtf();
            String label = buf.readUtf();
            String labelKey = buf.readUtf();
            int fn = buf.readVarInt();
            List<String> fields = new ArrayList<>(fn);
            for (int j = 0; j < fn; j++) fields.add(buf.readUtf());
            tabs.add(new CharacterEditorLayout.Tab(id, localize(labelKey, label), labelKey, fields));
        }
        return new CharacterEditorLayout(nativeGroups, tabs);
    }

    /**
     * Resolve a synced display string in the reading client's locale: when a
     * translate key travelled with it, render {@code translatableWithFallback}
     * (client lang table → localized, else the English fallback); otherwise the
     * value was a literal and is returned as-is. Read runs client-side (S2C), so
     * this resolves against the client's {@code Language}.
     */
    private static String localize(String key, String fallback) {
        return (key == null || key.isEmpty())
                ? fallback
                : Component.translatableWithFallback(key, fallback).getString();
    }
}
