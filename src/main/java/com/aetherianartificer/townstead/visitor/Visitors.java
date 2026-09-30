package com.aetherianartificer.townstead.visitor;

import com.aetherianartificer.townstead.journey.Journeys;
import com.aetherianartificer.townstead.persona.PersonaService;
import com.aetherianartificer.townstead.pheno.state.EntityStates;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One-off visitors that a story brings to town: an ordinary villager made with given states, who
 * walks in from the edge to the player. A visitor carries a role (such as {@code fledgling}) so the
 * story can find them, and is not a resident until the story settles them. A visitor who is sent
 * away walks out of town and is gone.
 */
public final class Visitors {
    private static final String ROLE = "townstead:visitor";
    private static final String PARTY = "townstead:visitor_party";

    private Visitors() {}

    /** Who to make: states to set, and optionally a gender, a profession and a thrall who comes with them. */
    public record Spec(String role, Map<ResourceLocation, Double> states, @Nullable String gender,
                       @Nullable ResourceLocation profession, @Nullable Spec thrall) {}

    /** Brings a visitor (and their thrall) in from the edge of {@code village}, walking to {@code player}. */
    public static @Nullable VillagerEntityMCA arrive(ServerPlayer player, Village village, Spec spec) {
        ServerLevel level = player.serverLevel();
        BlockPos at = PersonaService.arrivalPoint(level, village, player);
        VillagerEntityMCA visitor = make(level, at, spec);
        if (visitor == null) return null;
        PersonaService.walkTo(visitor, player);
        if (spec.thrall() != null) {
            VillagerEntityMCA thrall = make(level, at, spec.thrall());
            if (thrall != null) {
                visitor.getPersistentData().putUUID(PARTY, thrall.getUUID());
                thrall.getPersistentData().putUUID(PARTY, visitor.getUUID());
                com.aetherianartificer.townstead.compat.vampirism.Thralls.bind(visitor, thrall);
                PersonaService.walkTo(thrall, player);
            }
        }
        return visitor;
    }

    private static @Nullable VillagerEntityMCA make(ServerLevel level, BlockPos at, Spec spec) {
        VillagerFactory factory = VillagerFactory.newVillager(level).withAge(0).withPosition(Vec3.atBottomCenterOf(at));
        if ("male".equals(spec.gender())) factory.withGender(Gender.MALE);
        else if ("female".equals(spec.gender())) factory.withGender(Gender.FEMALE);
        if (spec.profession() != null && BuiltInRegistries.VILLAGER_PROFESSION.containsKey(spec.profession())) {
            factory.withProfession(BuiltInRegistries.VILLAGER_PROFESSION.get(spec.profession()));
        }
        VillagerEntityMCA villager = factory.build();
        //? if >=1.21 {
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        //?} else {
        /*villager.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        *///?}
        villager.getPersistentData().putString(ROLE, spec.role());
        if (!level.addFreshEntity(villager)) return null;
        for (Map.Entry<ResourceLocation, Double> state : spec.states().entrySet()) {
            EntityStates.set(villager, state.getKey(), state.getValue(), 0, null);
        }
        return villager;
    }

    /** The visitor role of {@code entity}, or null when they are not a visitor. */
    public static @Nullable String role(Entity entity) {
        String role = entity.getPersistentData().getString(ROLE);
        return role.isEmpty() ? null : role;
    }

    /** The nearest loaded visitor with {@code role} within {@code radius} of {@code around}. */
    public static @Nullable VillagerEntityMCA near(LivingEntity around, String role, double radius) {
        List<VillagerEntityMCA> found = around.level().getEntitiesOfClass(VillagerEntityMCA.class,
                new AABB(around.blockPosition()).inflate(radius), v -> v.isAlive() && role.equals(role(v)));
        VillagerEntityMCA best = null;
        for (VillagerEntityMCA v : found) {
            if (best == null || v.distanceToSqr(around) < best.distanceToSqr(around)) best = v;
        }
        return best;
    }

    /** The visitor stays: they stop being a visitor and look for a home here, with their party. */
    public static void settle(VillagerEntityMCA visitor) {
        for (VillagerEntityMCA member : party(visitor)) {
            member.getPersistentData().remove(ROLE);
            member.getPersistentData().remove(PARTY);
            member.getResidency().seekHome();
        }
    }

    /** The visitor leaves town for good, with their party. */
    public static void dismiss(VillagerEntityMCA visitor) {
        for (VillagerEntityMCA member : party(visitor)) {
            member.getPersistentData().remove(ROLE);
            Journeys.leave(member);
        }
    }

    private static List<VillagerEntityMCA> party(VillagerEntityMCA visitor) {
        if (!visitor.getPersistentData().hasUUID(PARTY) || !(visitor.level() instanceof ServerLevel level)) return List.of(visitor);
        UUID other = visitor.getPersistentData().getUUID(PARTY);
        return level.getEntity(other) instanceof VillagerEntityMCA companion && companion.isAlive()
                ? List.of(visitor, companion) : List.of(visitor);
    }
}
