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

public final class CemAnimationProgram {
    private final List<CemAssignment> assignments;
    private final Map<UUID, EntityVariables> entityVariables = new HashMap<>();
    private long lastVariablePruneTick;

    private CemAnimationProgram(List<CemAssignment> assignments) {
        this.assignments = assignments;
    }

    public static Optional<CemAnimationProgram> load(ResourceLocation entryPoint) {
        try {
            List<CemAssignment> assignments = new ArrayList<>();
            Set<ResourceLocation> visited = new HashSet<>();
            loadResource(entryPoint, assignments, visited);
            if (assignments.isEmpty()) return Optional.empty();
            Townstead.LOGGER.info(
                    "[AnimationBridge] loaded CEM program location={} assignments={} targets={}",
                    entryPoint,
                    assignments.size(),
                    targetSummary(assignments));
            return Optional.of(new CemAnimationProgram(assignments));
        } catch (Exception e) {
            Townstead.LOGGER.warn("[AnimationBridge] failed to load CEM program location={}", entryPoint, e);
            return Optional.empty();
        }
    }

    public <T extends LivingEntity> List<AnimationTransform> evaluate(AnimationSourceContext<T> source) {
        T entity = source.entity();
        long gameTime = entity.level().getGameTime();
        pruneEntityVariables(gameTime);
        EntityVariables entityState = entityVariables.computeIfAbsent(entity.getUUID(), ignored -> new EntityVariables());
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
        CemEvaluationContext<T> context = new CemEvaluationContext<>(source, variables, frameTime, partialTick, entityState.frameCounter);
        for (CemAssignment assignment : assignments) {
            double value = assignment.expression().evaluate(context);
            if (Double.isFinite(value)) {
                context.assign(assignment.target(), value);
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
            Set<ResourceLocation> visited
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
                    loadResource(sibling(location, child), assignments, visited);
                }
                readAnimations(model, assignments);
            }
        }

