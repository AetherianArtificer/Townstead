package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Where an attachment is, so that hitting it hits whoever wears it. The server decides, from the pose
 * {@link BodyPose} works out, and only when a projectile's path comes near. A client's crosshair aims at
 * what it actually drew, as a vanilla crosshair aims at what it sees, and the server has the last word on
 * whether a swing is in reach.
 *
 * <p>These boxes aren't part entities: every part entity costs every entity search in a level, and a
 * village wears a lot of tails. An attachment with a {@code part} has real part entities, which sit on
 * these same boxes.</p>
 */
public final class HitShapes {

    private static final int MAX_GROUPS = 8;
    // How far past a body its attachments can reach, in blocks, when looking for who might be hit.
    private static final double REACH = 4.0;
    // A chain swings around where it settles, so the server gives it this much room, in blocks.
    private static final double SWING_MARGIN = 0.08;
    private static final double REACH_SLACK = 0.5;
    private static final String SEGMENT_MARK = "__seg";

    private static final Map<String, Optional<Condition>> GATES = new ConcurrentHashMap<>();
    private static final Map<LivingEntity, Solved> SOLVED = Collections.synchronizedMap(new WeakHashMap<>());

    private static Function<LivingEntity, Map<String, float[]>> clientMeasured = entity -> null;

    private static final class Solved {
        final long tick;
        final Map<String, List<AABB>> byDef = new HashMap<>();

        Solved(long tick) {
            this.tick = tick;
        }
    }

    private HitShapes() {}

    /** Set once by the client's setup: what it measured while drawing, for its crosshair. */
    public static void setClientMeasured(Function<LivingEntity, Map<String, float[]>> measured) {
        clientMeasured = measured;
    }

    /** Where each box of what this person wears is now, from their feet, not counting attachments with parts. */
    public static List<AABB> placed(LivingEntity entity) {
        if (!(entity instanceof Player) && !(entity instanceof VillagerEntityMCA)) return List.of();
        List<AABB> out = null;
        for (AttachmentDef def : WornAttachments.of(entity)) {
            AttachmentBody.Part part = def.body().part();
            if (part != null && part.hasEntities()) continue;
            List<AABB> boxes = boxes(entity, def);
            if (boxes.isEmpty()) continue;
            if (out == null) out = new ArrayList<>();
            out.addAll(boxes);
        }
        return out == null ? List.of() : out;
    }

    /**
     * Where this attachment's boxes are now, from the feet. On a client, what it drew if it just drew this
     * body. On the server, the pose it worked out, once a tick at most.
     */
    public static List<AABB> boxes(LivingEntity entity, AttachmentDef def) {
        List<AABB> out = new ArrayList<>();
        for (AABB box : aligned(entity, def)) {
            if (box != null) out.add(box);
        }
        return out;
    }

    /** As {@link #boxes}, with a null for each box not showing, so a box's index still says which piece it's on. */
    public static List<AABB> aligned(LivingEntity entity, AttachmentDef def) {
        if (entity.level().isClientSide) {
            Map<String, float[]> measured = clientMeasured.apply(entity);
            if (measured != null) {
                float[] flat = measured.get(def.id());
                return flat == null ? List.of() : soften(entity, def, unflatten(flat, 0.0, true));
            }
            return soften(entity, def, unflatten(BodyPose.measure(entity, def), margin(def), true));
        }
        long tick = entity.level().getGameTime();
        Solved solved = SOLVED.get(entity);
        if (solved == null || solved.tick != tick) {
            solved = new Solved(tick);
            SOLVED.put(entity, solved);
        }
        return solved.byDef.computeIfAbsent(def.id(), id -> soften(entity, def, unflatten(BodyPose.measure(entity, def), margin(def), true)));
    }

    /** A soft part is hit where it is squashed to, not where its model would reach through a wall. */
    private static List<AABB> soften(LivingEntity entity, AttachmentDef def, List<AABB> boxes) {
        if (def.body().soft() == null || boxes.isEmpty()) return boxes;
        SoftBodies.Shape shape = SoftBodies.of(entity, def);
        if (shape == null) return boxes;
        List<AABB> out = new ArrayList<>(boxes.size());
        for (int i = 0; i < boxes.size(); i++) {
            AABB box = boxes.get(i);
            AABB lobe = shape.lobe(i);
            out.add(box == null ? null : lobe != null ? lobe : box);
        }
        return out;
    }

