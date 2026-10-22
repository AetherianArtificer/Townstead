package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.root.ability.GlideAI;
import com.aetherianartificer.townstead.root.locomotion.FlightProfile;
import com.aetherianartificer.townstead.root.locomotion.Locomotion;
import com.aetherianartificer.townstead.root.locomotion.SwimProfile;
import com.aetherianartificer.townstead.root.locomotion.plan.AirRoute;
import com.aetherianartificer.townstead.root.locomotion.plan.AirRoutePlanner;
import com.aetherianartificer.townstead.root.locomotion.plan.SwimRoute;
import com.aetherianartificer.townstead.root.locomotion.plan.SwimRoutePlanner;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * {@code /townstead debug glide} — gate-by-gate flight report for villagers near the
 * executing player, so "they just stand there" separates into its causes: no
 * flight gene, no lift ability, a travel target outside the launch envelope, a
 * blocked line, or a joy-target search starved by cramped terrain.
 *
 * <p>{@code /townstead debug flightplan} plans a flight for the nearest flier to the
 * block the player looks at (else to the flier's own destination) and draws the route:
 * end-rod sparks along the legs, green sparks on the landing spot. {@code swimplan} does
 * the same for the nearest swimmer, through water.</p>
 */
public final class GlideDiagnosticCommand {

    private static final double RANGE = 16.0;
    private static final int MAX_REPORTS = 5;

    private GlideDiagnosticCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("debug")
                .then(Commands.literal("glide").executes(c -> dump(c.getSource())))
                .then(Commands.literal("flightplan").executes(c -> flightPlan(c.getSource())))
                .then(Commands.literal("swimplan").executes(c -> swimPlan(c.getSource())))));
    }

    private static int dump(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<VillagerEntityMCA> villagers = player.level().getEntitiesOfClass(
                VillagerEntityMCA.class, player.getBoundingBox().inflate(RANGE));
        if (villagers.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                    "command.townstead.glide.no_villagers", (int) RANGE), false);
            return 0;
        }
        villagers.sort(Comparator.comparingDouble(v -> v.distanceToSqr(player)));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < villagers.size() && i < MAX_REPORTS; i++) {
            if (i > 0) out.append('\n');
            out.append(GlideAI.diagnose(villagers.get(i)));
        }
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int flightPlan(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        VillagerEntityMCA flier = null;
        FlightProfile profile = null;
        List<VillagerEntityMCA> villagers = player.level().getEntitiesOfClass(
                VillagerEntityMCA.class, player.getBoundingBox().inflate(RANGE));
        villagers.sort(Comparator.comparingDouble(v -> v.distanceToSqr(player)));
        for (VillagerEntityMCA villager : villagers) {
            profile = FlightProfile.of(villager);
            if (profile != null) {
                flier = villager;
                break;
            }
        }
        if (flier == null) {
            source.sendFailure(Component.literal("No flier within " + (int) RANGE + " blocks."));
            return 0;
        }
        BlockPos destination = null;
        HitResult hit = player.pick(96.0, 1.0f, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            destination = block.getBlockPos().relative(block.getDirection());
        }
        if (destination == null) destination = Locomotion.destination(flier);
        if (destination == null) {
            source.sendFailure(Component.literal("Look at a block, or wait until the flier has somewhere to go."));
            return 0;
        }
        AirRoutePlanner.Result result = AirRoutePlanner.plan(flier, profile, destination);
        StringBuilder out = new StringBuilder(flier.getName().getString()).append(": ").append(profile.describe())
                .append(" to ").append(destination.toShortString())
                .append(String.format(" (%d cells, %.1f ms)", result.expansions(), result.micros() / 1000.0));
        AirRoute route = result.route();
        if (route == null) {
            out.append("\n  NO ROUTE: ").append(result.failure());
        } else {
            out.append(String.format("\n  %d waypoints, %.0f blocks, lift %.1f of %s, land %s%s",
                    route.waypoints().size(), route.length(), route.lift(),
                    Double.isInfinite(route.liftBudget()) ? "unlimited" : String.format("%.0f", route.liftBudget()),
                    route.landing().toShortString(), route.perch() ? " (perch: flies down after)" : ""));
            draw((ServerLevel) player.level(), player, route.waypoints(), Vec3.atBottomCenterOf(route.landing()));
        }
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return route == null ? 0 : 1;
    }

    private static int swimPlan(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        VillagerEntityMCA swimmer = null;
        SwimProfile profile = null;
        List<VillagerEntityMCA> villagers = player.level().getEntitiesOfClass(
                VillagerEntityMCA.class, player.getBoundingBox().inflate(RANGE));
        villagers.sort(Comparator.comparingDouble(v -> v.distanceToSqr(player)));
        for (VillagerEntityMCA villager : villagers) {
            profile = SwimProfile.of(villager);
            if (profile != null) {
                swimmer = villager;
                break;
            }
        }
        if (swimmer == null) {
            source.sendFailure(Component.literal("No swimmer within " + (int) RANGE + " blocks."));
            return 0;
        }
        BlockPos destination = null;
        HitResult hit = player.pick(96.0, 1.0f, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            destination = block.getBlockPos().relative(block.getDirection());
        }
        if (destination == null) destination = Locomotion.destination(swimmer);
        if (destination == null) {
            source.sendFailure(Component.literal("Look at a block, or wait until the swimmer has somewhere to go."));
            return 0;
        }
        SwimRoutePlanner.Result result = SwimRoutePlanner.plan(swimmer, profile, destination);
        StringBuilder out = new StringBuilder(swimmer.getName().getString()).append(": ")
                .append(com.aetherianartificer.townstead.root.locomotion.SwimAI.describe(swimmer))
                .append("\n  plan to ").append(destination.toShortString())
                .append(String.format(" (%d cells, %.1f ms)", result.expansions(), result.micros() / 1000.0));
        SwimRoute route = result.route();
        if (route == null) {
            out.append("\n  NO ROUTE: ").append(result.failure());
        } else {
            out.append(String.format("\n  %d waypoints, %.0f blocks, longest breath hold %dt of %dt air%s",
                    route.waypoints().size(), route.length(), route.longestHold(), swimmer.getAirSupply(),
                    route.toShore() ? ", ends at the shore" : ""));
            draw((ServerLevel) player.level(), player, route.waypoints(), route.end());
        }
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return route == null ? 0 : 1;
    }

    private static void draw(ServerLevel level, ServerPlayer player, List<Vec3> points, Vec3 end) {
        for (int i = 1; i < points.size(); i++) {
            Vec3 a = points.get(i - 1).add(0, 0.5, 0);
            Vec3 b = points.get(i).add(0, 0.5, 0);
            int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 0.5));
            for (int k = 0; k <= steps; k++) {
                Vec3 p = a.lerp(b, (double) k / steps);
                spark(level, player, ParticleTypes.END_ROD, p, 1);
            }
            spark(level, player, ParticleTypes.FLAME, a, 3);
        }
        spark(level, player, ParticleTypes.HAPPY_VILLAGER, end.add(0, 0.3, 0), 12);
    }

    private static void spark(ServerLevel level, ServerPlayer player, ParticleOptions type, Vec3 at, int count) {
        level.sendParticles(player, type, true, at.x, at.y, at.z, count, 0.05, 0.05, 0.05, 0.0);
    }
}
