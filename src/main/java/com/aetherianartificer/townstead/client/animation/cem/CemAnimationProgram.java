package com.aetherianartificer.townstead.client.animation.cem;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.AnimationSourceContext;
import com.aetherianartificer.townstead.client.animation.AnimationTransform;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CemAnimationProgram {
    private final List<CemAssignment> assignments;
    private final CemVariableStore.Layout layout;
    private final CemInputPlan<CemEvaluationContext<?>> inputs;
    private final Map<UUID, EntityVariables> entityVariables = new HashMap<>();
    private long lastVariablePruneTick;

    private CemAnimationProgram(List<CemAssignment> assignments, CemVariableStore.Layout layout) {
        this.layout = layout;
        this.assignments = assignments;
        this.inputs = CemEvaluationContext.createInputPlan(layout);
    }

    public static Optional<CemAnimationProgram> load(ResourceLocation entryPoint) {
        try {
            List<CemAssignment> assignments = new ArrayList<>();
            Set<ResourceLocation> visited = new HashSet<>();
            CemVariableStore.Layout layout = new CemVariableStore.Layout();
            loadResource(entryPoint, assignments, visited, layout);
            if (assignments.isEmpty()) return Optional.empty();
            Townstead.LOGGER.info(
                    "[AnimationBridge] loaded CEM program location={} assignments={} targets={}",
                    entryPoint,
                    assignments.size(),
                    targetSummary(assignments));
            return Optional.of(new CemAnimationProgram(assignments, layout));
        } catch (Exception e) {
            Townstead.LOGGER.warn("[AnimationBridge] failed to load CEM program location={}", entryPoint, e);
            return Optional.empty();
        }
    }

    public <T extends LivingEntity> List<AnimationTransform> evaluate(AnimationSourceContext<T> source) {
        T entity = source.entity();
        long gameTime = entity.level().getGameTime();
        pruneEntityVariables(gameTime);
        EntityVariables entityState = entityVariables.computeIfAbsent(entity.getUUID(), ignored -> new EntityVariables(layout));
        entityState.lastSeenTick = gameTime;
        long nowMillis = Util.getMillis();
        double frameTime = entityState.lastEvalMillis == 0L
                ? 1.0D / 20.0D
                : Math.min(0.25D, Math.max(0.0D, (nowMillis - entityState.lastEvalMillis) / 1000.0D));
        entityState.lastEvalMillis = nowMillis;
        entityState.frameCounter++;
        float partialTick = (float) Mth.clamp(source.animationProgress() - entity.tickCount, 0.0D, 1.0D);
        CemVariableStore variables = entityState.variables;
        variables.clearAssignments();
        CemEvaluationContext<T> context = new CemEvaluationContext<>(source, variables, frameTime, partialTick, entityState.frameCounter, inputs);
        for (CemAssignment assignment : assignments) {
            double value = assignment.expression().evaluate(context);
            if (Double.isFinite(value)) {
                context.assign(assignment.slot(), value);
            }
        }
        return context.transforms();
    }

    private void pruneEntityVariables(long gameTime) {
        if (gameTime - lastVariablePruneTick < 600L) return;
        lastVariablePruneTick = gameTime;
        entityVariables.entrySet().removeIf(entry -> gameTime - entry.getValue().lastSeenTick > 2400L);
    }

    private static void loadResource(
            ResourceLocation location,
            List<CemAssignment> assignments,
            Set<ResourceLocation> visited,
            CemVariableStore.Layout layout
    ) throws Exception {
        if (!visited.add(location)) return;
        Minecraft client = Minecraft.getInstance();
        Optional<Resource> resource = client.getResourceManager().getResource(location);
        if (resource.isEmpty()) return;

        JsonObject root;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8))) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }

        JsonArray models = root.has("models") && root.get("models").isJsonArray()
                ? root.getAsJsonArray("models")
                : null;
        if (models != null) {
            for (JsonElement element : models) {
                if (!element.isJsonObject()) continue;
                JsonObject model = element.getAsJsonObject();
                if (model.has("model")) {
                    String child = model.get("model").getAsString();
                    loadResource(sibling(location, child), assignments, visited, layout);
                }
                readAnimations(model, assignments, layout);
            }
        }

        readAnimations(root, assignments, layout);
    }

    private static ResourceLocation sibling(ResourceLocation base, String child) {
        String path = base.getPath();
        int slash = path.lastIndexOf('/');
        String prefix = slash >= 0 ? path.substring(0, slash + 1) : "";
        //? if neoforge {
        return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), prefix + child);
        //?} else {
        /*return new ResourceLocation(base.getNamespace(), prefix + child);
        *///?}
    }

    private static void readAnimations(JsonObject object, List<CemAssignment> assignments, CemVariableStore.Layout layout) {
        if (!object.has("animations") || !object.get("animations").isJsonArray()) return;
        for (JsonElement animationElement : object.getAsJsonArray("animations")) {
            if (!animationElement.isJsonObject()) continue;
            JsonObject animation = animationElement.getAsJsonObject();
            for (String key : animation.keySet()) {
                String expression = animation.get(key).getAsString();
                try {
                    assignments.add(new CemAssignment(key, layout.slot(key), CemExpressionParser.parse(expression, layout)));
                } catch (RuntimeException e) {
                    Townstead.LOGGER.debug(
                            "[AnimationBridge] skipped unsupported CEM expression target={} expr={}",
                            key,
                            expression);
                }
            }
        }
    }

    private static String targetSummary(List<CemAssignment> assignments) {
        Set<String> targets = new HashSet<>();
        for (CemAssignment assignment : assignments) {
            String target = assignment.target();
            int dot = target.indexOf('.');
            targets.add((dot > 0 ? target.substring(0, dot) : target).toLowerCase(Locale.ROOT));
        }
        return targets.stream().sorted().toList().toString();
    }

    private record CemAssignment(String target, int slot, CemExpression expression) {}

    private static final class EntityVariables {
        private final CemVariableStore variables;

        private EntityVariables(CemVariableStore.Layout layout) {
            variables = new CemVariableStore(layout);
        }
        private long lastSeenTick;
        private long lastEvalMillis;
        private long frameCounter;
    }

    static final class CemEvaluationContext<T extends LivingEntity> {
        private final AnimationSourceContext<T> source;
        private final CemVariableStore variables;
        private final double frameTime;
        private final float partialTick;
        private final long frameCounter;
        private final Map<String, NbtValue> nbtValues = new HashMap<>();
        private CompoundTag savedNbt;
        private boolean savedNbtLoaded;

        private NbtValue nbtValue(String path) {
            return nbtValues.computeIfAbsent(path, key -> liveNbtValue(key, source.entity())
                    .orElseGet(() -> {
                        // All queries in this evaluation share one lazy snapshot. Never retain
                        // it across frames: equipment and sleeping state can change immediately.
                        if (!savedNbtLoaded) {
                            savedNbtLoaded = true;
                            try {
                                CompoundTag snapshot = new CompoundTag();
                                source.entity().saveWithoutId(snapshot);
                                savedNbt = snapshot;
                            } catch (RuntimeException ignored) {
                                savedNbt = null;
                            }
                        }
                        if (savedNbt == null) return NbtValue.MISSING;
                        Tag tag = findNbtPath(savedNbt, key);
                        return tag == null ? NbtValue.MISSING : NbtValue.ofTag(tag);
                    }));
        }

        CemEvaluationContext(AnimationSourceContext<T> source, CemVariableStore variables, double frameTime, float partialTick, long frameCounter,
                             CemInputPlan<CemEvaluationContext<?>> inputs) {
            this.source = source;
            this.variables = variables;
            this.frameTime = frameTime;
            this.partialTick = partialTick;
            this.frameCounter = frameCounter;
            inputs.seed(this, variables);
        }

        double value(String key) {
            return variables.get(key);
        }

        double value(int slot) {
            return variables.get(slot);
        }

        void assign(int slot, double value) {
            variables.set(slot, value);
        }

        List<AnimationTransform> transforms() {
            List<AnimationTransform> transforms = new ArrayList<>();
            collectTransform(transforms, "head");
            collectTransform(transforms, "body");
            collectTransform(transforms, "right_arm");
            collectTransform(transforms, "left_arm");
            collectTransform(transforms, "right_leg");
            collectTransform(transforms, "left_leg");
            return transforms;
        }

        private static CemInputPlan<CemEvaluationContext<?>> createInputPlan(CemVariableStore.Layout layout) {
            var inputs = new CemInputPlan.Builder<CemEvaluationContext<?>>(layout);
            inputs.add("age", context -> context.source.animationProgress());
            inputs.add("time", context -> context.source.animationProgress());
            inputs.add("frame_time", context -> context.frameTime);
            inputs.add("frame_counter", context -> context.frameCounter);
            inputs.add("limb_swing", context -> context.source.parameters().limbAngle());
            inputs.add("limb_speed", context -> context.source.parameters().limbDistance());
            inputs.add("head_yaw", context -> context.source.parameters().headYaw());
            inputs.add("head_pitch", context -> context.source.headPitch());
            inputs.add("move_forward", context -> context.movement().forward());
            inputs.add("move_strafing", context -> context.movement().strafing());
            inputs.add("rot_x", context -> Math.toRadians(context.source.headPitch()));
            inputs.add("rot_y", context -> Math.toRadians(context.source.parameters().headYaw()));
            inputs.add("player_rot_x", context -> Math.toRadians(context.source.headPitch()));
            inputs.add("player_rot_y", context -> Math.toRadians(context.source.parameters().headYaw()));
            inputs.add("pos_x", context -> Mth.lerp(context.partialTick, context.source.entity().xOld, context.source.entity().getX()));
            inputs.add("pos_y", context -> Mth.lerp(context.partialTick, context.source.entity().yOld, context.source.entity().getY()));
            inputs.add("pos_z", context -> Mth.lerp(context.partialTick, context.source.entity().zOld, context.source.entity().getZ()));
            inputs.add("player_pos_x", context -> Mth.lerp(context.partialTick, context.source.entity().xOld, context.source.entity().getX()));
            inputs.add("player_pos_y", context -> Mth.lerp(context.partialTick, context.source.entity().yOld, context.source.entity().getY()));
            inputs.add("player_pos_z", context -> Mth.lerp(context.partialTick, context.source.entity().zOld, context.source.entity().getZ()));
            inputs.add("health", context -> context.source.entity().getHealth());
            inputs.add("hurt_time", context -> Mth.lerp(context.partialTick, (float) Math.max(0, context.source.entity().hurtTime - 1), (float) context.source.entity().hurtTime));
            inputs.add("death_time", context -> context.source.entity().deathTime);
            inputs.add("max_health", context -> context.source.entity().getMaxHealth());
            inputs.add("distance", context -> distanceFromClientPlayer(context.source.entity(), context.partialTick));
            inputs.add("height_above_ground", context -> heightAboveGround(context.source.entity()));
            inputs.add("fluid_depth", context -> context.fluidDepth().total());
            inputs.add("fluid_depth_down", context -> context.fluidDepth().down());
            inputs.add("fluid_depth_up", context -> context.fluidDepth().up());
            inputs.add("dimension", context -> context.dimension().value());
            inputs.add("dimension_overworld", context -> context.dimension() == CemDimension.OVERWORLD ? 1.0D : 0.0D);
            inputs.add("dimension_nether", context -> context.dimension() == CemDimension.NETHER ? 1.0D : 0.0D);
            inputs.add("dimension_end", context -> context.dimension() == CemDimension.END ? 1.0D : 0.0D);
            inputs.add("anger_time", context -> angerTime(context.source.entity()));
            inputs.add("anger_time_start", context -> angerTime(context.source.entity()) > 0 ? angerTime(context.source.entity()) : 0.0D);
            inputs.add("is_aggressive", context -> angerTime(context.source.entity()) > 0 || context.source.entity() instanceof Mob mob && mob.getTarget() != null ? 1.0D : 0.0D);
            inputs.add("is_alive", context -> context.source.entity().isAlive() ? 1.0D : 0.0D);
            inputs.add("is_burning", context -> context.source.entity().isOnFire() ? 1.0D : 0.0D);
            inputs.add("is_child", context -> context.source.entity().isBaby() ? 1.0D : 0.0D);
            inputs.add("is_glowing", context -> context.source.entity().isCurrentlyGlowing() ? 1.0D : 0.0D);
            inputs.add("is_jumping", context -> context.source.entity().getDeltaMovement().y > 0.05D ? 1.0D : 0.0D);
            inputs.add("is_in_hand", context -> 0.0D);
            inputs.add("is_in_item_frame", context -> 0.0D);
            inputs.add("is_in_ground", context -> 0.0D);
            inputs.add("is_riding", context -> context.source.entity().isPassenger() ? 1.0D : 0.0D);
            inputs.add("is_ridden", context -> context.source.entity().isVehicle() ? 1.0D : 0.0D);
            inputs.add("is_gliding", context -> context.source.entity().isFallFlying() ? 1.0D : 0.0D);
            inputs.add("is_flying", context -> context.source.entity() instanceof Player player && player.getAbilities().flying ? 1.0D : 0.0D);
            inputs.add("is_on_ground", context -> context.source.entity().onGround() ? 1.0D : 0.0D);
            inputs.add("is_on_head", context -> 0.0D);
            inputs.add("is_on_shoulder", context -> 0.0D);
            inputs.add("is_in_water", context -> context.source.entity().isInWater() ? 1.0D : 0.0D);
            inputs.add("is_in_lava", context -> context.source.entity().isInLava() ? 1.0D : 0.0D);
            inputs.add("is_invisible", context -> context.source.entity().isInvisible() ? 1.0D : 0.0D);
            inputs.add("is_sprinting", context -> context.source.entity().isSprinting() ? 1.0D : 0.0D);
            inputs.add("is_swimming", context -> context.source.entity().isSwimming() ? 1.0D : 0.0D);
            inputs.add("is_sitting", context -> context.source.entity().isPassenger() || context.source.entity().getPose() == net.minecraft.world.entity.Pose.SITTING ? 1.0D : 0.0D);
            inputs.add("is_sneaking", context -> context.source.entity().isCrouching() ? 1.0D : 0.0D);
            inputs.add("is_tamed", context -> 0.0D);
            inputs.add("is_wet", context -> context.source.entity().isInWaterRainOrBubble() ? 1.0D : 0.0D);
            inputs.add("is_crawling", context -> context.source.entity().isVisuallyCrawling() ? 1.0D : 0.0D);
            inputs.add("is_climbing", context -> context.source.entity().onClimbable() ? 1.0D : 0.0D);
            inputs.add("is_hurt", context -> context.source.entity().hurtTime > 0 ? 1.0D : 0.0D);
            inputs.add("is_in_gui", context -> 0.0D);
            inputs.add("is_first_person_hand", context -> 0.0D);
            inputs.add("is_using_item", context -> context.source.entity().isUsingItem() ? 1.0D : 0.0D);
            inputs.add("is_blocking", context -> context.source.entity().isBlocking() ? 1.0D : 0.0D);
            inputs.add("is_right_handed", context -> (context.source.entity().getMainArm() == HumanoidArm.RIGHT) ? 1.0D : 0.0D);
            inputs.add("is_swinging_right_arm", context -> context.source.entity().swinging && (context.source.entity().getMainArm() == HumanoidArm.RIGHT) ? 1.0D : 0.0D);
            inputs.add("is_swinging_left_arm", context -> context.source.entity().swinging && !(context.source.entity().getMainArm() == HumanoidArm.RIGHT) ? 1.0D : 0.0D);
            inputs.add("is_holding_item_right", context -> ((context.source.entity().getMainArm() == HumanoidArm.RIGHT) ? context.source.entity().getMainHandItem() : context.source.entity().getOffhandItem()).isEmpty() ? 0.0D : 1.0D);
            inputs.add("is_holding_item_left", context -> ((context.source.entity().getMainArm() == HumanoidArm.RIGHT) ? context.source.entity().getOffhandItem() : context.source.entity().getMainHandItem()).isEmpty() ? 0.0D : 1.0D);
            inputs.add("is_paused", context -> Minecraft.getInstance().isPaused() ? 1.0D : 0.0D);
            inputs.add("is_hovered", context -> Minecraft.getInstance().crosshairPickEntity == context.source.entity() ? 1.0D : 0.0D);
            inputs.add("swing_progress", context -> context.source.entity().getAttackAnim(context.partialTick));
            inputs.add("rule_index", context -> 1.0D);
            inputs.add("id", context -> Math.abs(context.source.entity().getUUID().hashCode()));
            inputs.add("pi", context -> Math.PI);
            addModelInputs(inputs, "root", context -> context.source.model().body);
            addModelInputs(inputs, "head", context -> context.source.model().head);
            addModelInputs(inputs, "headwear", context -> context.source.model().hat);
            addModelInputs(inputs, "body", context -> context.source.model().body);
            addModelInputs(inputs, "right_arm", context -> context.source.model().rightArm);
            addModelInputs(inputs, "left_arm", context -> context.source.model().leftArm);
            addModelInputs(inputs, "right_leg", context -> context.source.model().rightLeg);
            addModelInputs(inputs, "left_leg", context -> context.source.model().leftLeg);
            return inputs.build();
        }

        // Shared calculations are lazy and live only for this evaluation, never across frames.
        private MovementInput movement;
        private FluidDepth fluidDepth;
        private CemDimension dimension;

        private MovementInput movement() {
            if (movement == null) movement = MovementInput.from(source.entity());
            return movement;
        }

        private FluidDepth fluidDepth() {
            if (fluidDepth == null) fluidDepth = FluidDepth.from(source.entity());
            return fluidDepth;
        }

        private CemDimension dimension() {
            if (dimension == null) dimension = CemDimension.from(source.entity());
            return dimension;
        }

        private static void addModelInputs(CemInputPlan.Builder<CemEvaluationContext<?>> inputs,
                                           String name,
                                           java.util.function.Function<CemEvaluationContext<?>, ModelPart> part) {
            inputs.add(name + ".rx", context -> part.apply(context).xRot);
            inputs.add(name + ".ry", context -> part.apply(context).yRot);
            inputs.add(name + ".rz", context -> part.apply(context).zRot);
            inputs.add(name + ".tx", context -> part.apply(context).x);
            inputs.add(name + ".ty", context -> part.apply(context).y);
            inputs.add(name + ".tz", context -> part.apply(context).z);
            inputs.add(name + ".sx", context -> part.apply(context).xScale);
            inputs.add(name + ".sy", context -> part.apply(context).yScale);
            inputs.add(name + ".sz", context -> part.apply(context).zScale);
        }

        private void collectTransform(List<AnimationTransform> transforms, String target) {
            if (!variables.wasAssigned(target + ".rx")
                    && !variables.wasAssigned(target + ".ry")
                    && !variables.wasAssigned(target + ".rz")
                    && !variables.wasAssigned(target + ".tx")
                    && !variables.wasAssigned(target + ".ty")
                    && !variables.wasAssigned(target + ".tz")) {
                return;
            }
            transforms.add(new AnimationTransform(
                    target,
                    mappedTranslationValue(target, "tx"),
                    mappedTranslationValue(target, "ty"),
                    mappedTranslationValue(target, "tz"),
                    mappedRotationValue(target, "rx"),
                    mappedRotationValue(target, "ry"),
                    mappedRotationValue(target, "rz"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    variables.wasAssigned(target + ".tx")
                            || variables.wasAssigned(target + ".ty")
                            || variables.wasAssigned(target + ".tz"),
                    false,
                    false,
                    AnimationTransform.Operation.SET));
        }

        private Float assignedFloat(String key) {
            return variables.wasAssigned(key) ? (float) variables.get(key) : null;
        }

        private Float mappedRotationValue(String target, String axis) {
            Float value = assignedFloat(target + "." + axis);
            if (value == null) return null;
            return clampRotationValue(value);
        }

        private Float mappedTranslationValue(String target, String axis) {
            Float value = assignedFloat(target + "." + axis);
            if (value == null) return null;
            return clampTranslationValue(value);
        }

        private static float clampRotationValue(float value) {
            float limit = (float) Math.PI * 0.75F;
            if (value > limit) return limit;
            if (value < -limit) return -limit;
            return value;
        }

        private static float clampTranslationValue(float value) {
            float limit = 24.0F;
            if (value > limit) return limit;
            if (value < -limit) return -limit;
            return value;
        }

    }

    private enum CemDimension {
        NETHER(-1.0D),
        OVERWORLD(0.0D),
        END(1.0D),
        OTHER(0.0D);

        private final double value;

        CemDimension(double value) {
            this.value = value;
        }

        double value() {
            return value;
        }

        static CemDimension from(LivingEntity entity) {
            ResourceLocation location = entity.level().dimension().location();
            if (Level.NETHER.location().equals(location)) return NETHER;
            if (Level.END.location().equals(location)) return END;
            if (Level.OVERWORLD.location().equals(location)) return OVERWORLD;
            return OTHER;
        }
    }

    private record MovementInput(double forward, double strafing) {
        private static MovementInput from(LivingEntity entity) {
            Vec3 movement = entity.getDeltaMovement();
            double horizontalSpeed = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
            if (horizontalSpeed < 0.0001D) return new MovementInput(0.0D, 0.0D);

            float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
            double forwardX = -Mth.sin(yaw);
            double forwardZ = Mth.cos(yaw);
            double rightX = Mth.cos(yaw);
            double rightZ = Mth.sin(yaw);

            double forward = (movement.x * forwardX + movement.z * forwardZ) / horizontalSpeed;
            double strafing = (movement.x * rightX + movement.z * rightZ) / horizontalSpeed;
            return new MovementInput(clampUnit(forward), clampUnit(strafing));
        }

        private static double clampUnit(double value) {
            if (!Double.isFinite(value)) return 0.0D;
            return Mth.clamp(value, -1.0D, 1.0D);
        }
    }

    private static double distanceFromClientPlayer(LivingEntity entity, float partialTick) {
        net.minecraft.world.entity.player.Player player = Minecraft.getInstance().player;
        if (player == null) return 0.0D;
        double ex = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double ey = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double ez = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        double px = Mth.lerp(partialTick, player.xOld, player.getX());
        double py = Mth.lerp(partialTick, player.yOld, player.getY());
        double pz = Mth.lerp(partialTick, player.zOld, player.getZ());
        double dx = ex - px;
        double dy = ey - py;
        double dz = ez - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double heightAboveGround(LivingEntity entity) {
        Level level = entity.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(
                Mth.floor(entity.getX()),
                Mth.floor(entity.getY()),
                Mth.floor(entity.getZ()));
        int minY = Math.max(level.getMinBuildHeight(), pos.getY() - 64);
        for (int y = pos.getY(); y >= minY; y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return Math.max(0.0D, entity.getY() - (y + 1.0D));
            }
        }
        return 64.0D;
    }

    private static int angerTime(LivingEntity entity) {
        return entity instanceof Mob mob && mob.getTarget() != null ? 400 : 0;
    }

    private record FluidDepth(double down, double up) {
        private double total() {
            return down + up;
        }

        private static FluidDepth from(LivingEntity entity) {
            if (!entity.isInWater() && !entity.isInLava()) return new FluidDepth(0.0D, 0.0D);
            Level level = entity.level();
            double bottom = entity.getBoundingBox().minY;
            double top = entity.getBoundingBox().maxY;
            double centerX = entity.getX();
            double centerZ = entity.getZ();
            int minY = Mth.floor(bottom) - 4;
            int maxY = Mth.floor(top) + 4;
            double lowestFluidTop = Double.NaN;
            double highestFluidTop = Double.NaN;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(centerX), minY, Mth.floor(centerZ));
            for (int y = minY; y <= maxY; y++) {
                pos.setY(y);
                if (level.getFluidState(pos).isEmpty()) continue;
                double fluidTop = y + level.getFluidState(pos).getHeight(level, pos);
                if (!Double.isFinite(lowestFluidTop)) lowestFluidTop = fluidTop;
                highestFluidTop = Math.max(Double.isFinite(highestFluidTop) ? highestFluidTop : fluidTop, fluidTop);
            }
            if (!Double.isFinite(highestFluidTop)) return new FluidDepth(0.0D, 0.0D);
            double down = Math.max(0.0D, Math.min(top, highestFluidTop) - bottom);
            double up = Math.max(0.0D, highestFluidTop - top);
            return new FluidDepth(down, up);
        }
    }

    /** Resolve function dispatch at pack load and keep common math entirely primitive. */
    static CemExpression compileMethod(String name, List<CemExpression> args) {
        if (args.size() == 1) {
            java.util.function.DoubleUnaryOperator function = switch (name) {
                case "sin" -> x -> Math.sin(x);
                case "cos" -> x -> Math.cos(x);
                case "tan" -> x -> Math.tan(x);
                case "asin" -> x -> Math.asin(x);
                case "acos" -> x -> Math.acos(x);
                case "atan" -> x -> Math.atan(x);
                case "sqrt" -> x -> Math.sqrt(Math.max(0.0D, x));
                case "abs" -> x -> Math.abs(x);
                case "frac" -> x -> x - Math.floor(x);
                case "log" -> x -> Math.log(x);
                case "signum" -> x -> Math.signum(x);
                case "floor" -> x -> Math.floor(x);
                case "ceil" -> x -> Math.ceil(x);
                case "round" -> x -> Math.round(x);
                case "exp" -> x -> Math.exp(x);
                case "torad" -> x -> Math.toRadians(x);
                case "todeg" -> x -> Math.toDegrees(x);
                case "wrapdeg" -> x -> Math.toDegrees(wrapRad(Math.toRadians(x)));
                case "wraprad" -> x -> wrapRad(x);
                default -> null;
            };
            if (function != null) {
                CemExpression x = args.get(0);
                return context -> function.applyAsDouble(x.evaluate(context));
            }
        } else if (args.size() == 2) {
            java.util.function.DoubleBinaryOperator function = switch (name) {
                case "atan2" -> (x, y) -> Math.atan2(x, y);
                case "pow" -> (x, y) -> Math.pow(x, y);
                case "fmod" -> (x, y) -> fmod(x, y);
                case "degdiff" -> (x, y) -> Math.toDegrees(wrapRad(Math.toRadians(x - y)));
                case "raddiff" -> (x, y) -> wrapRad(x - y);
                default -> null;
            };
            if (function != null) {
                CemExpression x = args.get(0), y = args.get(1);
                return context -> function.applyAsDouble(x.evaluate(context), y.evaluate(context));
            }
        } else if (args.size() == 3) {
            TernaryFunction function = switch (name) {
                case "lerp" -> (x, y, z) -> y + x * (z - y);
                case "easeinexpo" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInExpo);
                case "easeinquad" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInQuad);
                case "easeinquart" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInQuart);
                case "easeinsine" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInSine);
                case "easeinbounce" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInBounce);
                case "easeincubic" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInCubic);
                case "easeinquint" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInQuint);
                case "easeincirc" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInCirc);
                case "easeinelastic" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInElastic);
                case "easeinback" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInBack);
                case "easeoutexpo" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeOutExpo);
                case "easeoutquad" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeOutQuad);
                case "easeoutquart" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeOutQuart);
                case "easeoutsine" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeOutSine);
                case "easeoutbounce" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeOutBounce);
                case "easeoutcubic" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeOutCubic);
                case "easeoutquint" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeOutQuint);
                case "easeoutcirc" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeOutCirc);
                case "easeoutelastic" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeOutElastic);
                case "easeoutback" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeOutBack);
                case "easeinoutexpo" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInOutExpo);
                case "easeinoutquad" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInOutQuad);
                case "easeinoutquart" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInOutQuart);
                case "easeinoutsine" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInOutSine);
                case "easeinoutbounce" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInOutBounce);
                case "easeinoutcubic" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInOutCubic);
                case "easeinoutquint" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInOutQuint);
                case "easeinoutcirc" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInOutCirc);
                case "easeinoutelastic" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInOutElastic);
                case "easeinoutback" -> (x, y, z) -> ease(x, y, z, CemAnimationProgram::easeInOutBack);
                case "clamp" -> (x, y, z) -> Math.max(y, Math.min(z, x));
                case "between" -> (x, y, z) -> bool(x >= y && x <= z);
                default -> null;
            };
            if (function != null) {
                CemExpression x = args.get(0), y = args.get(1), z = args.get(2);
                return context -> function.apply(x.evaluate(context), y.evaluate(context), z.evaluate(context));
            }
        }
        // Uncommon and variable-arity functions retain their evaluation order and semantics.
        CemExpression[] arguments = args.toArray(CemExpression[]::new);
        return context -> {
            double[] values = new double[arguments.length];
            for (int i = 0; i < arguments.length; i++) values[i] = arguments[i].evaluate(context);
            return method(name, values, context);
        };
    }

    @FunctionalInterface
    private interface TernaryFunction {
        double apply(double x, double y, double z);
    }

    private static double extreme(double[] args, boolean min) {
        if (args.length == 0) return 0.0D;
        double result = args[0];
        for (int i = 1; i < args.length; i++) result = min ? Math.min(result, args[i]) : Math.max(result, args[i]);
        return result;
    }

    static double method(String name, double[] args, CemEvaluationContext<?> context) {
        String key = name.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "sin" -> Math.sin(args[0]);
            case "cos" -> Math.cos(args[0]);
            case "tan" -> Math.tan(args[0]);
            case "asin" -> Math.asin(args[0]);
            case "acos" -> Math.acos(args[0]);
            case "atan" -> Math.atan(args[0]);
            case "atan2" -> Math.atan2(args[0], args[1]);
            case "sqrt" -> Math.sqrt(Math.max(0.0D, args[0]));
            case "abs" -> Math.abs(args[0]);
            case "frac" -> args[0] - Math.floor(args[0]);
            case "log" -> Math.log(args[0]);
            case "signum" -> Math.signum(args[0]);
            case "floor" -> Math.floor(args[0]);
            case "ceil" -> Math.ceil(args[0]);
            case "round" -> Math.round(args[0]);
            case "exp" -> Math.exp(args[0]);
            case "pow" -> Math.pow(args[0], args[1]);
            case "fmod" -> fmod(args[0], args[1]);
            case "lerp" -> args[1] + args[0] * (args[2] - args[1]);
            case "keyframe" -> keyframe(args, false);
            case "keyframeloop" -> keyframe(args, true);
            case "catmullrom" -> catmullRom(args[0], args[1], args[2], args[3], args[4]);
            case "hermite" -> hermite(args[0], args[1], args[2], args[3], args[4]);
            case "cubicbezier" -> cubicBezier(args[0], args[1], args[2], args[3], args[4]);
            case "quadbezier" -> quadBezier(args[0], args[1], args[2], args[3]);
            case "easeinexpo" -> ease(args, CemAnimationProgram::easeInExpo);
            case "easeinquad" -> ease(args, CemAnimationProgram::easeInQuad);
            case "easeinquart" -> ease(args, CemAnimationProgram::easeInQuart);
            case "easeinsine" -> ease(args, CemAnimationProgram::easeInSine);
            case "easeinbounce" -> ease(args, CemAnimationProgram::easeInBounce);
            case "easeincubic" -> ease(args, CemAnimationProgram::easeInCubic);
            case "easeinquint" -> ease(args, CemAnimationProgram::easeInQuint);
            case "easeincirc" -> ease(args, CemAnimationProgram::easeInCirc);
            case "easeinelastic" -> ease(args, CemAnimationProgram::easeInElastic);
            case "easeinback" -> ease(args, CemAnimationProgram::easeInBack);
            case "easeoutexpo" -> ease(args, CemAnimationProgram::easeOutExpo);
            case "easeoutquad" -> ease(args, CemAnimationProgram::easeOutQuad);
            case "easeoutquart" -> ease(args, CemAnimationProgram::easeOutQuart);
            case "easeoutsine" -> ease(args, CemAnimationProgram::easeOutSine);
            case "easeoutbounce" -> ease(args, CemAnimationProgram::easeOutBounce);
            case "easeoutcubic" -> ease(args, CemAnimationProgram::easeOutCubic);
            case "easeoutquint" -> ease(args, CemAnimationProgram::easeOutQuint);
            case "easeoutcirc" -> ease(args, CemAnimationProgram::easeOutCirc);
            case "easeoutelastic" -> ease(args, CemAnimationProgram::easeOutElastic);
            case "easeoutback" -> ease(args, CemAnimationProgram::easeOutBack);
            case "easeinoutexpo" -> ease(args, CemAnimationProgram::easeInOutExpo);
            case "easeinoutquad" -> ease(args, CemAnimationProgram::easeInOutQuad);
            case "easeinoutquart" -> ease(args, CemAnimationProgram::easeInOutQuart);
            case "easeinoutsine" -> ease(args, CemAnimationProgram::easeInOutSine);
            case "easeinoutbounce" -> ease(args, CemAnimationProgram::easeInOutBounce);
            case "easeinoutcubic" -> ease(args, CemAnimationProgram::easeInOutCubic);
            case "easeinoutquint" -> ease(args, CemAnimationProgram::easeInOutQuint);
            case "easeinoutcirc" -> ease(args, CemAnimationProgram::easeInOutCirc);
            case "easeinoutelastic" -> ease(args, CemAnimationProgram::easeInOutElastic);
            case "easeinoutback" -> ease(args, CemAnimationProgram::easeInOutBack);
            case "min" -> extreme(args, true);
            case "max" -> extreme(args, false);
            case "clamp" -> Math.max(args[1], Math.min(args[2], args[0]));
            case "torad" -> Math.toRadians(args[0]);
            case "todeg" -> Math.toDegrees(args[0]);
            case "wrapdeg" -> Math.toDegrees(wrapRad(Math.toRadians(args[0])));
            case "wraprad" -> wrapRad(args[0]);
            case "degdiff" -> Math.toDegrees(wrapRad(Math.toRadians(args[0] - args[1])));
            case "raddiff" -> wrapRad(args[0] - args[1]);
            case "random" -> random((args.length == 0) ? context.value("frame_counter") : args[0]);
            case "between" -> bool(args[0] >= args[1] && args[0] <= args[2]);
            case "in" -> bool(in(args));
            case "equals" -> bool(args.length >= 2 && Math.abs(args[0] - args[1]) < 0.00001D);
            case "if" -> conditional(args);
            case "print", "printb" -> (args.length == 0) ? 0.0D : args[args.length - 1];
            case "catch" -> (args.length == 0) ? 0.0D : (Double.isFinite(args[0]) ? args[0] : args.length > 1 ? args[1] : 0.0D);
            case "nbt" -> 0.0D;
            default -> 0.0D;
        };
    }

    static CemExpression nbt(String query) {
        String trimmed = query.trim();
        int comma = trimmed.indexOf(',');
        String path = (comma >= 0 ? trimmed.substring(0, comma) : trimmed).trim();
        String expected = comma >= 0 ? trimmed.substring(comma + 1).trim() : "";

        Predicate<NbtValue> matcher = nbtMatcher(expected);
        return context -> {
            NbtValue value = context.nbtValue(path);
            if (!value.exists()) return bool(matchesMissing(expected));
            if (expected.isEmpty()) return bool(value.truthy());
            return bool(matcher.test(value));
        };
    }

    private static Predicate<NbtValue> nbtMatcher(String expected) {
        String normalized = expected.toLowerCase(Locale.ROOT);
        for (String prefix : List.of("raw:iregex:", "raw:regex:", "iregex:", "regex:")) {
            if (normalized.startsWith(prefix)) {
                Predicate<String> matcher = regexMatcher(expected.substring(prefix.length()), prefix.contains("iregex"));
                return value -> matcher.test(prefix.startsWith("raw:") ? value.raw() : value.string());
            }
        }
        return value -> matchesNbtExpected(value, expected);
    }

    private static Optional<NbtValue> liveNbtValue(String path, LivingEntity entity) {
        // Fresh Animations checks SleepingX even on awake villagers. Saving the whole
        // entity for this also serializes trades, brains and every Forge capability.
        if (isSleepingCoordinate(path)) {
            Tag coordinate = sleepingCoordinate(path, entity.getSleepingPos());
            return Optional.of(coordinate == null ? NbtValue.MISSING : NbtValue.ofTag(coordinate));
        }
        if (!(entity instanceof Player player)) return Optional.empty();
        String normalized = path.toLowerCase(Locale.ROOT);
        if ("abilities.flying".equals(normalized)) {
            return Optional.of(NbtValue.ofBoolean(player.getAbilities().flying));
        }
        if ("selecteditem.id".equals(normalized)) {
            if (player.getMainHandItem().isEmpty()) return Optional.of(NbtValue.MISSING);
            return Optional.of(NbtValue.ofString(BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString()));
        }
        return Optional.empty();
    }

    private static boolean isSleepingCoordinate(String path) {
        return "SleepingX".equals(path) || "SleepingY".equals(path) || "SleepingZ".equals(path);
    }

    /** Same optional integer tags that LivingEntity writes, without an entity save. */
    static Tag sleepingCoordinate(String path, Optional<BlockPos> sleepingPos) {
        if (sleepingPos.isEmpty()) return null;
        BlockPos pos = sleepingPos.get();
        return switch (path) {
            case "SleepingX" -> IntTag.valueOf(pos.getX());
            case "SleepingY" -> IntTag.valueOf(pos.getY());
            case "SleepingZ" -> IntTag.valueOf(pos.getZ());
            default -> null;
        };
    }

    private static Tag findNbtPath(CompoundTag root, String path) {
        if (path.isEmpty()) return root;
        Tag current = root;
        for (String part : path.split("\\.")) {
            if (part.isEmpty()) return null;
            if (!(current instanceof CompoundTag compound) || !compound.contains(part)) return null;
            current = compound.get(part);
            if (current == null) return null;
        }
        return current;
    }

    private static boolean matchesMissing(String expected) {
        String normalized = expected.trim().toLowerCase(Locale.ROOT);
        return "exists:false".equals(normalized);
    }

    private static boolean matchesNbtExpected(NbtValue value, String expected) {
        String normalized = expected.trim().toLowerCase(Locale.ROOT);
        if ("exists:true".equals(normalized)) return value.exists();
        if ("exists:false".equals(normalized)) return !value.exists();
        if ("true".equals(normalized)) return value.asBoolean();
        if ("false".equals(normalized)) return !value.asBoolean();

        Double expectedNumber = parseDoubleOrNull(expected);
        if (expectedNumber != null && value.number() != null) {
            return Math.abs(value.number() - expectedNumber) < 0.00001D;
        }

        return value.string().equals(expected) || value.string().equalsIgnoreCase(expected);
    }

    static Predicate<String> regexMatcher(String regex, boolean caseInsensitive) {
        try {
            int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
            Pattern pattern = Pattern.compile(regex, flags);
            // ETF's NBT regex predicates match the entire value. Substring search
            // retries leading .* at every offset when a large inventory fails to match.
            return value -> pattern.matcher(value).matches();
        } catch (PatternSyntaxException ignored) {
            return value -> false;
        }
    }

    private static Double parseDoubleOrNull(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record NbtValue(boolean exists, String string, String raw, Double number, Boolean bool) {
        private static final NbtValue MISSING = new NbtValue(false, "", "", null, null);

        private static NbtValue ofBoolean(boolean value) {
            return new NbtValue(true, value ? "1" : "0", value ? "1b" : "0b", value ? 1.0D : 0.0D, value);
        }

        private static NbtValue ofString(String value) {
            return new NbtValue(true, value, value, null, null);
        }

        private static NbtValue ofTag(Tag tag) {
            if (tag instanceof NumericTag numeric) {
                double value = numeric.getAsDouble();
                return new NbtValue(true, Double.toString(value), tag.toString(), value, Math.abs(value) > 0.00001D);
            }
            if (tag instanceof StringTag stringTag) {
                return ofString(stringTag.getAsString());
            }
            return new NbtValue(true, tag.getAsString(), tag.toString(), null, null);
        }

        private boolean truthy() {
            if (bool != null) return bool;
            if (number != null) return Math.abs(number) > 0.00001D;
            return !string.isEmpty();
        }

        private boolean asBoolean() {
            if (bool != null) return bool;
            if (number != null) return Math.abs(number) > 0.00001D;
            return "true".equalsIgnoreCase(string) || "1".equals(string);
        }
    }

    private static double keyframe(double[] args, boolean loop) {
        if (args.length < 2) return 0.0D;
        int keyframes = args.length - 1;
        double frame = args[0];
        if (keyframes == 1) return args[1];

        if (loop) {
            frame = fmod(frame, keyframes);
        } else if (frame <= 0.0D) {
            return args[1];
        } else if (frame >= keyframes - 1) {
            return args[args.length - 1];
        }

        int index = Mth.clamp((int) Math.floor(frame), 0, keyframes - 1);
        int next = loop ? (index + 1) % keyframes : Math.min(index + 1, keyframes - 1);
        int previous = loop ? fmodIndex(index - 1, keyframes) : Math.max(index - 1, 0);
        int afterNext = loop ? fmodIndex(index + 2, keyframes) : Math.min(index + 2, keyframes - 1);
        double t = frame - Math.floor(frame);
        return catmullRom(
                t,
                args[index + 1],
                args[next + 1],
                args[previous + 1],
                args[afterNext + 1]);
    }

    private static int fmodIndex(int value, int divisor) {
        int result = value % divisor;
        return result < 0 ? result + divisor : result;
    }

    private static double catmullRom(double t, double x, double y, double z, double w) {
        double tt = t * t;
        double ttt = tt * t;
        return 0.5D * ((2.0D * x)
                + (-z + y) * t
                + (2.0D * z - 5.0D * x + 4.0D * y - w) * tt
                + (-z + 3.0D * x - 3.0D * y + w) * ttt);
    }

    private static double hermite(double t, double x, double y, double z, double w) {
        double tt = t * t;
        double ttt = tt * t;
        return (2.0D * ttt - 3.0D * tt + 1.0D) * x
                + (ttt - 2.0D * tt + t) * z
                + (-2.0D * ttt + 3.0D * tt) * y
                + (ttt - tt) * w;
    }

    private static double cubicBezier(double t, double x, double y, double z, double w) {
        double oneMinusT = 1.0D - t;
        return oneMinusT * oneMinusT * oneMinusT * x
                + 3.0D * oneMinusT * oneMinusT * t * z
                + 3.0D * oneMinusT * t * t * w
                + t * t * t * y;
    }

    private static double quadBezier(double t, double x, double y, double z) {
        double oneMinusT = 1.0D - t;
        return oneMinusT * oneMinusT * x
                + 2.0D * oneMinusT * t * z
                + t * t * y;
    }

    private static double smoothStep(double value) {
        double t = Mth.clamp(value, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    private static double lerp(double t, double x, double y) {
        return x + t * (y - x);
    }

    private static double ease(double[] args, Easing easing) {
        return ease(args[0], args[1], args[2], easing);
    }

    private static double ease(double t, double start, double end, Easing easing) {
        return lerp(easing.value(Mth.clamp(t, 0.0D, 1.0D)), start, end);
    }

    private static double easeInExpo(double t) {
        return t == 0.0D ? 0.0D : Math.pow(2.0D, 10.0D * t - 10.0D);
    }

    private static double easeOutExpo(double t) {
        return t == 1.0D ? 1.0D : 1.0D - Math.pow(2.0D, -10.0D * t);
    }

    private static double easeInOutExpo(double t) {
        if (t == 0.0D || t == 1.0D) return t;
        return t < 0.5D
                ? Math.pow(2.0D, 20.0D * t - 10.0D) / 2.0D
                : (2.0D - Math.pow(2.0D, -20.0D * t + 10.0D)) / 2.0D;
    }

    private static double easeInQuad(double t) {
        return t * t;
    }

    private static double easeOutQuad(double t) {
        return 1.0D - (1.0D - t) * (1.0D - t);
    }

    private static double easeInOutQuad(double t) {
        return t < 0.5D ? 2.0D * t * t : 1.0D - Math.pow(-2.0D * t + 2.0D, 2.0D) / 2.0D;
    }

    private static double easeInQuart(double t) {
        return t * t * t * t;
    }

    private static double easeOutQuart(double t) {
        return 1.0D - Math.pow(1.0D - t, 4.0D);
    }

    private static double easeInOutQuart(double t) {
        return t < 0.5D ? 8.0D * Math.pow(t, 4.0D) : 1.0D - Math.pow(-2.0D * t + 2.0D, 4.0D) / 2.0D;
    }

    private static double easeInSine(double t) {
        return 1.0D - Math.cos(t * Math.PI / 2.0D);
    }

    private static double easeOutSine(double t) {
        return Math.sin(t * Math.PI / 2.0D);
    }

    private static double easeInOutSine(double t) {
        return -(Math.cos(Math.PI * t) - 1.0D) / 2.0D;
    }

    private static double easeInBounce(double t) {
        return 1.0D - easeOutBounce(1.0D - t);
    }

    private static double easeInOutBounce(double t) {
        return t < 0.5D
                ? (1.0D - easeOutBounce(1.0D - 2.0D * t)) / 2.0D
                : (1.0D + easeOutBounce(2.0D * t - 1.0D)) / 2.0D;
    }

    private static double easeOutBounce(double t) {
        double n1 = 7.5625D;
        double d1 = 2.75D;
        if (t < 1.0D / d1) {
            return n1 * t * t;
        } else if (t < 2.0D / d1) {
            double adjusted = t - 1.5D / d1;
            return n1 * adjusted * adjusted + 0.75D;
        } else if (t < 2.5D / d1) {
            double adjusted = t - 2.25D / d1;
            return n1 * adjusted * adjusted + 0.9375D;
        }
        double adjusted = t - 2.625D / d1;
        return n1 * adjusted * adjusted + 0.984375D;
    }

    private static double easeInCubic(double t) {
        return t * t * t;
    }

    private static double easeOutCubic(double t) {
        return 1.0D - Math.pow(1.0D - t, 3.0D);
    }

    private static double easeInOutCubic(double t) {
        return t < 0.5D ? 4.0D * t * t * t : 1.0D - Math.pow(-2.0D * t + 2.0D, 3.0D) / 2.0D;
    }

    private static double easeInQuint(double t) {
        return t * t * t * t * t;
    }

    private static double easeOutQuint(double t) {
        return 1.0D - Math.pow(1.0D - t, 5.0D);
    }

    private static double easeInOutQuint(double t) {
        return t < 0.5D ? 16.0D * Math.pow(t, 5.0D) : 1.0D - Math.pow(-2.0D * t + 2.0D, 5.0D) / 2.0D;
    }

    private static double easeInCirc(double t) {
        return 1.0D - Math.sqrt(1.0D - t * t);
    }

    private static double easeOutCirc(double t) {
        return Math.sqrt(1.0D - Math.pow(t - 1.0D, 2.0D));
    }

    private static double easeInOutCirc(double t) {
        return t < 0.5D
                ? (1.0D - Math.sqrt(1.0D - Math.pow(2.0D * t, 2.0D))) / 2.0D
                : (Math.sqrt(1.0D - Math.pow(-2.0D * t + 2.0D, 2.0D)) + 1.0D) / 2.0D;
    }

    private static double easeInElastic(double t) {
        if (t == 0.0D || t == 1.0D) return t;
        double c4 = 2.0D * Math.PI / 3.0D;
        return -Math.pow(2.0D, 10.0D * t - 10.0D) * Math.sin((t * 10.0D - 10.75D) * c4);
    }

    private static double easeOutElastic(double t) {
        if (t == 0.0D || t == 1.0D) return t;
        double c4 = 2.0D * Math.PI / 3.0D;
        return Math.pow(2.0D, -10.0D * t) * Math.sin((t * 10.0D - 0.75D) * c4) + 1.0D;
    }

    private static double easeInOutElastic(double t) {
        if (t == 0.0D || t == 1.0D) return t;
        double c5 = 2.0D * Math.PI / 4.5D;
        return t < 0.5D
                ? -(Math.pow(2.0D, 20.0D * t - 10.0D) * Math.sin((20.0D * t - 11.125D) * c5)) / 2.0D
                : Math.pow(2.0D, -20.0D * t + 10.0D) * Math.sin((20.0D * t - 11.125D) * c5) / 2.0D + 1.0D;
    }

    private static double easeInBack(double t) {
        double c1 = 1.70158D;
        double c3 = c1 + 1.0D;
        return c3 * t * t * t - c1 * t * t;
    }

    private static double easeOutBack(double t) {
        double c1 = 1.70158D;
        double c3 = c1 + 1.0D;
        return 1.0D + c3 * Math.pow(t - 1.0D, 3.0D) + c1 * Math.pow(t - 1.0D, 2.0D);
    }

    private static double easeInOutBack(double t) {
        double c1 = 1.70158D;
        double c2 = c1 * 1.525D;
        return t < 0.5D
                ? Math.pow(2.0D * t, 2.0D) * ((c2 + 1.0D) * 2.0D * t - c2) / 2.0D
                : (Math.pow(2.0D * t - 2.0D, 2.0D) * ((c2 + 1.0D) * (2.0D * t - 2.0D) + c2) + 2.0D) / 2.0D;
    }

    @FunctionalInterface
    private interface Easing {
        double value(double t);
    }

    private static double fmod(double value, double divisor) {
        if (divisor == 0.0D) return Double.NaN;
        double result = value % divisor;
        if (result != 0.0D && Math.signum(result) != Math.signum(divisor)) {
            result += divisor;
        }
        return result;
    }

    private static double conditional(double[] args) {
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (truthy(args[i])) return args[i + 1];
        }
        return args.length % 2 == 1 ? args[args.length - 1] : 0.0D;
    }

    private static boolean in(double[] args) {
        if ((args.length == 0)) return false;
        double value = args[0];
        for (int i = 1; i < args.length; i++) {
            if (Math.abs(value - args[i]) < 0.00001D) return true;
        }
        return false;
    }

    static boolean truthy(double value) {
        return Math.abs(value) > 0.00001D;
    }

    private static double bool(boolean value) {
        return value ? 1.0D : 0.0D;
    }

    private static double random(double seed) {
        long bits = Double.doubleToLongBits(seed * 31.4159D);
        bits ^= bits >>> 33;
        bits *= 0xff51afd7ed558ccdL;
        bits ^= bits >>> 33;
        return ((bits >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    private static double wrapRad(double value) {
        double twoPi = Math.PI * 2.0D;
        double wrapped = value % twoPi;
        if (wrapped >= Math.PI) wrapped -= twoPi;
        if (wrapped < -Math.PI) wrapped += twoPi;
        return wrapped;
    }
}
