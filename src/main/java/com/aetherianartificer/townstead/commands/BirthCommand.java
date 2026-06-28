package com.aetherianartificer.townstead.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Pregnancy;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Debug command to force an immediate birth, for testing root/heritage/genotype
 * inheritance without waiting out a gestation.
 *
 * <ul>
 *   <li>{@code /townstead birth} births from the MCA villager the caller is
 *       looking at (or the nearest one within 16 blocks).
 *   <li>{@code /townstead birth <mother>} births from an explicit mother; the
 *       co-parent is the mother's partner, or the mother herself if unpartnered.
 *   <li>{@code /townstead birth <mother> <father>} births from two explicit parents.
 * </ul>
 *
 * <p>Runs MCA's {@link Pregnancy#createChild} so the Townstead inheritance mixins
 * (diploid {@code Heredity}, litter spawning) fire exactly as on a natural birth.</p>
 */
public final class BirthCommand {
    private static final double LOOK_RANGE = 16.0;

    private BirthCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(
                Commands.literal("townstead").then(Commands.literal("birth")
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> birthAuto(c.getSource()))
                        .then(Commands.argument("mother", EntityArgument.entity())
                                .executes(c -> birthExplicit(
                                        c.getSource(),
                                        EntityArgument.getEntity(c, "mother"),
                                        null))
                                .then(Commands.argument("father", EntityArgument.entity())
                                        .executes(c -> birthExplicit(
                                                c.getSource(),
                                                EntityArgument.getEntity(c, "mother"),
                                                EntityArgument.getEntity(c, "father")))))));
    }

    private static int birthAuto(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This form must be run by a player. Supply <mother> instead."));
            return 0;
        }
        VillagerEntityMCA mother = pickLookedAtOrNearest(player);
        if (mother == null) {
            source.sendFailure(Component.literal("No MCA villager near your crosshair (within "
                    + (int) LOOK_RANGE + " blocks)."));
            return 0;
        }
        return birth(source, mother, null);
    }

    private static int birthExplicit(CommandSourceStack source, Entity mother, Entity father) {
        if (!(mother instanceof VillagerEntityMCA motherMca)) {
            source.sendFailure(Component.literal("Mother must be an MCA villager."));
            return 0;
        }
        if (father != null && !(father instanceof VillagerEntityMCA)) {
            source.sendFailure(Component.literal("Father must be an MCA villager."));
            return 0;
        }
        return birth(source, motherMca, (VillagerEntityMCA) father);
    }

    private static int birth(CommandSourceStack source, VillagerEntityMCA mother, VillagerEntityMCA father) {
        VillagerEntityMCA coParent = father != null ? father
                : mother.getRelationships().getPartner()
                        .filter(VillagerEntityMCA.class::isInstance)
                        .map(VillagerEntityMCA.class::cast)
                        .orElse(mother);

        Gender gender = mother.getRandom().nextBoolean() ? Gender.MALE : Gender.FEMALE;
        VillagerEntityMCA child = mother.getRelationships().getPregnancy().createChild(gender, coParent);
        child.setPos(mother.getX(), mother.getY(), mother.getZ());
        WorldUtils.spawnEntity(mother.level(), child, MobSpawnType.BREEDING);

        boolean selfParented = coParent == mother;
        source.sendSuccess(() -> Component.literal(mother.getName().getString()
                + (selfParented ? " (self-parented) gave birth to " : " and " + coParent.getName().getString()
                        + " gave birth to ")
                + child.getName().getString() + "."), true);
        if (selfParented && father == null) {
            source.sendSuccess(() -> Component.literal(
                    "  No partner found; inherited from a single parent. Pass <father> to test cross-parent inheritance."),
                    false);
        }
        return 1;
    }

    private static VillagerEntityMCA pickLookedAtOrNearest(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 endpoint = eye.add(look.scale(LOOK_RANGE));
        AABB sweep = new AABB(eye, endpoint).inflate(LOOK_RANGE);
        VillagerEntityMCA bestLook = null;
        double bestLookT = Double.POSITIVE_INFINITY;
        VillagerEntityMCA bestNear = null;
        double bestNearDist = Double.POSITIVE_INFINITY;
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, sweep)) {
            Vec3 toVillager = villager.position().subtract(eye);
            double along = toVillager.dot(look);
            if (along > 0 && along <= LOOK_RANGE) {
                Vec3 closestOnRay = eye.add(look.scale(along));
                double perp = villager.position().distanceTo(closestOnRay);
                if (perp <= Math.max(0.7, villager.getBbWidth()) && along < bestLookT) {
                    bestLookT = along;
                    bestLook = villager;
                }
            }
            double dist = player.distanceToSqr(villager);
            if (dist < bestNearDist) {
                bestNearDist = dist;
                bestNear = villager;
            }
        }
        if (bestLook != null) return bestLook;
        if (bestNear != null && bestNearDist <= LOOK_RANGE * LOOK_RANGE) return bestNear;
        return null;
    }
}