    /** The pose worked out from data, the one the server judges by. */
    public static List<AABB> solved(LivingEntity entity, AttachmentDef def) {
        return unflatten(BodyPose.measure(entity, def), margin(def), false);
    }

    private static double margin(AttachmentDef def) {
        return def.physics().isEmpty() ? 0.0 : SWING_MARGIN;
    }

    private static List<AABB> unflatten(float[] flat, double margin, boolean holes) {
        List<AABB> out = new ArrayList<>(flat.length / 6);
        for (int i = 0; i + 5 < flat.length; i += 6) {
            // A group with nothing showing measures inside out.
            if (flat[i] > flat[i + 3]) {
                if (holes) out.add(null);
                continue;
            }
            out.add(new AABB(flat[i], flat[i + 1], flat[i + 2], flat[i + 3], flat[i + 4], flat[i + 5]).inflate(margin));
        }
        return out;
    }

    /** A body's box grown to take in what it wears, with room for a crosshair aiming at a chain mid-swing. */
    public static AABB reachBounds(Entity target, AABB bounds) {
        if (!(target instanceof LivingEntity living)) return bounds;
        for (AABB box : placed(living)) bounds = bounds.minmax(box.move(living.position()).inflate(REACH_SLACK));
        return bounds;
    }

    /** How many boxes this attachment has, which is how many part entities it wants. */
    public static int count(AttachmentDef def, LivingEntity entity) {
        return BodyPose.boxCount(entity, def);
    }

