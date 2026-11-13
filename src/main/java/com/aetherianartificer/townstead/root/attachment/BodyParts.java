package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.mixin.ServerLevelPartsAccessor;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Parts of a body that can be hit on their own. Each hittable box becomes a part entity that vanilla's
 * aiming and projectiles find on their own, and Forge and NeoForge route it the way they route a
 * dragon's. They only do that for parts registered when the entity joined the level, so these are added
 * and removed here as genes change. Every part entity in a level costs every entity search, so only a
 * part a pack declared gets any. Any other attachment is hit as its bearer, and {@link #onBodyHit} works
 * out from the path of the blow which one it reached.
 *
 * <p>Each part's health and stage live on the bearer. A hit takes from the part's own pool, then passes
 * a share to the bearer. Area damage reaches the bearer anyway, so from a part it only hurts the part.
 * An attachment worn at several places (both ears) is a piece at each, with health of its own.</p>
 */
public final class BodyParts {

    private static final String STORAGE = "TownsteadParts";
    private static final String HEALTH = "h", STAGE = "s", REGROW_AT = "r";
    private static final int PRUNE_INTERVAL = 100;
    private static final float TRACE_PER_HIT = 0.2f;
    private static final int PROP_TICKS = 120;

    private static final Map<LivingEntity, ServerEntry> SERVER = new ConcurrentHashMap<>();
    private static final Map<String, Action> ACTIONS = new ConcurrentHashMap<>();
    // Which attachment's record a part uses: its own, or that of the one it shares with.
    private static final Map<String, String> OWNERS = new ConcurrentHashMap<>();
    private static final Action NOTHING = ctx -> {};
    private static final float STUMP_AMOUNT = 0.6f;
    private static final String ANY_LOST = "#any";
    private static final double SWING_REACH = 6.0;
    // Set while a part entity passes its share of a hit on to the bearer: that hit has found its part.
    private static boolean passingOn;

    private record ServerEntry(Level level, List<AttachmentPart> parts, String signature) {}

    static {
        BodyValues.register(BodyParts::stageValues);
    }

    private BodyParts() {}

    public static String stageKey(String attachment) {
        return "part:" + attachment;
    }

    private static String pieceKey(String attachment, int piece) {
        return piece == 0 ? attachment : attachment + "#" + piece;
    }

    /** The stage of a part by its attachment id, on either side: lost once every piece is, wounded once any is hurt. */
    public static int stage(LivingEntity entity, String attachment) {
        if (entity.level().isClientSide) {
            return Math.round(BodyValues.read(entity, stageKey(attachment), AttachmentBody.Part.INTACT));
        }
        String owner = ownerOf(attachment);
        CompoundTag parts = entity.getPersistentData().getCompound(STORAGE);
        if (parts.isEmpty()) return AttachmentBody.Part.INTACT;
        AttachmentDef def = AttachmentServerData.definition(owner);
        int pieces = def == null ? 1 : BodyPose.pieces(entity, def);
        int lost = 0;
        boolean hurt = false;
        for (int p = 0; p < pieces; p++) {
            String key = pieceKey(owner, p);
            int stage = parts.contains(key) ? parts.getCompound(key).getInt(STAGE) : AttachmentBody.Part.INTACT;
            if (stage == AttachmentBody.Part.LOST) lost++;
            if (stage != AttachmentBody.Part.INTACT) hurt = true;
        }
        return lost == pieces ? AttachmentBody.Part.LOST : hurt ? AttachmentBody.Part.WOUNDED : AttachmentBody.Part.INTACT;
    }

    /** Whether any piece of this attachment is gone, on either side: one wing is enough to stop flying. */
    public static boolean anyLost(LivingEntity entity, String attachment) {
        if (entity.level().isClientSide) {
            return BodyValues.read(entity, stageKey(attachment + ANY_LOST), 0f) > 0f;
        }
        CompoundTag parts = entity.getPersistentData().getCompound(STORAGE);
        if (parts.isEmpty()) return false;
        String owner = ownerOf(attachment);
        for (String key : parts.getAllKeys()) {
            if ((key.equals(owner) || key.startsWith(owner + "#")) && parts.getCompound(key).getInt(STAGE) == AttachmentBody.Part.LOST) return true;
        }
        return false;
    }

    /** Whether this one piece is gone while the rest of the attachment may not be, on either side. */
    public static boolean pieceLost(LivingEntity entity, String attachment, int piece) {
        if (entity.level().isClientSide) {
            return Math.round(BodyValues.read(entity, stageKey(attachment + "#" + piece), AttachmentBody.Part.INTACT))
                    == AttachmentBody.Part.LOST;
        }
        String key = pieceKey(ownerOf(attachment), piece);
        CompoundTag parts = entity.getPersistentData().getCompound(STORAGE);
        return parts.contains(key) && parts.getCompound(key).getInt(STAGE) == AttachmentBody.Part.LOST;
    }

    private static String ownerOf(String attachment) {
        return OWNERS.computeIfAbsent(attachment, id -> {
            AttachmentDef def = AttachmentServerData.definition(id);
            AttachmentBody.Part part = def == null ? null : def.body().part();
            return part == null || part.sharesWith().isEmpty() ? id : part.sharesWith();
        });
    }

    /** What a worn part looks like now: itself, or the attachment worn in its place. Null is nothing at all. */
    public static AttachmentDef shown(LivingEntity entity, AttachmentDef def, java.util.function.Function<String, AttachmentDef> lookup) {
        AttachmentBody.Part part = def.body().part();
        if (part == null) return def;
        int stage = stage(entity, def.id());
        if (stage == AttachmentBody.Part.LOST) return part.severed().isEmpty() ? null : lookup.apply(part.severed());
        if (stage == AttachmentBody.Part.WOUNDED && !part.wounded().isEmpty()) {
            AttachmentDef wounded = lookup.apply(part.wounded());
            return wounded != null ? wounded : def;
        }
        return def;
    }

    private static void stageValues(LivingEntity entity, BiConsumer<String, Float> out) {
        for (AttachmentDef def : WornAttachments.declared(entity)) {
            // Most parts are whole, and a client reads no value as whole, so only the others take up a packet.
            if (def.body().part() == null) continue;
            int stage = stage(entity, def.id());
            if (stage == AttachmentBody.Part.INTACT) continue;
            out.accept(stageKey(def.id()), (float) stage);
            if (anyLost(entity, def.id())) out.accept(stageKey(def.id() + ANY_LOST), 1f);
            int pieces = BodyPose.pieces(entity, def);
            for (int p = 0; pieces > 1 && p < pieces; p++) {
                if (pieceLost(entity, def.id(), p)) out.accept(stageKey(def.id() + "#" + p), (float) AttachmentBody.Part.LOST);
            }
        }
    }

    // --- Server -----------------------------------------------------------------------------

    /** Runs each server tick for a person: keeps their part entities placed and their wounds healing. */
    public static void tick(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        List<AttachmentDef> declared = WornAttachments.declared(entity);
        List<AttachmentPart> parts = sync(entity, level, declared);
        place(entity, parts);
        heal(entity, declared);
        if (entity.tickCount % PRUNE_INTERVAL == 0) prune();
    }

    private static List<AttachmentPart> sync(LivingEntity entity, ServerLevel level, List<AttachmentDef> declared) {
        StringBuilder signature = new StringBuilder();
        for (AttachmentDef def : declared) {
            if (!wantsEntities(entity, def)) continue;
            signature.append(def.id()).append(':').append(HitShapes.count(def, entity)).append(';');
        }
        ServerEntry entry = SERVER.get(entity);
        String wanted = signature.toString();
        if (entry != null && entry.signature().equals(wanted) && entry.level() == level) return entry.parts();
        if (entry != null) unregister(entry);
        if (wanted.isEmpty()) {
            SERVER.remove(entity);
            if (entry != null) send(entity, List.of());
            return List.of();
        }
        List<AttachmentPart> parts = new ArrayList<>();
        for (AttachmentDef def : declared) {
            if (!wantsEntities(entity, def)) continue;
            int count = HitShapes.count(def, entity);
            for (int i = 0; i < count; i++) {
                AttachmentPart part = new AttachmentPart(entity, def.id(), i);
                ((ServerLevelPartsAccessor) level).townstead$parts().put(part.getId(), part);
                parts.add(part);
            }
        }
        SERVER.put(entity, new ServerEntry(level, parts, wanted));
        send(entity, parts);
        return parts;
    }

    /** Part entities cost every search in the level, so a pose that isn't showing doesn't keep any. */
    private static boolean wantsEntities(LivingEntity entity, AttachmentDef def) {
        AttachmentBody.Part part = def.body().part();
        return part != null && part.hasEntities() && stage(entity, def.id()) != AttachmentBody.Part.LOST
                && HitShapes.active(entity, def);
    }

    private static void unregister(ServerEntry entry) {
        if (!(entry.level() instanceof ServerLevel level)) return;
        for (AttachmentPart part : entry.parts()) {
            ((ServerLevelPartsAccessor) level).townstead$parts().remove(part.getId());
        }
    }

    private static void prune() {
        SERVER.entrySet().removeIf(e -> {
            if (!e.getKey().isRemoved()) return false;
            unregister(e.getValue());
            return true;
        });
    }

    /** Puts each part where its box is now. */
    public static void place(LivingEntity entity, List<AttachmentPart> parts) {
        if (parts.isEmpty()) return;
        Map<String, List<AABB>> boxes = new java.util.HashMap<>();
        for (AttachmentDef def : WornAttachments.of(entity)) {
            AttachmentBody.Part rules = def.body().part();
            if (rules != null && rules.hasEntities()) boxes.put(def.id(), HitShapes.aligned(entity, def));
        }
        for (AttachmentPart part : parts) {
            List<AABB> mine = boxes.get(part.attachment);
            AABB box = mine != null && part.boxIndex < mine.size() ? mine.get(part.boxIndex) : null;
            if (box != null) part.place(box.move(entity.position()));
            else part.showing = false;
        }
    }

    private static void send(LivingEntity entity, List<AttachmentPart> parts) {
        BodyPartsS2CPayload payload = payload(entity, parts);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(entity, payload);
        if (entity instanceof ServerPlayer self) com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(self, payload);
        *///?}
    }

    /** Tells a player who starts seeing {@code target} which parts it has. */
    public static void syncTo(ServerPlayer player, Entity target) {
        if (!(target instanceof LivingEntity living)) return;
        ServerEntry entry = SERVER.get(living);
        if (entry == null || entry.parts().isEmpty()) return;
        BodyPartsS2CPayload payload = payload(living, entry.parts());
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    private static BodyPartsS2CPayload payload(LivingEntity entity, List<AttachmentPart> parts) {
        List<BodyPartsS2CPayload.Entry> entries = new ArrayList<>(parts.size());
        for (AttachmentPart part : parts) {
            entries.add(new BodyPartsS2CPayload.Entry(part.attachment, part.boxIndex, part.getId()));
        }
        return new BodyPartsS2CPayload(entity.getId(), entries);
    }

    // --- Hits -------------------------------------------------------------------------------

    static boolean hurt(AttachmentPart part, DamageSource source, float amount) {
        LivingEntity bearer = part.getParent();
        if (bearer.isRemoved() || bearer.level().isClientSide) return !bearer.isRemoved();
        // Your own shots and swings pass through your own parts.
        if (source.getEntity() == bearer) return false;
        AttachmentDef def = AttachmentServerData.definition(ownerOf(part.attachment));
        AttachmentBody.Part rules = def == null ? null : def.body().part();
        if (rules == null) return bearer.hurt(source, amount);
        if (bearer.isInvulnerableTo(source)) return false;

        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        wound(bearer, def, rules, BodyPose.pieceOfBox(bearer, def, part.boxIndex), source, amount, attacker, severs(rules, source));
        // Area damage reaches the bearer on its own; passing it on would count it twice.
        boolean area = source.is(DamageTypeTags.IS_EXPLOSION);
        if (!area && rules.damageScale() <= 0f && source.getDirectEntity() != null) {
            Vec3 at = part.getBoundingBox().getCenter();
            com.aetherianartificer.townstead.compat.mess.MessBridge.partHit(bearer, at, at.subtract(source.getDirectEntity().position()), amount);
        }
        if (area || rules.damageScale() <= 0f) return true;
        passingOn = true;
        try {
            return bearer.hurt(source, amount * rules.damageScale());
        } finally {
            passingOn = false;
        }
    }

    /**
     * A hit that landed on the bearer takes the part its path reached first, judged from the server's own
     * pose. A shot follows its flight, a person their aim, and a mob swings at the middle of the body.
     */
    public static void onBodyHit(LivingEntity bearer, DamageSource source, float amount) {
        if (passingOn || amount <= 0f) return;
        Vec3[] path = blowPath(bearer, source);
        if (path == null) return;
        Vec3 from = path[0], to = path[1];
        BodyPose.Strike onBody = BodyPose.strike(bearer, from, to);
        // Only something the blow reaches before the body itself: a part can run right through to the back.
        Vec3 body = onBody.exact() ? onBody.world() : bearer.getBoundingBox().clip(from, to).orElse(null);
        double closest = body == null ? Double.MAX_VALUE : body.distanceToSqr(from);
        AttachmentDef nearest = null;
        int piece = 0;
        for (AttachmentDef def : WornAttachments.of(bearer)) {
            if (def.body().part() == null || !HitShapes.active(bearer, def)) continue;
            List<AABB> boxes = HitShapes.aligned(bearer, def);
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i) == null) continue;
                Vec3 at = boxes.get(i).move(bearer.position()).clip(from, to).orElse(null);
                if (at == null || at.distanceToSqr(from) >= closest) continue;
                closest = at.distanceToSqr(from);
                nearest = def;
                piece = BodyPose.pieceOfBox(bearer, def, i);
            }
        }
        if (nearest == null) return;
        AttachmentDef def = AttachmentServerData.definition(ownerOf(nearest.id()));
        AttachmentBody.Part rules = def == null ? null : def.body().part();
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        if (rules != null) wound(bearer, def, rules, piece, source, amount, attacker, severs(rules, source));
    }

    /**
     * Where a blow travels: a shot along its flight, a person along their aim, and a mob at somewhere on the
     * body. Null for damage that doesn't come from something, or doesn't have a direction to speak of.
     */
    @org.jetbrains.annotations.Nullable
    public static Vec3[] blowPath(LivingEntity bearer, DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return null;
        Entity direct = source.getDirectEntity();
        if (direct == null || direct == bearer || source.getEntity() == bearer) return null;
        Vec3 from, to;
        if (direct instanceof Projectile) {
            from = direct.position();
            to = from.add(direct.getDeltaMovement().scale(1.5));
        } else if (direct instanceof net.minecraft.world.entity.player.Player player) {
            from = player.getEyePosition();
            to = from.add(player.getLookAngle().scale(SWING_REACH));
        } else if (direct instanceof LivingEntity swinger) {
            from = swinger.getEyePosition();
            // A mob can't aim, so its blow lands somewhere on the body: an arm, a leg.
            var random = bearer.getRandom();
            double wide = bearer.getBbWidth();
            Vec3 aim = bearer.position().add((random.nextDouble() - 0.5) * wide, bearer.getBbHeight() * (0.15 + random.nextDouble() * 0.8),
                    (random.nextDouble() - 0.5) * wide);
            to = from.add(aim.subtract(from).scale(1.4));
        } else {
            return null;
        }
        return new Vec3[]{from, to};
    }



    /** Hits a part directly, whatever could normally cut it: for testing and for actions. */
    public static boolean strike(LivingEntity bearer, String id, float amount) {
        AttachmentDef def = AttachmentServerData.definition(ownerOf(id));
        AttachmentBody.Part rules = def == null ? null : def.body().part();
        if (rules == null) return false;
        int pieces = BodyPose.pieces(bearer, def);
        for (int p = 0; p < pieces; p++) wound(bearer, def, rules, p, bearer.level().damageSources().generic(), amount, null, true);
        return true;
    }

    /** As {@link #strike}, on one piece alone. */
    public static boolean strike(LivingEntity bearer, String id, int piece, float amount) {
        AttachmentDef def = AttachmentServerData.definition(ownerOf(id));
        AttachmentBody.Part rules = def == null ? null : def.body().part();
        if (rules == null || piece < 0 || piece >= BodyPose.pieces(bearer, def)) return false;
        wound(bearer, def, rules, piece, bearer.level().damageSources().generic(), amount, null, true);
        return true;
    }

    private static void wound(LivingEntity bearer, AttachmentDef def, AttachmentBody.Part rules, int piece, DamageSource source,
                              float amount, LivingEntity attacker, boolean cuts) {
        String key = pieceKey(def.id(), piece);
        PartWounds.State before = read(bearer, key, rules);
        // Where it was, while it's still there to measure.
        Vec3 site = pieceCenter(bearer, def, piece);
        PartWounds.State after = PartWounds.hit(before, rules, amount, cuts && Switchboard.get(TownsteadConfig.ENABLE_SEVERING));
        run(rules.onHit(), bearer, attacker);
        if (!rules.trace().isEmpty()) com.aetherianartificer.townstead.compat.mess.MessBridge.addTrace(bearer, rules.trace(), TRACE_PER_HIT);
        long regrowAt = 0L;
        if (after.stage() == AttachmentBody.Part.LOST && before.stage() != AttachmentBody.Part.LOST) {
            regrowAt = rules.regrowTicks() > 0 ? bearer.level().getGameTime() + rules.regrowTicks() : -1L;
        }
        write(bearer, key, after, regrowAt);
        if (after.stage() == before.stage()) return;
        if (after.stage() == AttachmentBody.Part.WOUNDED) run(rules.onWounded(), bearer, attacker);
        if (after.stage() == AttachmentBody.Part.LOST) {
            run(rules.onSevered(), bearer, attacker);
            drop(bearer, rules);
            letFall(bearer, def.id(), piece);
            if (rules.severed().isEmpty() && !rules.stumpTrace().isEmpty()) {
                com.aetherianartificer.townstead.compat.mess.MessBridge.addTrace(bearer, rules.stumpTrace(), STUMP_AMOUNT);
                if (site != null) com.aetherianartificer.townstead.compat.mess.MessBridge.partLost(bearer, rules.stumpTrace(), site.add(bearer.position()));
            }
        }
    }

    private static Vec3 pieceCenter(LivingEntity bearer, AttachmentDef def, int piece) {
        List<AABB> boxes = HitShapes.aligned(bearer, def);
        Vec3 sum = Vec3.ZERO;
        int count = 0;
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i) == null || BodyPose.pieceOfBox(bearer, def, i) != piece) continue;
            sum = sum.add(boxes.get(i).getCenter());
            count++;
        }
        return count == 0 ? null : sum.scale(1.0 / count);
    }

    /** Tells whoever can see the bearer to let the piece fall, in whichever pose of the part is showing. */
    private static void letFall(LivingEntity bearer, String owner, int piece) {
        for (AttachmentDef def : WornAttachments.declared(bearer)) {
            if (!owner.equals(ownerOf(def.id())) || !HitShapes.active(bearer, def)) continue;
            BodyPartLostS2CPayload payload = new BodyPartLostS2CPayload(bearer.getId(), def.id(), piece);
            //? if neoforge {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(bearer, payload);
            //?} else {
            /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(bearer, payload);
            if (bearer instanceof ServerPlayer self) com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(self, payload);
            *///?}
        }
    }

    private static boolean severs(AttachmentBody.Part rules, DamageSource source) {
        if (rules.severTags().isEmpty()) return true;
        for (String tag : rules.severTags()) {
            if (tag.equals("melee")) {
                if (source.getEntity() != null && source.getDirectEntity() == source.getEntity()) return true;
                continue;
            }
            if (tag.equals("projectile")) {
                if (source.getDirectEntity() instanceof Projectile) return true;
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(tag);
            if (id != null && source.is(TagKey.create(Registries.DAMAGE_TYPE, id))) return true;
        }
        return false;
    }

    private static void drop(LivingEntity bearer, AttachmentBody.Part rules) {
        if (rules.drop().isEmpty()) return;
        ResourceLocation id = ResourceLocation.tryParse(rules.drop());
        if (id == null) return;
        Item item = BuiltInRegistries.ITEM.get(id);
        ItemEntity dropped = new ItemEntity(bearer.level(), bearer.getX(), bearer.getY() + 1.0, bearer.getZ(), new ItemStack(item));
        if (!rules.keepDrop()) {
            // Just something falling to the ground: nobody picks it up, and it soon goes.
            dropped.setNeverPickUp();
            dropped.lifespan = PROP_TICKS;
        }
        bearer.level().addFreshEntity(dropped);
    }

    private static void heal(LivingEntity entity, List<AttachmentDef> declared) {
        for (AttachmentDef def : declared) {
            AttachmentBody.Part rules = def.body().part();
            if (rules == null || !rules.sharesWith().isEmpty()) continue;
            CompoundTag stored = entity.getPersistentData().getCompound(STORAGE);
            if (stored.isEmpty()) return;
            int pieces = BodyPose.pieces(entity, def);
            for (int p = 0; p < pieces; p++) {
                String key = pieceKey(def.id(), p);
                if (!stored.contains(key)) continue;
                PartWounds.State before = read(entity, key, rules);
                if (before.stage() == AttachmentBody.Part.LOST) {
                    long regrowAt = stored.getCompound(key).getLong(REGROW_AT);
                    if (regrowAt > 0L && entity.level().getGameTime() >= regrowAt) {
                        write(entity, key, PartWounds.regrown(rules), 0L);
                        com.aetherianartificer.townstead.compat.mess.MessBridge.partRegrown(entity, def.id());
                    }
                    continue;
                }
                PartWounds.State after = PartWounds.heal(before, rules, rules.healPerSecond() / 20f);
                if (after.equals(before)) continue;
                if (after.stage() == AttachmentBody.Part.INTACT && after.health() >= rules.health()) {
                    CompoundTag parts = entity.getPersistentData().getCompound(STORAGE);
                    parts.remove(key);
                    entity.getPersistentData().put(STORAGE, parts);
                } else {
                    write(entity, key, after, 0L);
                }
                if (before.stage() == AttachmentBody.Part.WOUNDED && after.stage() == AttachmentBody.Part.INTACT) {
                    run(rules.onHealed(), entity, null);
                }
            }
        }
    }

    private static PartWounds.State read(LivingEntity entity, String id, AttachmentBody.Part rules) {
        CompoundTag parts = entity.getPersistentData().getCompound(STORAGE);
        if (!parts.contains(id)) return new PartWounds.State(rules.health(), AttachmentBody.Part.INTACT);
        CompoundTag part = parts.getCompound(id);
        return new PartWounds.State(part.getFloat(HEALTH), part.getInt(STAGE));
    }

    private static void write(LivingEntity entity, String id, PartWounds.State state, long regrowAt) {
        CompoundTag parts = entity.getPersistentData().getCompound(STORAGE);
        CompoundTag part = new CompoundTag();
        part.putFloat(HEALTH, state.health());
        part.putInt(STAGE, state.stage());
        part.putLong(REGROW_AT, regrowAt);
        parts.put(id, part);
        entity.getPersistentData().put(STORAGE, parts);
    }

    /** Heals or regrows a part now (a cure); an empty id means every part. */
    public static void restore(LivingEntity entity, String id) {
        CompoundTag parts = entity.getPersistentData().getCompound(STORAGE);
        com.aetherianartificer.townstead.compat.mess.MessBridge.partRegrown(entity, id.isEmpty() ? "" : ownerOf(id));
        if (id.isEmpty()) entity.getPersistentData().remove(STORAGE);
        else {
            String owner = ownerOf(id);
            parts.getAllKeys().removeIf(key -> key.equals(owner) || key.startsWith(owner + "#"));
            entity.getPersistentData().put(STORAGE, parts);
        }
    }

    private static void run(String json, LivingEntity bearer, LivingEntity other) {
        if (json.isEmpty()) return;
        Action action = ACTIONS.computeIfAbsent(json, BodyParts::parse);
        if (action != NOTHING) action.run(new ActionContext(bearer, other));
    }

    private static Action parse(String json) {
        try {
            Action action = Actions.parse(JsonParser.parseString(json));
            return action == null ? NOTHING : action;
        } catch (RuntimeException e) {
            Townstead.LOGGER.warn("Attachment part action could not be read: {}", json);
            return NOTHING;
        }
    }

    /** Forgets parsed actions, which may name things a reload replaced. */
    public static void onReload() {
        BodyPose.onReload();
        ACTIONS.clear();
        OWNERS.clear();
    }

    // --- Both sides --------------------------------------------------------------------------

    /** A client's copy of a part, under the id the server knows it by. */
    public static AttachmentPart clientPart(LivingEntity bearer, BodyPartsS2CPayload.Entry entry) {
        AttachmentPart part = new AttachmentPart(bearer, entry.attachment(), entry.box());
        part.setId(entry.partId());
        return part;
    }

    private static volatile int localPlayerId = -1;

    /** Set each client tick: you can't aim at your own parts. */
    public static void setLocalPlayer(int id) {
        localPlayerId = id;
    }

    static boolean pickable(AttachmentPart part) {
        LivingEntity bearer = part.getParent();
        if (bearer.isRemoved() || !part.showing) return false;
        return !bearer.level().isClientSide || bearer.getId() != localPlayerId;
    }
}
