package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo;
import com.aetherianartificer.townstead.root.attachment.AttachmentBody;
import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.aetherianartificer.townstead.root.attachment.SoftBodies;
import com.aetherianartificer.townstead.root.attachment.SoftLattice;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Runs a {@link SoftLattice} for each soft part drawn, and hands its bend to {@link AttachmentGeo} for the draw.
 * The full grid within {@link #NEAR} blocks of the camera, for up to {@link #BUDGET} parts a frame; a coarse one
 * out to {@link #FAR}; past that, or in a menu, the part draws as posed.
 */
public final class SoftLook {

    private static final float NEAR = 16f;
    private static final float FAR = 48f;
    private static final int BUDGET = 50;
    private static final float GAP_SECONDS = 0.3f;
    private static final float TELEPORT = 8f;
    private static final int MAX_SOLIDS = 320;
    private static final long EVICT_AFTER_MS = 10_000L;

    /** The blocks around one piece, gathered once a tick, from where the body was then. */
    private static final class Blocks {
        long tick = Long.MIN_VALUE;
        double x, y, z;
        final float[] solids = new float[MAX_SOLIDS * 6];
        final float[] bounds = new float[6];
        int count;
    }

    private static final class State {
        SoftLattice[] lattices;
        Blocks[] blocks;
        float[][] lastRest;
        float[] radii;
        long lastNanos;
        long lastTouchMs;
        double x, y, z;
    }

    private static final Map<Integer, Map<String, State>> STATES = new HashMap<>();
    private static final Map<AttachmentGeo, Map<String, Set<String>>> COVERED = new WeakHashMap<>();
    private static long frameKey = Long.MIN_VALUE;
    private static int fullThisFrame;

    // Render thread scratch.
    private static final Matrix4f FRAME = new Matrix4f();
    private static final float[] SOLIDS_NOW = new float[MAX_SOLIDS * 6];

    private SoftLook() {}

    public static void onManifest() {
        STATES.clear();
        COVERED.clear();
    }

    /** One jointed piece of a soft part this frame: its grid, where it rests and how it's carried. */
    private record Piece(String start, Set<String> bones, float[] rest, SoftLattice lattice, Matrix4f carry,
                         org.joml.Quaternionf turn) {}

    /**
     * Steps the soft part's grids for this draw and returns their bend, or null to draw the part as posed.
     * {@code attachFrame} is the pose the geometry renders under, with every bone already posed and scaled.
     * Each piece (the part's own bone, then each of its {@code segments}) hangs from its bone's pivot: the first
     * from the body, each next one from where the piece before it has swung it.
     */
    @Nullable
    public static AttachmentGeo.Deformer prepare(LivingEntity entity, AttachmentDef def, int anchorIndex, AttachmentGeo geo,
                                                 Matrix4f attachFrame, float partialTick) {
        return prepare(entity, def, anchorIndex, geo, attachFrame, partialTick, java.util.List.of());
    }

    /** As above, kept out of the bearer's own {@code bodies} as well. */
    @Nullable
    public static AttachmentGeo.Deformer prepare(LivingEntity entity, AttachmentDef def, int anchorIndex, AttachmentGeo geo,
                                                 Matrix4f attachFrame, float partialTick,
                                                 java.util.List<com.aetherianartificer.townstead.root.attachment.ChainSim.Capsule> bodies) {
        AttachmentBody.Soft soft = def.body().soft();
        if (soft == null || geo == null) return null;
        if (AttachmentPhysics.quality() == PhysicsQuality.OFF) return null;
        Matrix4f root = EntityRootFrames.rootFor(entity);
        if (root == null) return null;
        if (root.getScale(new Vector3f()).x > 1.5f) return null;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distance = Math.sqrt(entity.distanceToSqr(camera));
        if (distance > FAR) return null;
        long key = entity.level().getGameTime() * 1000L + (long) (partialTick * 1000f);
        if (key != frameKey) {
            frameKey = key;
            fullThisFrame = 0;
        }
        boolean full = distance <= NEAR && AttachmentPhysics.quality() == PhysicsQuality.FULL && fullThisFrame < BUDGET;
        if (full) fullThisFrame++;

        FRAME.set(root).invert().mul(attachFrame);
        Matrix4f frame = new Matrix4f(FRAME);
        Matrix4f back = new Matrix4f(FRAME).invert();

        java.util.List<String> starts = new java.util.ArrayList<>();
        starts.add(soft.bone());
        for (String segment : soft.segments()) {
            if (geo.bone(segment) != null) starts.add(segment);
        }
        State state = STATES.computeIfAbsent(entity.getId(), k -> new HashMap<>())
                .computeIfAbsent(def.id() + "#" + anchorIndex, k -> new State());
        state.lastTouchMs = Util.getMillis();
        if (STATES.size() > 256) evictStale();
        if (state.lattices == null || state.lattices.length != starts.size()) {
            state.lattices = new SoftLattice[starts.size()];
            state.blocks = new Blocks[starts.size()];
            for (int b = 0; b < state.blocks.length; b++) state.blocks[b] = new Blocks();
            state.lastRest = new float[starts.size()][];
            state.radii = new float[starts.size()];
        }

        long now = System.nanoTime();
        float frameDt = state.lastNanos == 0L ? 0f : (now - state.lastNanos) / 1e9f;
        state.lastNanos = now;
        double ex = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double ey = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double ez = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        float dx = (float) (ex - state.x), dy = (float) (ey - state.y), dz = (float) (ez - state.z);
        state.x = ex;
        state.y = ey;
        state.z = ez;
        boolean reset = frameDt <= 0f || frameDt > GAP_SECONDS
                || Math.abs(dx) > TELEPORT || Math.abs(dy) > TELEPORT || Math.abs(dz) > TELEPORT;

        SoftLattice.Feel feel = SoftLattice.Feel.of(soft.squish());
        float[] envelope = envelope(entity, def);
        java.util.List<Piece> pieces = new java.util.ArrayList<>();
        Matrix4f carry = new Matrix4f();
        org.joml.Quaternionf carried = new org.joml.Quaternionf();
        int solids;
        for (int p = 0; p < starts.size(); p++) {
            String start = starts.get(p);
            Set<String> bones = piece(geo, start, starts.subList(p + 1, starts.size()));
            float[] rest = bounds(geo, bones);
            if (rest == null) continue;
            float sx = rest[3] - rest[0], sy = rest[4] - rest[1], sz = rest[5] - rest[2];
            SoftLattice lattice = state.lattices[p];
            // A grid keeps its layout once chosen: picked again each frame, a part about as long as it's wide
            // flipped between layouts as it swelled, and every flip threw the grid away mid-swing.
            boolean coarse = lattice != null && lattice.count == 8;
            if (lattice == null || coarse == full) {
                int[] dims = full ? dims(frame, sx, sy, sz) : new int[]{2, 2, 2};
                lattice = state.lattices[p] = new SoftLattice(dims[0], dims[1], dims[2]);
            }
            Vector3f[] goals = new Vector3f[lattice.count];
            for (int k = 0; k < lattice.nz; k++) {
                for (int j = 0; j < lattice.ny; j++) {
                    for (int i = 0; i < lattice.nx; i++) {
                        Vector3f g = new Vector3f(rest[0] + sx * i / (lattice.nx - 1), rest[1] + sy * j / (lattice.ny - 1),
                                rest[2] + sz * k / (lattice.nz - 1));
                        carry.transformPosition(frame.transformPosition(g));
                        goals[lattice.index(i, j, k)] = g;
                    }
                }
            }
            Vector3f hinge = carry.transformPosition(frame.transformPosition(pivot(geo, start, rest)));
            if (reset) lattice.ready = false;
            float[] before = state.lastRest[p];
            if (!reset && before != null && !java.util.Arrays.equals(before, rest)) {
                // It grew or shrank: the grid comes along with it rather than being yanked there.
                lattice.reshape(grid(lattice, before, frame, carry), goals);
            }
            state.lastRest[p] = rest.clone();
            lattice.setGoal(goals, hinge, feel.droop());
            if (!reset) lattice.shift(-dx, -dy, -dz);
            solids = 0;
            if (full) {
                // Each piece looks for blocks around itself: a tip reaching past its sac needs its own.
                Blocks near = state.blocks[p];
                long tick = entity.level().getGameTime();
                if (tick != near.tick) {
                    near.tick = tick;
                    gather(entity, lattice, ex, ey, ez, near);
                }
                float ox = (float) (near.x - ex), oy = (float) (near.y - ey), oz = (float) (near.z - ez);
                for (int i = 0; i < near.count; i++) {
                    int b = i * 6;
                    SOLIDS_NOW[b] = near.solids[b] + ox;
                    SOLIDS_NOW[b + 1] = near.solids[b + 1] + oy;
                    SOLIDS_NOW[b + 2] = near.solids[b + 2] + oz;
                    SOLIDS_NOW[b + 3] = near.solids[b + 3] + ox;
                    SOLIDS_NOW[b + 4] = near.solids[b + 4] + oy;
                    SOLIDS_NOW[b + 5] = near.solids[b + 5] + oz;
                }
                solids = near.count;
                lattice.known = new float[]{near.bounds[0] + ox, near.bounds[1] + oy, near.bounds[2] + oz,
                        near.bounds[3] + ox, near.bounds[4] + oy, near.bounds[5] + oz};
            } else {
                lattice.known = null;
            }
            float cell = Math.min(Math.min(span(frame, sx, 0) / (lattice.nx - 1), span(frame, sy, 1) / (lattice.ny - 1)),
                    span(frame, sz, 2) / (lattice.nz - 1));
            float radius = Mth.clamp(cell * 0.5f, 0.08f, 0.6f);
            state.radii[p] = radius;
            lattice.step(frameDt, feel, radius, SOLIDS_NOW, solids, envelope, envelope.length / 6, bodies);

            org.joml.Quaternionf own = lattice.rotation(new org.joml.Quaternionf());
            pieces.add(new Piece(start, bones, rest, lattice, new Matrix4f(carry), new org.joml.Quaternionf(own).mul(carried)));
            // What comes next is carried by how this piece turned about its hinge.
            Matrix4f turnHere = new Matrix4f().translate(hinge).rotate(own).translate(-hinge.x, -hinge.y, -hinge.z);
            carry = turnHere.mul(carry);
            carried = new org.joml.Quaternionf(own).mul(carried);
        }
        if (pieces.isEmpty()) return null;

        java.util.List<float[]> plates = soft.plates();
        java.util.Map<String, AttachmentGeo.Deformer> byStart = new HashMap<>();
        for (Piece piece : pieces) byStart.put(piece.start(), bender(piece, frame, back, plates));
        AttachmentGeo.Deformer first = byStart.get(pieces.get(0).start());
        boolean whole = soft.bone().isEmpty();
        return new AttachmentGeo.Deformer() {
            @Override
            public AttachmentGeo.Deformer at(String bone) {
                AttachmentGeo.Deformer own = byStart.get(bone);
                if (own != null) return own;
                if (whole) {
                    AttachmentGeo.Bone found = geo.bone(bone);
                    if (found != null && found.parent() == null) return first;
                }
                return null;
            }

            @Override
            public void deform(Vector3f point) {
            }
        };
    }

    /** A grid of goal points over a rest box, carried into the body's space. */
    private static Vector3f[] grid(SoftLattice lattice, float[] rest, Matrix4f frame, Matrix4f carry) {
        float sx = rest[3] - rest[0], sy = rest[4] - rest[1], sz = rest[5] - rest[2];
        Vector3f[] out = new Vector3f[lattice.count];
        for (int k = 0; k < lattice.nz; k++) {
            for (int j = 0; j < lattice.ny; j++) {
                for (int i = 0; i < lattice.nx; i++) {
                    Vector3f g = new Vector3f(rest[0] + sx * i / (lattice.nx - 1), rest[1] + sy * j / (lattice.ny - 1),
                            rest[2] + sz * k / (lattice.nz - 1));
                    carry.transformPosition(frame.transformPosition(g));
                    out[lattice.index(i, j, k)] = g;
                }
            }
        }
        return out;
    }

    /**
     * The soft parts this body wears, as spheres where their particles last were, for chains (ears, hair, tails)
     * to rest against. From the body's feet, world axes, as chain colliders are.
     */
    public static java.util.List<com.aetherianartificer.townstead.root.attachment.ChainSim.Capsule> capsules(LivingEntity entity) {
        Map<String, State> byDef = STATES.get(entity.getId());
        if (byDef == null) return java.util.List.of();
        java.util.List<com.aetherianartificer.townstead.root.attachment.ChainSim.Capsule> out = new java.util.ArrayList<>();
        for (State state : byDef.values()) {
            if (state.lattices == null) continue;
            for (int p = 0; p < state.lattices.length; p++) {
                SoftLattice lattice = state.lattices[p];
                if (lattice == null || !lattice.ready) continue;
                float radius = state.radii[p];
                for (int i = 0; i < lattice.count; i++) {
                    Vector3f at = new Vector3f(lattice.pos[i]);
                    out.add(new com.aetherianartificer.townstead.root.attachment.ChainSim.Capsule("soft", at, at, radius));
                }
            }
        }
        return out;
    }

    /** Bends one piece: the membrane follows its grid, a plate turns with the whole piece about its centre. */
    private static AttachmentGeo.Deformer bender(Piece piece, Matrix4f frame, Matrix4f back, java.util.List<float[]> plates) {
        float[] bounds = piece.rest();
        SoftLattice lattice = piece.lattice();
        Matrix4f carry = piece.carry();
        org.joml.Quaternionf turn = piece.turn();
        return new AttachmentGeo.Deformer() {
            private final Vector3f entityPoint = new Vector3f(), moved = new Vector3f(), centre = new Vector3f(),
                    arm = new Vector3f();

            @Override
            public AttachmentGeo.Deformer at(String bone) {
                return null;
            }

            @Override
            public int plate(float u, float v) {
                for (int i = 0; i < plates.size(); i++) {
                    float[] area = plates.get(i);
                    if (u >= area[0] && u <= area[2] && v >= area[1] && v <= area[3]) return i + 1;
                }
                return 0;
            }

            @Override
            public int detail() {
                return plates.isEmpty() ? 4 : 8;
            }

            @Override
            public void deform(Vector3f point) {
                float u = (point.x - bounds[0]) / Math.max(1e-4f, bounds[3] - bounds[0]);
                float v = (point.y - bounds[1]) / Math.max(1e-4f, bounds[4] - bounds[1]);
                float w = (point.z - bounds[2]) / Math.max(1e-4f, bounds[5] - bounds[2]);
                lattice.displacement(u, v, w, moved);
                carry.transformPosition(frame.transformPosition(entityPoint.set(point))).add(moved);
                back.transformPosition(entityPoint, point);
            }

            @Override
            public void deformPlate(Vector3f point, Vector3f rest) {
                frame.transformPosition(arm.set(point));
                frame.transformPosition(centre.set(rest));
                arm.sub(centre);
                turn.transform(arm);
                deform(centre.set(rest));
                frame.transformPosition(centre);
                back.transformPosition(centre.add(arm), point);
            }
        };
    }

    /** The bones of one piece: its start and everything under it, short of where a later piece starts. */
    private static Set<String> piece(AttachmentGeo geo, String start, java.util.List<String> later) {
        Set<String> out = new HashSet<>(covered(geo, start));
        for (String next : later) {
            if (out.contains(next)) out.removeAll(covered(geo, next));
        }
        return out;
    }

    /** The box around a piece's bones as posed, in the space the geometry draws in; null if it shows none. */
    @Nullable
    private static float[] bounds(AttachmentGeo geo, Set<String> bones) {
        float[] rest = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        geo.measure(new Matrix4f(), (bone, x0, y0, z0, x1, y1, z1) -> {
            if (!bones.contains(bone)) return;
            rest[0] = Math.min(rest[0], x0);
            rest[1] = Math.min(rest[1], y0);
            rest[2] = Math.min(rest[2], z0);
            rest[3] = Math.max(rest[3], x1);
            rest[4] = Math.max(rest[4], y1);
            rest[5] = Math.max(rest[5], z1);
        });
        if (rest[0] > rest[3]) return null;
        for (int a = 0; a < 3; a++) {
            rest[a] -= 0.02f;
            rest[a + 3] += 0.02f;
        }
        return rest;
    }

    /** Where a bone's joint is as posed, in the space the geometry draws in; the middle of the part if unnamed. */
    private static Vector3f pivot(AttachmentGeo geo, String name, float[] rest) {
        AttachmentGeo.Bone bone = name.isEmpty() ? null : geo.bone(name);
        if (bone == null) {
            return new Vector3f((rest[0] + rest[3]) * 0.5f, (rest[1] + rest[4]) * 0.5f, (rest[2] + rest[5]) * 0.5f);
        }
        java.util.List<AttachmentGeo.Bone> above = new java.util.ArrayList<>();
        for (AttachmentGeo.Bone at = bone.parent(); at != null; at = at.parent()) above.add(0, at);
        Matrix4f m = new Matrix4f();
        org.joml.Quaternionf q = new org.joml.Quaternionf();
        for (AttachmentGeo.Bone at : above) {
            at.applyLocal(m, at.physicsRotation != null ? at.physicsRotation : at.poseRotation(q));
        }
        return m.transformPosition(bone.pivot(new Vector3f()));
    }

    /** Grid points per axis: the longest of the part's three sides gets four. */
    private static int[] dims(Matrix4f frame, float sx, float sy, float sz) {
        float ax = span(frame, sx, 0), ay = span(frame, sy, 1), az = span(frame, sz, 2);
        int[] dims = {3, 3, 3};
        if (ax >= ay && ax >= az) dims[0] = 4;
        else if (ay >= az) dims[1] = 4;
        else dims[2] = 4;
        return dims;
    }

    /** How long one side of the part is in the world, in blocks. */
    private static float span(Matrix4f frame, float length, int axis) {
        Vector3f d = new Vector3f(axis == 0 ? length : 0f, axis == 1 ? length : 0f, axis == 2 ? length : 0f);
        return frame.transformDirection(d).length();
    }

    private static Set<String> covered(AttachmentGeo geo, String bone) {
        return COVERED.computeIfAbsent(geo, g -> new HashMap<>()).computeIfAbsent(bone, b -> {
            Set<String> out = new HashSet<>();
            if (b.isEmpty()) {
                out.addAll(geo.boneNames());
            } else {
                AttachmentGeo.Bone start = geo.bone(b);
                if (start != null) collect(start, out);
            }
            return out;
        });
    }

    private static void collect(AttachmentGeo.Bone bone, Set<String> out) {
        out.add(bone.name);
        for (AttachmentGeo.Bone child : bone.children()) collect(child, out);
    }

    private static float[] envelope(LivingEntity entity, AttachmentDef def) {
        SoftBodies.Shape shape = SoftBodies.of(entity, def);
        if (shape == null) return new float[0];
        int n = 0;
        for (boolean showing : shape.showing()) if (showing) n++;
        float[] out = new float[n * 6];
        int at = 0;
        for (int i = 0; i < shape.showing().length; i++) {
            AABB lobe = shape.lobe(i);
            if (lobe == null) continue;
            out[at++] = (float) lobe.minX;
            out[at++] = (float) lobe.minY;
            out[at++] = (float) lobe.minZ;
            out[at++] = (float) lobe.maxX;
            out[at++] = (float) lobe.maxY;
            out[at++] = (float) lobe.maxZ;
        }
        return out;
    }

    private static void gather(LivingEntity entity, SoftLattice lattice, double ex, double ey, double ez, Blocks into) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < lattice.count; i++) {
            Vector3f g = lattice.goal[i];
            minX = Math.min(minX, g.x);
            minY = Math.min(minY, g.y);
            minZ = Math.min(minZ, g.z);
            maxX = Math.max(maxX, g.x);
            maxY = Math.max(maxY, g.y);
            maxZ = Math.max(maxZ, g.z);
        }
        AABB around = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(1.5).move(ex, ey, ez);
        into.bounds[0] = (float) (around.minX - ex);
        into.bounds[1] = (float) (around.minY - ey);
        into.bounds[2] = (float) (around.minZ - ez);
        into.bounds[3] = (float) (around.maxX - ex);
        into.bounds[4] = (float) (around.maxY - ey);
        into.bounds[5] = (float) (around.maxZ - ez);
        int count = 0;
        for (VoxelShape shape : entity.level().getBlockCollisions(entity, around)) {
            for (AABB box : shape.toAabbs()) {
                if (count == MAX_SOLIDS) break;
                int b = count++ * 6;
                into.solids[b] = (float) (box.minX - ex);
                into.solids[b + 1] = (float) (box.minY - ey);
                into.solids[b + 2] = (float) (box.minZ - ez);
                into.solids[b + 3] = (float) (box.maxX - ex);
                into.solids[b + 4] = (float) (box.maxY - ey);
                into.solids[b + 5] = (float) (box.maxZ - ez);
            }
        }
        into.count = count;
        into.x = ex;
        into.y = ey;
        into.z = ez;
    }

    private static void evictStale() {
        long now = Util.getMillis();
        STATES.values().removeIf(byDef -> {
            byDef.values().removeIf(state -> now - state.lastTouchMs > EVICT_AFTER_MS);
            return byDef.isEmpty();
        });
    }
}
