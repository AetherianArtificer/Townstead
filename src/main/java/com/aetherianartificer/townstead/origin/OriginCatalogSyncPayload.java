package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.origin.rig.RigDefinition;
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

/**
 * Server → client: the selectable assignment profiles (lineage names + granted gene ids) plus
 * a gene dictionary (id → display data) covering every gene any profile grants, so
 * the picker renders fully on a client whose datapack-driven registries are empty.
 * Sent on login and datapack reload.
 */
//? if neoforge {
public record OriginCatalogSyncPayload(List<OriginCatalogEntry> entries, List<GeneCatalogEntry> genes,
                                       List<TraitCatalogEntry> traits, List<RigDefinition> rigs)
        implements CustomPacketPayload {
//?} else {
/*public record OriginCatalogSyncPayload(List<OriginCatalogEntry> entries, List<GeneCatalogEntry> genes,
                                       List<TraitCatalogEntry> traits, List<RigDefinition> rigs) {
*///?}

    //? if neoforge {
    public static final Type<OriginCatalogSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "origin_catalog_sync"));

    public static final StreamCodec<FriendlyByteBuf, OriginCatalogSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), OriginCatalogSyncPayload::read);

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
        for (OriginCatalogEntry e : entries) {
            buf.writeUtf(e.id());
            buf.writeUtf(e.name());
            buf.writeUtf(e.demonymSingular());
            buf.writeUtf(e.demonymPlural());
            buf.writeUtf(e.backstory());
            buf.writeUtf(e.speciesName());
            buf.writeUtf(e.ancestryName());
            buf.writeUtf(e.lineageName());
            buf.writeVarInt(e.inheritedGenes().size());
            for (OriginCatalogEntry.Inherited in : e.inheritedGenes()) {
                buf.writeUtf(in.geneId());
                buf.writeFloat(in.occurrence());
            }
            buf.writeVarInt(e.geneRanges().size());
            for (OriginCatalogEntry.GeneRangeView r : e.geneRanges()) {
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
            }
            buf.writeUtf(g.nameKey());
            buf.writeUtf(g.descriptionKey());
            buf.writeUtf(g.faceSlot());
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
    }

    public static OriginCatalogSyncPayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<OriginCatalogEntry> entries = new ArrayList<>(n);
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
            List<OriginCatalogEntry.Inherited> inherited = new ArrayList<>(gn);
            for (int j = 0; j < gn; j++) {
                String igid = buf.readUtf();
                float iocc = buf.readFloat();
                inherited.add(new OriginCatalogEntry.Inherited(igid, iocc));
            }
            int rn = buf.readVarInt();
            List<OriginCatalogEntry.GeneRangeView> ranges = new ArrayList<>(rn);
            for (int j = 0; j < rn; j++) {
                ranges.add(new OriginCatalogEntry.GeneRangeView(buf.readUtf(), buf.readFloat(), buf.readFloat()));
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
            entries.add(new OriginCatalogEntry(id,
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
                    rigBase, rigScale, animations, breasts, stageRigs));
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
                variants.add(new GeneCatalogEntry.Variant(vid, localize(vlabelKey, vlabel), vweight, vlabelKey,
                        vtint, vtexture, vglow));
            }
            String gNameKey = buf.readUtf();
            String gDescKey = buf.readUtf();
            String gFaceSlot = buf.readUtf();
            genes.add(new GeneCatalogEntry(gid, localize(gNameKey, gname), localize(gDescKey, gdesc),
                    gcat, kind, gmin, gmax,
                    gtarget, gamount, gdom, glocus, gweight, variants,
                    gNameKey, gDescKey, gFaceSlot));
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
        return new OriginCatalogSyncPayload(entries, genes, traits, rigs);
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
        for (Map.Entry<String, List<RigDefinition.PoseBone>> e : r.poses().entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue().size());
            for (RigDefinition.PoseBone p : e.getValue()) {
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
        }
        buf.writeVarInt(r.disabledSlots().size());
        for (net.minecraft.world.entity.EquipmentSlot slot : r.disabledSlots()) buf.writeByte(slot.ordinal());
        buf.writeUtf(r.cameraBone() == null ? "" : r.cameraBone());
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
        Map<String, List<RigDefinition.PoseBone>> poses = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pn; i++) {
            String state = buf.readUtf();
            int cnt = buf.readVarInt();
            List<RigDefinition.PoseBone> list = new ArrayList<>(cnt);
            for (int j = 0; j < cnt; j++) {
                String bone = buf.readUtf();
                float[] rot = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
                float[] off = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
                list.add(new RigDefinition.PoseBone(bone, rot, off));
            }
            poses.put(state, List.copyOf(list));
        }
        RigDefinition.Hitbox hitbox = null;
        if (buf.readBoolean()) {
            hitbox = new RigDefinition.Hitbox(buf.readFloat(), buf.readFloat());
        }
        int disabledCount = buf.readVarInt();
        java.util.Set<net.minecraft.world.entity.EquipmentSlot> disabledSlots = disabledCount == 0
                ? java.util.Set.of()
                : java.util.EnumSet.noneOf(net.minecraft.world.entity.EquipmentSlot.class);
        for (int i = 0; i < disabledCount; i++) {
            disabledSlots.add(net.minecraft.world.entity.EquipmentSlot.values()[buf.readByte()]);
        }
        String cameraBone = buf.readUtf();
        return new RigDefinition(id, modelType, modelRef, modelLayer, texture, bones, armorType, inner, outer, face, back, head, java.util.List.copyOf(boots), hold, hair, Map.copyOf(poses), hitbox, java.util.Set.copyOf(disabledSlots), cameraBone);
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