    public static boolean active(LivingEntity entity, AttachmentDef def) {
        for (String name : def.hidesUnder()) {
            var slot = AttachmentDef.equipmentSlot(name);
            if (slot != null && !entity.getItemBySlot(slot).isEmpty()) return false;
        }
        if (def.whenJson().isEmpty()) return true;
        Optional<Condition> gate = GATES.computeIfAbsent(def.whenJson(), json -> {
            try {
                return Optional.ofNullable(Conditions.parse(JsonParser.parseString(json)));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
        return gate.map(condition -> condition.test(new ConditionContext(entity))).orElse(true);
    }

    /**
     * What a projectile moving from {@code start} to {@code end} really hits, given what vanilla found.
     * An attachment nearer than that makes its bearer the target, with the hit where the attachment is.
     * Your own shots go past your own attachments, as they do your parts. A shot that crosses one of a
     * person's real parts stays with that part, which has wounds to take it, even if another box is nearer.
     * The server decides these, and a client's copy of the arrow just follows.
     */
    @Nullable
    public static EntityHitResult intercept(Level level, Entity projectile, Vec3 start, Vec3 end,
                                            java.util.function.Predicate<Entity> filter, float inflate,
                                            @Nullable EntityHitResult found) {
        if (level.isClientSide) return found;
        Entity owner = projectile instanceof net.minecraft.world.entity.projectile.Projectile shot ? shot.getOwner() : null;
        return nearest(level, projectile, owner, start, end, filter, inflate, Double.MAX_VALUE, found);
    }

    /** What someone aiming from {@code start} to {@code end} has in their sights, as far as {@code reachSqr}. */
    @Nullable
    public static EntityHitResult aim(Entity aiming, Vec3 start, Vec3 end, java.util.function.Predicate<Entity> filter,
                                      double reachSqr, @Nullable EntityHitResult found) {
        return nearest(aiming.level(), aiming, null, start, end, filter, 0f, reachSqr, found);
    }

    @Nullable
    private static EntityHitResult nearest(Level level, Entity mover, @Nullable Entity owner, Vec3 start, Vec3 end,
                                           java.util.function.Predicate<Entity> filter, float inflate, double limitSqr,
                                           @Nullable EntityHitResult found) {
        Entity struck = found != null && found.getEntity() instanceof AttachmentPart part ? part.getParent() : null;
        double closest = found == null ? limitSqr : start.distanceToSqr(found.getLocation());
        EntityHitResult best = found;
        AABB path = new AABB(start, end).inflate(REACH);
        for (LivingEntity bearer : level.getEntitiesOfClass(LivingEntity.class, path,
                e -> (e instanceof Player || e instanceof VillagerEntityMCA) && e != mover && e != owner && e != struck)) {
            if (!filter.test(bearer)) continue;
            for (AABB box : placed(bearer)) {
                Vec3 at = box.move(bearer.position()).inflate(inflate).clip(start, end).orElse(null);
                if (at == null) continue;
                double distance = start.distanceToSqr(at);
                if (distance >= closest) continue;
                closest = distance;
                best = new EntityHitResult(bearer, at);
            }
        }
        return best;
    }

    /**
     * The bones that start each separate piece of a model. Bones with nothing of their own that lead to
     * one place are only holding the rest up. Where they branch, each branch is a piece (each wing, each
     * tusk), and a bone with cubes holds all it carries together. Empty when there are no bones.
     */
    public static List<String> pieceRoots(JsonObject geometry) {
        JsonArray bones = bonesOf(geometry);
        if (bones == null) return List.of();
        Map<String, List<String>> children = new LinkedHashMap<>();
        Map<String, Boolean> cubes = new LinkedHashMap<>();
        List<String> level = new ArrayList<>();
        for (JsonElement bone : bones) {
            JsonObject json = bone.getAsJsonObject();
            String name = json.get("name").getAsString();
            JsonArray own = json.getAsJsonArray("cubes");
            cubes.put(name, own != null && !own.isEmpty());
            if (json.has("parent")) children.computeIfAbsent(json.get("parent").getAsString(), k -> new ArrayList<>()).add(name);
            else level.add(name);
        }
        level = carrying(level, children, cubes);
        while (level.size() == 1 && !cubes.getOrDefault(level.get(0), false)) {
            level = carrying(children.getOrDefault(level.get(0), List.of()), children, cubes);
        }
        return level;
    }

    private static List<String> carrying(List<String> names, Map<String, List<String>> children, Map<String, Boolean> cubes) {
        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (carries(name, children, cubes)) out.add(name);
        }
        return out;
    }

    private static boolean carries(String name, Map<String, List<String>> children, Map<String, Boolean> cubes) {
        if (cubes.getOrDefault(name, false)) return true;
        for (String child : children.getOrDefault(name, List.of())) {
            if (carries(child, children, cubes)) return true;
        }
        return false;
    }

    /** Which piece each box is on, by the first bone in it. */
    public static int[] groupPieces(JsonObject geometry, Map<String, Integer> groups, List<String> roots) {
        int count = 0;
        for (int group : groups.values()) count = Math.max(count, group + 1);
        int[] out = new int[count];
        java.util.Arrays.fill(out, -1);
        Map<String, String> parents = new LinkedHashMap<>();
        JsonArray bones = bonesOf(geometry);
        if (bones != null) {
            for (JsonElement bone : bones) {
                JsonObject json = bone.getAsJsonObject();
                if (json.has("parent")) parents.put(json.get("name").getAsString(), json.get("parent").getAsString());
            }
        }
        for (Map.Entry<String, Integer> entry : groups.entrySet()) {
            if (out[entry.getValue()] >= 0) continue;
            String at = entry.getKey();
            int piece = roots.indexOf(at);
            for (int guard = 0; piece < 0 && parents.containsKey(at) && guard <= parents.size(); guard++) {
                at = parents.get(at);
                piece = roots.indexOf(at);
            }
            out[entry.getValue()] = Math.max(piece, 0);
        }
        for (int i = 0; i < count; i++) out[i] = Math.max(out[i], 0);
        return out;
    }

    /**
     * Which box each bone with cubes belongs to, in file order. Every bone gets its own until there are
     * too many, then neighbours share. Both sides work this out alike, so a box means the same on each.
     */
    public static Map<String, Integer> groups(JsonObject geometry) {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<String> withCubes = new ArrayList<>();
        JsonArray bones = bonesOf(geometry);
        if (bones == null) return out;
        for (JsonElement bone : bones) {
            JsonObject json = bone.getAsJsonObject();
            JsonArray cubes = json.getAsJsonArray("cubes");
            if (cubes != null && !cubes.isEmpty()) withCubes.add(json.get("name").getAsString());
        }
        int total = withCubes.size();
        for (int i = 0; i < total; i++) {
            out.put(withCubes.get(i), total <= MAX_GROUPS ? i : i * MAX_GROUPS / total);
        }
        return out;
    }

    /** A bone's group. A chain's generated slices of a bone stay with the bone they were cut from. */
    public static int groupOf(Map<String, Integer> groups, String bone) {
        int mark = bone.indexOf(SEGMENT_MARK);
        return groups.getOrDefault(mark < 0 ? bone : bone.substring(0, mark), 0);
    }

    @Nullable
    static JsonArray bonesOf(JsonObject geometry) {
        JsonArray geometries = geometry.getAsJsonArray("minecraft:geometry");
        return geometries == null || geometries.isEmpty() ? null : geometries.get(0).getAsJsonObject().getAsJsonArray("bones");
    }
}
