package com.aetherianartificer.townstead.compat.beachparty;

import com.aetherianartificer.townstead.hangout.HangoutEmbodiment;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * Reflection-safe bridge to Beachparty's chair entity and occupancy ledger. Beachparty 2.x seats
 * through its own util; 1.1.5 (Forge 1.20.1) seats through Let's Do API's ChairUtil, which has the
 * same three calls.
 */
public final class BeachpartyChairAdapter implements HangoutEmbodiment.PostureAdapter {
    public static final ResourceLocation ID = ResourceLocation.tryParse("beachparty:native_chair");
    private static final Map<ResourceLocation, Double> HEIGHTS = Map.of(
            id("beachparty:beach_chair"), 0.30D,
            id("beachparty:hooded_beach_chair"), 0.45D,
            id("beachparty:palm_chair"), 0.55D,
            id("beachparty:palm_bar_stool"), 0.60D,
            id("beachparty:lounge_chair"), -0.10D,
            id("beachparty:tiki_chair"), 0.10D,
            id("beachparty:chair"), 0.10D);
    private record SeatApi(String util, ResourceLocation chair) {}
    private static final SeatApi[] APIS = {
            new SeatApi("net.satisfy.beachparty.core.util.BeachpartyUtil", id("beachparty:chair")),
            new SeatApi("de.cristelknight.doapi.common.util.ChairUtil", id("doapi:chair"))};

    @Override public ResourceLocation id() { return ID; }

    @Override
    public @Nullable HangoutEmbodiment.Handle enter(ServerLevel level, VillagerEntityMCA villager,
                                                     BlockPos spot, ResourceLocation posture,
                                                     Vec3 position, UUID visitId) {
        BlockPos base = normalizeBase(level.getBlockState(spot), spot);
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(base).getBlock());
        Double height = HEIGHTS.get(block);
        if (height == null) return null;
        SeatApi api = seatApi();
        // Neither seat API is installed: the vanilla posture still seats the villager.
        if (api == null) return null;
        if (occupied(api, level, base)) return HangoutEmbodiment.BLOCKED;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(api.chair()).orElse(null);
        if (type == null) return HangoutEmbodiment.BLOCKED;
        Entity chair = type.create(level);
        if (chair == null) return HangoutEmbodiment.BLOCKED;
        float yaw = facingYaw(level.getBlockState(base));
        chair.moveTo(position.x, position.y, position.z, yaw, 0F);
        HangoutEmbodiment.markVisitAnchor(chair, visitId);
        if (!addNativeClaim(api, level, base, chair, villager.blockPosition())) {
            chair.discard();
            return HangoutEmbodiment.BLOCKED;
        }
        if (!level.addFreshEntity(chair) || !villager.startRiding(chair, true)) {
            removeNativeClaim(api, level, base);
            chair.discard();
            return HangoutEmbodiment.BLOCKED;
        }
        face(villager, yaw);
        UUID chairId = chair.getUUID();
        return (closeLevel, closeVillager) -> {
            if (closeVillager.isPassenger() && closeVillager.getVehicle() != null
                    && chairId.equals(closeVillager.getVehicle().getUUID())) closeVillager.stopRiding();
            removeNativeClaim(api, closeLevel, base);
            Entity loaded = closeLevel.getEntity(chairId);
            if (loaded != null) loaded.discard();
        };
    }

    /** Keeps the sitter's body turned the way the chair faces; the head still looks about. */
    @Override
    public void maintain(ServerLevel level, VillagerEntityMCA villager, BlockPos spot, ResourceLocation posture,
                         Vec3 position, UUID visitId) {
        if (!villager.isPassenger()) return;
        BlockPos base = normalizeBase(level.getBlockState(spot), spot);
        float yaw = facingYaw(level.getBlockState(base));
        villager.setYBodyRot(yaw);
        villager.setYRot(yaw);
    }

    private static void face(VillagerEntityMCA villager, float yaw) {
        villager.setYRot(yaw);
        villager.setYBodyRot(yaw);
        villager.setYHeadRot(yaw);
    }

    /** The way a seated person looks: the chair's own facing, as Beachparty places it toward its placer. */
    private static float facingYaw(BlockState state) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty("facing");
        if (property == null) return 0F;
        Comparable<?> raw = state.getValue(cast(property));
        return raw instanceof net.minecraft.core.Direction direction && direction.getAxis().isHorizontal()
                ? direction.toYRot() : 0F;
    }

    /** Beachparty seats at block Y + .25 + each block's authored extra height. */
    public static @Nullable Double seatHeight(ResourceLocation block) { return HEIGHTS.get(block); }

    /** Normalizes either half of Beachparty's tall chairs to its occupancy key on the lower half. */
    static BlockPos normalizeBase(BlockState state, BlockPos pos) {
        Property<?> half = state.getBlock().getStateDefinition().getProperty("half");
        if (half == null) return pos;
        Comparable<?> value = state.getValue(cast(half));
        return "upper".equals(String.valueOf(value)) ? pos.below() : pos;
    }

    /** The first seat API whose util class and chair entity are both installed. */
    private static @Nullable SeatApi seatApi() {
        for (SeatApi api : APIS) {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(api.chair())) continue;
            try {
                Class.forName(api.util(), false, BeachpartyChairAdapter.class.getClassLoader());
                return api;
            } catch (ClassNotFoundException | LinkageError ignored) {
            }
        }
        return null;
    }

    private static boolean occupied(SeatApi api, ServerLevel level, BlockPos base) {
        Object value = invoke(api, "isOccupied", level, base);
        return Boolean.TRUE.equals(value);
    }

    private static boolean addNativeClaim(SeatApi api, ServerLevel level, BlockPos base, Entity chair, BlockPos previous) {
        return Boolean.TRUE.equals(invoke(api, "addChairEntity", level, base, chair, previous));
    }

    private static void removeNativeClaim(SeatApi api, ServerLevel level, BlockPos base) {
        invoke(api, "removeChairEntity", level, base);
    }

    private static @Nullable Object invoke(SeatApi api, String name, Object... args) {
        try {
            Class<?> util = Class.forName(api.util(), false, BeachpartyChairAdapter.class.getClassLoader());
            for (Method method : util.getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                return method.invoke(null, args);
            }
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException ignored) {
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Property cast(Property<?> property) { return property; }

    private static ResourceLocation id(String raw) {
        ResourceLocation value = ResourceLocation.tryParse(raw);
        if (value == null) throw new IllegalArgumentException(raw);
        return value;
    }
}
