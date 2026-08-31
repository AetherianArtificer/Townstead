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
public record CareerGraphS2CPayload(String title, boolean inspect,
                                    String notice, String authority, String dateLine,
                                    List<Node> nodes) implements CustomPacketPayload {
//?} else {
/*public record CareerGraphS2CPayload(String title, boolean inspect,
                                    String notice, String authority, String dateLine,
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

    /**
     * A skill's specialization membership, so the board can draw the path as one arm off the
     * career: every member carries the path's id and name, and {@code gateway} marks the skill
     * that opens it. {@link #NONE} is a trunk skill, a career, or a Combo Skill.
     */
    public record PathTag(String id, String name, boolean gateway, int color, String backdrop) {
        public static final PathTag NONE = new PathTag("", "", false, 0, "");
        public boolean present() { return !id.isEmpty(); }

        /** Compatibility constructor predating the board's authored section styling. */
        public PathTag(String id, String name, boolean gateway) {
            this(id, name, gateway, 0, "");
        }
    }

    /**
     * The mark the subject pressed on this node's record, in page space, or {@link #NONE}.
     *
     * <p>The authority and date are the strings that were true on the day, carried with the mark
     * rather than resolved at render time, so a renamed village does not rewrite old records.</p>
     */
    public record Stamp(boolean present, int x, int y, float rotation, String authority,
                         String date, String textureId, String sourcePack, String label) {
        public static final Stamp NONE = new Stamp(false, 0, 0, 0f, "", "", "", "", "");
    }

    /**
     * A skill's ACTIVE ability: something you press, in a slot, on a cooldown, sometimes for a
     * price. {@link #NONE} for the great majority of skills, which simply apply.
     *
     * <p>Structured rather than pre-rendered prose, because the record needs to say one thing the
     * server cannot know: whether the player has actually BOUND the key for this slot. An active
     * ability whose slot is unbound is learned, paid for, and inert.</p>
     */
    public record Ability(boolean present, int slot, int cooldownTicks, int costAmount,
                          String costLabel) {
        public static final Ability NONE = new Ability(false, 0, 0, 0, "");
    }

    public record Node(String id, String rootId, String parentId, byte kind, byte state,
                       String name, String description, String icon,
                       int tier, int maxTier, int xp, int xpToNext, int xpToday, int dailyCap,
                       boolean primary, boolean equipped,
                       String routesLine, String replaces,
                       List<Evidence> evidence, List<String> moments,
                       String rankName, int points,
                       String group, String nextRankName, List<String> effects,
                       List<String> requires, PathTag path, Stamp stamp, Ability ability) {

        /** Compatibility constructor predating the active-ability block. */
        public Node(String id, String rootId, String parentId, byte kind, byte state,
                    String name, String description, String icon,
                    int tier, int maxTier, int xp, int xpToNext, int xpToday, int dailyCap,
                    boolean primary, boolean equipped,
                    String routesLine, String replaces,
                    List<Evidence> evidence, List<String> moments,
                    String rankName, int points,
                    String group, String nextRankName, List<String> effects,
                    List<String> requires, PathTag path, Stamp stamp) {
            this(id, rootId, parentId, kind, state, name, description, icon, tier, maxTier, xp,
                    xpToNext, xpToday, dailyCap, primary, equipped, routesLine, replaces,
                    evidence, moments, rankName, points, group, nextRankName, effects, requires,
                    path, stamp, Ability.NONE);
        }

        /** Compatibility constructor predating the player-pressed Archives stamp. */
        public Node(String id, String rootId, String parentId, byte kind, byte state,
                    String name, String description, String icon,
                    int tier, int maxTier, int xp, int xpToNext, int xpToday, int dailyCap,
                    boolean primary, boolean equipped,
                    String routesLine, String replaces,
                    List<Evidence> evidence, List<String> moments,
                    String rankName, int points,
                    String group, String nextRankName, List<String> effects,
                    List<String> requires, PathTag path) {
            this(id, rootId, parentId, kind, state, name, description, icon, tier, maxTier, xp,
                    xpToNext, xpToday, dailyCap, primary, equipped, routesLine, replaces,
                    evidence, moments, rankName, points, group, nextRankName, effects, requires,
                    path, Stamp.NONE, Ability.NONE);
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(title);
        buf.writeBoolean(inspect);
        buf.writeUtf(notice);
        buf.writeUtf(authority);
        buf.writeUtf(dateLine);
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
            buf.writeVarInt(node.requires().size());
            for (String required : node.requires()) {
                buf.writeUtf(required);
            }
            buf.writeUtf(node.path().id());
            buf.writeUtf(node.path().name());
            buf.writeBoolean(node.path().gateway());
            buf.writeInt(node.path().color());
            buf.writeUtf(node.path().backdrop());
            buf.writeBoolean(node.stamp().present());
            if (node.stamp().present()) {
                buf.writeVarInt(node.stamp().x());
                buf.writeVarInt(node.stamp().y());
                buf.writeFloat(node.stamp().rotation());
                buf.writeUtf(node.stamp().authority());
                buf.writeUtf(node.stamp().date());
                buf.writeUtf(node.stamp().textureId());
                buf.writeUtf(node.stamp().sourcePack());
                buf.writeUtf(node.stamp().label());
            }
            buf.writeBoolean(node.ability().present());
            if (node.ability().present()) {
                buf.writeVarInt(node.ability().slot());
                buf.writeVarInt(node.ability().cooldownTicks());
                buf.writeVarInt(node.ability().costAmount());
                buf.writeUtf(node.ability().costLabel());
            }
        }
    }

    public static CareerGraphS2CPayload read(FriendlyByteBuf buf) {
        String title = buf.readUtf();
        boolean inspect = buf.readBoolean();
        String notice = buf.readUtf();
        String authority = buf.readUtf();
        String dateLine = buf.readUtf();
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
            int requireCount = buf.readVarInt();
            List<String> requires = new ArrayList<>(requireCount);
            for (int j = 0; j < requireCount; j++) {
                requires.add(buf.readUtf());
            }
            PathTag path = new PathTag(buf.readUtf(), buf.readUtf(), buf.readBoolean(),
                    buf.readInt(), buf.readUtf());
            Stamp stamp = Stamp.NONE;
            if (buf.readBoolean()) {
                stamp = new Stamp(true, buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                        buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf());
            }
            Ability ability = Ability.NONE;
            if (buf.readBoolean()) {
                ability = new Ability(true, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readUtf());
            }
            nodes.add(new Node(id, rootId, parentId, kind, state, name, description, icon,
                    tier, maxTier, xp, xpToNext, xpToday, dailyCap, primary, equipped,
                    routesLine, replaces, List.copyOf(evidence), List.copyOf(moments),
                    rankName, points, group, nextRankName, List.copyOf(effects),
                    List.copyOf(requires), path, stamp, ability));
        }
        return new CareerGraphS2CPayload(title, inspect, notice, authority, dateLine,
                List.copyOf(nodes));
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