        readAnimations(root, assignments);
    }

    private static ResourceLocation sibling(ResourceLocation base, String child) {
        String path = base.getPath();
        int slash = path.lastIndexOf('/');
        String prefix = slash >= 0 ? path.substring(0, slash + 1) : "";
        return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), prefix + child);
    }

    private static void readAnimations(JsonObject object, List<CemAssignment> assignments) {
        if (!object.has("animations") || !object.get("animations").isJsonArray()) return;
        for (JsonElement animationElement : object.getAsJsonArray("animations")) {
            if (!animationElement.isJsonObject()) continue;
            JsonObject animation = animationElement.getAsJsonObject();
            for (String key : animation.keySet()) {
                String expression = animation.get(key).getAsString();
                try {
                    assignments.add(new CemAssignment(key, CemExpressionParser.parse(expression)));
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

    private record CemAssignment(String target, CemExpression expression) {}

    private static final class EntityVariables {
        private final CemVariableStore variables = new CemVariableStore();
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
        private final Map<String, CemPartPose> baseline = new HashMap<>();

        CemEvaluationContext(AnimationSourceContext<T> source, CemVariableStore variables, double frameTime, float partialTick, long frameCounter) {
            this.source = source;
            this.variables = variables;
            this.frameTime = frameTime;
            this.partialTick = partialTick;
            this.frameCounter = frameCounter;
            seedRenderVariables();
            seedModelPart("root", source.model().body);
            seedModelPart("head", source.model().head);
            seedModelPart("headwear", source.model().hat);
            seedModelPart("body", source.model().body);
            seedModelPart("right_arm", source.model().rightArm);
            seedModelPart("left_arm", source.model().leftArm);
            seedModelPart("right_leg", source.model().rightLeg);
            seedModelPart("left_leg", source.model().leftLeg);
        }

        double value(String key) {
            return variables.get(key);
        }

        void assign(String key, double value) {
            variables.set(key, value);
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

        private void seedRenderVariables() {
            T entity = source.entity();
            float animationProgress = source.animationProgress();
            variables.seed("age", animationProgress);
            variables.seed("time", animationProgress);
            variables.seed("frame_time", frameTime);
            variables.seed("frame_counter", frameCounter);
            variables.seed("limb_swing", source.parameters().limbAngle());
            variables.seed("limb_speed", source.parameters().limbDistance());
            variables.seed("head_yaw", source.parameters().headYaw());
            variables.seed("head_pitch", source.headPitch());
            MovementInput movement = MovementInput.from(entity);
            variables.seed("move_forward", movement.forward());
            variables.seed("move_strafing", movement.strafing());
            variables.seed("rot_x", Math.toRadians(source.headPitch()));
            variables.seed("rot_y", Math.toRadians(source.parameters().headYaw()));
            variables.seed("player_rot_x", Math.toRadians(source.headPitch()));
            variables.seed("player_rot_y", Math.toRadians(source.parameters().headYaw()));
            double posX = Mth.lerp(partialTick, entity.xOld, entity.getX());
            double posY = Mth.lerp(partialTick, entity.yOld, entity.getY());
            double posZ = Mth.lerp(partialTick, entity.zOld, entity.getZ());
            variables.seed("pos_x", posX);
            variables.seed("pos_y", posY);
            variables.seed("pos_z", posZ);
            variables.seed("player_pos_x", posX);
            variables.seed("player_pos_y", posY);
            variables.seed("player_pos_z", posZ);
            variables.seed("health", entity.getHealth());
            variables.seed("hurt_time", Mth.lerp(partialTick, (float) Math.max(0, entity.hurtTime - 1), (float) entity.hurtTime));
            variables.seed("death_time", entity.deathTime);
            variables.seed("max_health", entity.getMaxHealth());
            variables.seed("distance", distanceFromClientPlayer(entity, partialTick));
            variables.seed("height_above_ground", heightAboveGround(entity));
            FluidDepth fluidDepth = FluidDepth.from(entity);
            variables.seed("fluid_depth", fluidDepth.total());
            variables.seed("fluid_depth_down", fluidDepth.down());
            variables.seed("fluid_depth_up", fluidDepth.up());
            CemDimension dimension = CemDimension.from(entity);
            variables.seed("dimension", dimension.value());
            variables.seed("dimension_overworld", dimension == CemDimension.OVERWORLD ? 1.0D : 0.0D);
            variables.seed("dimension_nether", dimension == CemDimension.NETHER ? 1.0D : 0.0D);
            variables.seed("dimension_end", dimension == CemDimension.END ? 1.0D : 0.0D);
            HumanoidArm mainArm = entity.getMainArm();
            boolean rightHanded = mainArm == HumanoidArm.RIGHT;
            int angerTime = angerTime(entity);
            variables.seed("anger_time", angerTime);
            variables.seed("anger_time_start", angerTime > 0 ? angerTime : 0.0D);
            variables.seed("is_aggressive", angerTime > 0 || entity instanceof Mob mob && mob.getTarget() != null ? 1.0D : 0.0D);
            variables.seed("is_alive", entity.isAlive() ? 1.0D : 0.0D);
            variables.seed("is_burning", entity.isOnFire() ? 1.0D : 0.0D);
            variables.seed("is_child", entity.isBaby() ? 1.0D : 0.0D);
            variables.seed("is_glowing", entity.isCurrentlyGlowing() ? 1.0D : 0.0D);
            variables.seed("is_jumping", entity.getDeltaMovement().y > 0.05D ? 1.0D : 0.0D);
            variables.seed("is_in_hand", 0.0D);
            variables.seed("is_in_item_frame", 0.0D);
            variables.seed("is_in_ground", 0.0D);
            variables.seed("is_riding", entity.isPassenger() ? 1.0D : 0.0D);
            variables.seed("is_ridden", entity.isVehicle() ? 1.0D : 0.0D);
            variables.seed("is_gliding", entity.isFallFlying() ? 1.0D : 0.0D);
            variables.seed("is_flying", entity instanceof Player player && player.getAbilities().flying ? 1.0D : 0.0D);
            variables.seed("is_on_ground", entity.onGround() ? 1.0D : 0.0D);
            variables.seed("is_on_head", 0.0D);
            variables.seed("is_on_shoulder", 0.0D);
            variables.seed("is_in_water", entity.isInWater() ? 1.0D : 0.0D);
            variables.seed("is_in_lava", entity.isInLava() ? 1.0D : 0.0D);
            variables.seed("is_invisible", entity.isInvisible() ? 1.0D : 0.0D);
            variables.seed("is_sprinting", entity.isSprinting() ? 1.0D : 0.0D);
            variables.seed("is_swimming", entity.isSwimming() ? 1.0D : 0.0D);
            variables.seed("is_sitting", entity.isPassenger() || entity.getPose() == net.minecraft.world.entity.Pose.SITTING ? 1.0D : 0.0D);
            variables.seed("is_sneaking", entity.isCrouching() ? 1.0D : 0.0D);
            variables.seed("is_tamed", 0.0D);
            variables.seed("is_wet", entity.isInWaterRainOrBubble() ? 1.0D : 0.0D);
            variables.seed("is_crawling", entity.isVisuallyCrawling() ? 1.0D : 0.0D);
            variables.seed("is_climbing", entity.onClimbable() ? 1.0D : 0.0D);
            variables.seed("is_hurt", entity.hurtTime > 0 ? 1.0D : 0.0D);
            variables.seed("is_in_gui", 0.0D);
            variables.seed("is_first_person_hand", 0.0D);
            variables.seed("is_using_item", entity.isUsingItem() ? 1.0D : 0.0D);
            variables.seed("is_blocking", entity.isBlocking() ? 1.0D : 0.0D);
            variables.seed("is_right_handed", rightHanded ? 1.0D : 0.0D);
            variables.seed("is_swinging_right_arm", entity.swinging && rightHanded ? 1.0D : 0.0D);
            variables.seed("is_swinging_left_arm", entity.swinging && !rightHanded ? 1.0D : 0.0D);
            variables.seed("is_holding_item_right", (rightHanded ? entity.getMainHandItem() : entity.getOffhandItem()).isEmpty() ? 0.0D : 1.0D);
            variables.seed("is_holding_item_left", (rightHanded ? entity.getOffhandItem() : entity.getMainHandItem()).isEmpty() ? 0.0D : 1.0D);
            variables.seed("is_paused", Minecraft.getInstance().isPaused() ? 1.0D : 0.0D);
            variables.seed("is_hovered", Minecraft.getInstance().crosshairPickEntity == entity ? 1.0D : 0.0D);
            variables.seed("swing_progress", entity.getAttackAnim(partialTick));
            variables.seed("rule_index", 1.0D);
            variables.seed("id", Math.abs(entity.getUUID().hashCode()));
            variables.seed("pi", Math.PI);
        }

        private void seedModelPart(String name, ModelPart part) {
            variables.seed(name + ".rx", part.xRot);
            variables.seed(name + ".ry", part.yRot);
            variables.seed(name + ".rz", part.zRot);
            variables.seed(name + ".tx", part.x);
            variables.seed(name + ".ty", part.y);
            variables.seed(name + ".tz", part.z);
            variables.seed(name + ".sx", part.xScale);
            variables.seed(name + ".sy", part.yScale);
            variables.seed(name + ".sz", part.zScale);
            baseline.put(name, new CemPartPose(
                    part.xRot,
                    part.yRot,
                    part.zRot,
                    part.x,
                    part.y,
                    part.z,
                    part.xScale,
                    part.yScale,
                    part.zScale));
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
                    variables.wasAssigned(target + ".tx")
                            || variables.wasAssigned(target + ".ty")
                            || variables.wasAssigned(target + ".tz"),
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

    private record CemPartPose(
            float xRot,
            float yRot,
            float zRot,
            float x,
            float y,
            float z,
            float xScale,
            float yScale,
            float zScale
    ) {
        private static final CemPartPose ZERO = new CemPartPose(
                0.0F, 0.0F, 0.0F,
                0.0F, 0.0F, 0.0F,
                1.0F, 1.0F, 1.0F);
    }

    static double method(String name, List<Double> args, CemEvaluationContext<?> context) {
        String key = name.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "sin" -> Math.sin(args.get(0));
            case "cos" -> Math.cos(args.get(0));
            case "tan" -> Math.tan(args.get(0));
            case "asin" -> Math.asin(args.get(0));
            case "acos" -> Math.acos(args.get(0));
            case "atan" -> Math.atan(args.get(0));
            case "atan2" -> Math.atan2(args.get(0), args.get(1));
            case "sqrt" -> Math.sqrt(Math.max(0.0D, args.get(0)));
            case "abs" -> Math.abs(args.get(0));
            case "frac" -> args.get(0) - Math.floor(args.get(0));
            case "log" -> Math.log(args.get(0));
            case "signum" -> Math.signum(args.get(0));
            case "floor" -> Math.floor(args.get(0));
            case "ceil" -> Math.ceil(args.get(0));
            case "round" -> Math.round(args.get(0));
            case "exp" -> Math.exp(args.get(0));
            case "pow" -> Math.pow(args.get(0), args.get(1));
            case "fmod" -> fmod(args.get(0), args.get(1));
            case "lerp" -> args.get(1) + args.get(0) * (args.get(2) - args.get(1));
            case "keyframe" -> keyframe(args, false);
            case "keyframeloop" -> keyframe(args, true);
            case "catmullrom" -> catmullRom(args.get(0), args.get(1), args.get(2), args.get(3), args.get(4));
            case "hermite" -> hermite(args.get(0), args.get(1), args.get(2), args.get(3), args.get(4));
            case "cubicbezier" -> cubicBezier(args.get(0), args.get(1), args.get(2), args.get(3), args.get(4));
            case "quadbezier" -> quadBezier(args.get(0), args.get(1), args.get(2), args.get(3));
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
            case "min" -> args.stream().mapToDouble(Double::doubleValue).min().orElse(0.0D);
            case "max" -> args.stream().mapToDouble(Double::doubleValue).max().orElse(0.0D);
            case "clamp" -> Math.max(args.get(1), Math.min(args.get(2), args.get(0)));
            case "torad" -> Math.toRadians(args.get(0));
            case "todeg" -> Math.toDegrees(args.get(0));
            case "wrapdeg" -> Math.toDegrees(wrapRad(Math.toRadians(args.get(0))));
            case "wraprad" -> wrapRad(args.get(0));
            case "degdiff" -> Math.toDegrees(wrapRad(Math.toRadians(args.get(0) - args.get(1))));
            case "raddiff" -> wrapRad(args.get(0) - args.get(1));
            case "random" -> random(args.isEmpty() ? context.value("frame_counter") : args.get(0));
            case "between" -> bool(args.get(0) >= args.get(1) && args.get(0) <= args.get(2));
            case "in" -> bool(in(args));
            case "equals" -> bool(args.size() >= 2 && Math.abs(args.get(0) - args.get(1)) < 0.00001D);
            case "if" -> conditional(args);
            case "print", "printb" -> args.isEmpty() ? 0.0D : args.get(args.size() - 1);
            case "catch" -> args.isEmpty() ? 0.0D : (Double.isFinite(args.get(0)) ? args.get(0) : args.size() > 1 ? args.get(1) : 0.0D);
            case "nbt" -> 0.0D;
            default -> 0.0D;
        };
    }

    static double nbt(String query, CemEvaluationContext<?> context) {
        String trimmed = query.trim();
        int comma = trimmed.indexOf(',');
        String path = (comma >= 0 ? trimmed.substring(0, comma) : trimmed).trim().toLowerCase(Locale.ROOT);
        String expected = comma >= 0 ? trimmed.substring(comma + 1).trim().toLowerCase(Locale.ROOT) : "";

        if ("abilities.flying".equals(path) && context.source.entity() instanceof Player player) {
            boolean flying = player.getAbilities().flying;
            if (expected.isEmpty()) return bool(flying);
            if ("1".equals(expected) || "true".equals(expected)) return bool(flying);
            if ("0".equals(expected) || "false".equals(expected)) return bool(!flying);
            return 0.0D;
        }

        return 0.0D;
    }

    private static double keyframe(List<Double> args, boolean loop) {
        if (args.size() < 2) return 0.0D;
        int keyframes = args.size() - 1;
        double frame = args.get(0);
        if (keyframes == 1) return args.get(1);

        if (loop) {
            frame = fmod(frame, keyframes);
        } else if (frame <= 0.0D) {
            return args.get(1);
        } else if (frame >= keyframes - 1) {
            return args.get(args.size() - 1);
        }

        int index = Mth.clamp((int) Math.floor(frame), 0, keyframes - 1);
        int next = loop ? (index + 1) % keyframes : Math.min(index + 1, keyframes - 1);
        int previous = loop ? fmodIndex(index - 1, keyframes) : Math.max(index - 1, 0);
        int afterNext = loop ? fmodIndex(index + 2, keyframes) : Math.min(index + 2, keyframes - 1);
        double t = frame - Math.floor(frame);
        return catmullRom(
                t,
                args.get(index + 1),
                args.get(next + 1),
                args.get(previous + 1),
                args.get(afterNext + 1));
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

    private static double ease(List<Double> args, Easing easing) {
        return lerp(easing.value(Mth.clamp(args.get(0), 0.0D, 1.0D)), args.get(1), args.get(2));
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

    private static double conditional(List<Double> args) {
        for (int i = 0; i + 1 < args.size(); i += 2) {
            if (truthy(args.get(i))) return args.get(i + 1);
        }
        return args.size() % 2 == 1 ? args.get(args.size() - 1) : 0.0D;
    }

    private static boolean in(List<Double> args) {
        if (args.isEmpty()) return false;
        double value = args.get(0);
        for (int i = 1; i < args.size(); i++) {
            if (Math.abs(value - args.get(i)) < 0.00001D) return true;
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
