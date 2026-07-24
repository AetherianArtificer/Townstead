package com.aetherianartificer.townstead.profession.career;

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
 * The server-rendered career registry record for the Career screen: one node per career and
 * per visible skill, with display state, progress, evidence, routes, and chronicle moments
 * already resolved, localized, and authorized per subject. Hidden specializations arrive
 * masked (no name, no evidence), so progressive disclosure is enforced before the wire.
 * {@code inspect} marks a read-only view of somebody else's record.
 */
//? if neoforge {
public record CareerGraphS2CPayload(String title, String scribeName, boolean inspect,
                                    List<Node> nodes) implements CustomPacketPayload {
//?} else {
/*public record CareerGraphS2CPayload(String title, String scribeName, boolean inspect,
                                    List<Node> nodes) {
*///?}

    public static final byte KIND_ROOT = 0;
    public static final byte KIND_ADVANCED = 1;
    public static final byte KIND_SKILL = 2;
    /** A Combo Skill: joins two or more careers laterally; evidence rows carry the thresholds. */
    public static final byte KIND_COMBO = 3;

    public static final byte STATE_HIDDEN = 0;
    public static final byte STATE_LOCKED = 1;
    public static final byte STATE_READY = 2;
    public static final byte STATE_ACQUIRED = 3;

    public record Evidence(String label, int current, int target, boolean met) {}

    public record Node(String id, String rootId, String parentId, byte kind, byte state,
                       String name, String description, String icon,
                       int tier, int maxTier, int xp, int xpToNext, int xpToday, int dailyCap,
                       boolean primary, boolean equipped, boolean tracked,
                       String routesLine, String replaces,
                       List<Evidence> evidence, List<String> moments,
                       String rankName, int points,
                       String group, String nextRankName, List<String> effects) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(title);
        buf.writeUtf(scribeName);
        buf.writeBoolean(inspect);
        buf.writeVarInt(nodes.size());
        for (Node node : nodes) {
            buf.writeUtf(node.id());
            buf.writeUtf(node.rootId());
            buf.writeUtf(node.parentId());
            buf.writeByte(node.kind());
            buf.writeByte(node.state());
            buf.writeUtf(node.name());
            buf.writeUtf(node.description());
            buf.writeUtf(node.icon());
            buf.writeVarInt(node.tier());
            buf.writeVarInt(node.maxTier());
            buf.writeVarInt(node.xp());
            buf.writeVarInt(node.xpToNext());
            buf.writeVarInt(node.xpToday());
            buf.writeVarInt(node.dailyCap());
            buf.writeBoolean(node.primary());
            buf.writeBoolean(node.equipped());
            buf.writeBoolean(node.tracked());
            buf.writeUtf(node.routesLine());
            buf.writeUtf(node.replaces());
            buf.writeVarInt(node.evidence().size());
            for (Evidence evidence : node.evidence()) {
                buf.writeUtf(evidence.label());
                buf.writeVarInt(evidence.current());
                buf.writeVarInt(evidence.target());
                buf.writeBoolean(evidence.met());
            }
            buf.writeVarInt(node.moments().size());
            for (String moment : node.moments()) {
                buf.writeUtf(moment);
            }
            buf.writeUtf(node.rankName());
            buf.writeVarInt(node.points());
            buf.writeUtf(node.group());
            buf.writeUtf(node.nextRankName());
            buf.writeVarInt(node.effects().size());
            for (String effect : node.effects()) {
                buf.writeUtf(effect);
            }
        }
    }

    public static CareerGraphS2CPayload read(FriendlyByteBuf buf) {
        String title = buf.readUtf();
        String scribeName = buf.readUtf();
        boolean inspect = buf.readBoolean();
        int nodeCount = buf.readVarInt();
        List<Node> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            String id = buf.readUtf();
            String rootId = buf.readUtf();
            String parentId = buf.readUtf();
            byte kind = buf.readByte();
            byte state = buf.readByte();
            String name = buf.readUtf();
            String description = buf.readUtf();
            String icon = buf.readUtf();
            int tier = buf.readVarInt();
            int maxTier = buf.readVarInt();
            int xp = buf.readVarInt();
            int xpToNext = buf.readVarInt();
            int xpToday = buf.readVarInt();
            int dailyCap = buf.readVarInt();
            boolean primary = buf.readBoolean();
            boolean equipped = buf.readBoolean();
            boolean tracked = buf.readBoolean();
            String routesLine = buf.readUtf();
            String replaces = buf.readUtf();
            int evidenceCount = buf.readVarInt();
            List<Evidence> evidence = new ArrayList<>(evidenceCount);
            for (int j = 0; j < evidenceCount; j++) {
                evidence.add(new Evidence(buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                        buf.readBoolean()));
            }
            int momentCount = buf.readVarInt();
            List<String> moments = new ArrayList<>(momentCount);
            for (int j = 0; j < momentCount; j++) {
                moments.add(buf.readUtf());
            }
            String rankName = buf.readUtf();
            int points = buf.readVarInt();
            String group = buf.readUtf();
            String nextRankName = buf.readUtf();
            int effectCount = buf.readVarInt();
            List<String> effects = new ArrayList<>(effectCount);
            for (int j = 0; j < effectCount; j++) {
                effects.add(buf.readUtf());
            }
            nodes.add(new Node(id, rootId, parentId, kind, state, name, description, icon,
                    tier, maxTier, xp, xpToNext, xpToday, dailyCap, primary, equipped, tracked,
                    routesLine, replaces, List.copyOf(evidence), List.copyOf(moments),
                    rankName, points, group, nextRankName, List.copyOf(effects)));
        }
        return new CareerGraphS2CPayload(title, scribeName, inspect, List.copyOf(nodes));
    }

    //? if neoforge {
    public static final Type<CareerGraphS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "career_graph"));
    public static final StreamCodec<FriendlyByteBuf, CareerGraphS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), CareerGraphS2CPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "career_graph");
    *///?}
}
