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

/** Reflection-safe bridge to Beachparty's own chair entity and occupancy ledger. */
public final class BeachpartyChairAdapter implements HangoutEmbodiment.PostureAdapter {
    public static final ResourceLocation ID = ResourceLocation.tryParse("beachparty:native_chair");
    private static final ResourceLocation CHAIR = ResourceLocation.tryParse("beachparty:chair");
    private static final Map<ResourceLocation, Double> HEIGHTS = Map.of(
            id("beachparty:beach_chair"), 0.30D,
            id("beachparty:hooded_beach_chair"), 0.45D,
            id("beachparty:palm_chair"), 0.55D,
            id("beachparty:palm_bar_stool"), 0.60D);
    private static final String UTIL = "net.satisfy.beachparty.core.util.BeachpartyUtil";

    @Override public ResourceLocation id() { return ID; }

    @Override
    public @Nullable HangoutEmbodiment.Handle enter(ServerLevel level, VillagerEntityMCA villager,
                                                     BlockPos spot, ResourceLocation posture,
                                                     Vec3 position, UUID session) {
        BlockPos base = normalizeBase(level.getBlockState(spot), spot);
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(base).getBlock());
        Double height = HEIGHTS.get(block);
        if (height == null) return null;
        if (occupied(level, base)) return HangoutEmbodiment.BLOCKED;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(CHAIR).orElse(null);
        if (type == null) return null;
        Entity chair = type.create(level);
        if (chair == null) return null;
        chair.moveTo(base.getX() + 0.5D, base.getY() + height, base.getZ() + 0.5D, 0F, 0F);
        HangoutEmbodiment.markSessionAnchor(chair, session);
        if (!addNativeClaim(level, base, chair, villager.blockPosition())) {
            chair.discard();
            return HangoutEmbodiment.BLOCKED;
        }
        if (!level.addFreshEntity(chair) || !villager.startRiding(chair, true)) {
            removeNativeClaim(level, base);
            chair.discard();
            return HangoutEmbodiment.BLOCKED;
        }
        UUID chairId = chair.getUUID();
        return (closeLevel, closeVillager) -> {
            if (closeVillager.isPassenger() && closeVillager.getVehicle() != null
                    && chairId.equals(closeVillager.getVehicle().getUUID())) closeVillager.stopRiding();
            removeNativeClaim(closeLevel, base);
            Entity loaded = closeLevel.getEntity(chairId);
            if (loaded != null) loaded.discard();
        };
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

    private static boolean occupied(ServerLevel level, BlockPos base) {
        Object value = invoke("isOccupied", level, base);
        return Boolean.TRUE.equals(value);
    }

    private static boolean addNativeClaim(ServerLevel level, BlockPos base, Entity chair, BlockPos previous) {
        return Boolean.TRUE.equals(invoke("addChairEntity", level, base, chair, previous));
    }

    private static void removeNativeClaim(ServerLevel level, BlockPos base) {
        invoke("removeChairEntity", level, base);
    }

    private static @Nullable Object invoke(String name, Object... args) {
        try {
            Class<?> util = Class.forName(UTIL, false, BeachpartyChairAdapter.class.getClassLoader());
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
