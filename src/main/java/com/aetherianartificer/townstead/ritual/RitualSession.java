package com.aetherianartificer.townstead.ritual;

import com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps;
import com.aetherianartificer.townstead.performance.PerformanceProviders;
import com.aetherianartificer.townstead.performance.PerformanceRequest;
import com.aetherianartificer.townstead.politics.order.Orders;
import com.aetherianartificer.townstead.politics.state.Faction;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One ritual under way at one altar. Everyone first walks to their place, laid out from the altar
 * toward the candidate's side; then the steps play in order while the actors are held in place,
 * facing the altar. It breaks off cleanly (nothing gained, the offering returned) if anyone is hurt,
 * leaves, or a player candidate sneaks away.
 */
final class RitualSession {
    private static final int GATHER_LIMIT = 300;
    private static final double IN_PLACE_SQR = 0.8 * 0.8;
    private static final double DRIFT_SQR = 0.6 * 0.6;
    private static final int PRIORITY = 90;
    private static final double SPEECH_RANGE = 24;

    final RitualDefinition ritual;
    final ServerLevel level;
    final BlockPos altar;
    final Faction order;
    final LivingEntity officiant;
    final LivingEntity candidate;
    final List<LivingEntity> witnesses;
    private final Map<LivingEntity, Vec3> spots = new LinkedHashMap<>();
    private final Vec3 center;
    private ItemStack offering = ItemStack.EMPTY;
    private int gathered = -1;
    private int gatherTicks;
    private boolean over;

    RitualSession(RitualDefinition ritual, ServerLevel level, BlockPos altar, Faction order,
                  LivingEntity officiant, LivingEntity candidate, List<LivingEntity> witnesses) {
        this.ritual = ritual;
        this.level = level;
        this.altar = altar;
        this.order = order;
        this.officiant = officiant;
        this.candidate = candidate;
        this.witnesses = List.copyOf(witnesses);
        this.center = Vec3.atBottomCenterOf(altar);
        Direction side = Direction.getNearest(candidate.getX() - center.x, 0, candidate.getZ() - center.z);
        if (side.getAxis().isVertical()) side = Direction.SOUTH;
        Vec3 out = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 across = new Vec3(-out.z, 0, out.x);
        spots.put(candidate, center.add(out.scale(1.5)));
        spots.put(officiant, center.subtract(out.scale(1.2)));
        for (int i = 0; i < this.witnesses.size(); i++) {
            double lateral = (i / 2 + 1) * 1.2 * (i % 2 == 0 ? 1 : -1);
            spots.put(this.witnesses.get(i), center.add(out.scale(3.2)).add(across.scale(lateral)));
        }
        takeOffering();
    }

    boolean over() {
        return over;
    }

    boolean involves(LivingEntity entity) {
        return spots.containsKey(entity);
    }

    void tick() {
        if (over) return;
        if (!officiant.isAlive() || !candidate.isAlive() || officiant.hurtTime > 0 || candidate.hurtTime > 0) {
            fail("ritual.townstead.broken.hurt");
            return;
        }
        if (candidate instanceof Player player && player.isShiftKeyDown()) {
            fail("ritual.townstead.broken.backed_out");
            return;
        }
        if (gathered < 0) {
            gather();
            return;
        }
        hold();
        int t = gathered++;
        for (RitualDefinition.Step step : ritual.steps()) {
            if (step.at() == t) play(step);
        }
        if (t >= ritual.duration()) complete();
    }

    private void gather() {
        boolean ready = true;
        for (Map.Entry<LivingEntity, Vec3> entry : spots.entrySet()) {
            LivingEntity actor = entry.getKey();
            Vec3 spot = entry.getValue();
            if (actor.position().distanceToSqr(spot) <= IN_PLACE_SQR) continue;
            if (actor instanceof ServerPlayer player) {
                // A player comes close on their own; the last step onto the spot is taken for them.
                if (player.position().distanceToSqr(spot) <= 9) place(player, spot);
                else if (actor == candidate) ready = false;
                continue;
            }
            if (actor instanceof VillagerEntityMCA villager) {
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(BlockPos.containing(spot), 0.5f, 0));
            }
            if (actor == officiant || actor == candidate) ready = false;
        }
        if (ready) {
            gathered = 0;
            return;
        }
        if (++gatherTicks > GATHER_LIMIT) fail("ritual.townstead.broken.absent");
    }

    /** Keeps everyone on their spot, facing the altar. */
    private void hold() {
        for (Map.Entry<LivingEntity, Vec3> entry : spots.entrySet()) {
            LivingEntity actor = entry.getKey();
            if (!actor.isAlive()) continue;
            Vec3 spot = entry.getValue();
            if (actor instanceof ServerPlayer player) {
                if (player.position().distanceToSqr(spot) > DRIFT_SQR) place(player, spot);
                continue;
            }
            if (actor instanceof VillagerEntityMCA villager) {
                villager.getNavigation().stop();
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                if (villager.position().distanceToSqr(spot) > DRIFT_SQR) villager.moveTo(spot.x, spot.y, spot.z);
            }
            float yaw = facing(actor.position());
            actor.setYRot(yaw);
            actor.setYHeadRot(yaw);
            actor.setYBodyRot(yaw);
        }
    }

    private void place(ServerPlayer player, Vec3 spot) {
        float yaw = facing(spot);
        //? if >=1.21 {
        player.teleportTo(level, spot.x, spot.y, spot.z, yaw, player.getXRot());
        //?} else {
        /*player.teleportTo(level, spot.x, spot.y, spot.z, yaw, player.getXRot());
        *///?}
    }

    private float facing(Vec3 from) {
        return (float) (Mth.atan2(center.z - from.z, center.x - from.x) * Mth.RAD_TO_DEG) - 90f;
    }

    private void play(RitualDefinition.Step step) {
        for (LivingEntity actor : actors(step.role())) {
            if (step.clip() != null && actor.isAlive()) {
                int priority = PRIORITY + ("ritual".equals(step.channel()) ? 0 : 10);
                PerformanceProviders.play(level, new PerformanceRequest(actor, step.clip(), step.channel(), step.ticks(),
                        priority, PerformanceRequest.Fallback.STAND));
            }
            String line = step.line();
            for (RitualDefinition.LineVariant variant : step.lines()) {
                if (variant.holds(actor, actor == candidate ? officiant : candidate)) {
                    line = variant.line();
                    break;
                }
            }
            if (line != null) speak(actor, line);
        }
        if (step.sound() != null) {
            //? if >=1.21 {
            BuiltInRegistries.SOUND_EVENT.getOptional(step.sound())
                    .ifPresent(sound -> level.playSound(null, altar, sound, SoundSource.BLOCKS, 1.0f, 0.9f));
            //?} else {
            /*BuiltInRegistries.SOUND_EVENT.getOptional(step.sound())
                    .ifPresent(sound -> level.playSound(null, altar, sound, SoundSource.BLOCKS, 1.0f, 0.9f));
            *///?}
        }
    }

    private List<LivingEntity> actors(RitualDefinition.Role role) {
        return switch (role) {
            case OFFICIANT -> List.of(officiant);
            case CANDIDATE -> List.of(candidate);
            case WITNESSES -> witnesses;
            case ALL -> List.copyOf(spots.keySet());
        };
    }

    private void speak(LivingEntity speaker, String line) {
        Component text = Component.translatable("ritual.townstead.speech", speaker.getName(),
                Component.translatable(line, order.name()));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= SPEECH_RANGE * SPEECH_RANGE) player.sendSystemMessage(text);
        }
    }

    private void takeOffering() {
        RitualDefinition.Offering wanted = ritual.offering();
        if (wanted == null) return;
        ItemStack held = candidate.getMainHandItem();
        if (!Blessings.accepts(held, wanted)) return;
        offering = held.copy();
        candidate.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private void returnOffering(boolean blessed) {
        if (offering.isEmpty()) return;
        ItemStack back = blessed && ritual.offering() != null
                ? Blessings.bless(offering, ritual.offering().against(), order.name()) : offering;
        if (candidate.getMainHandItem().isEmpty()) {
            candidate.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, back);
        } else if (candidate instanceof Player player && !player.getInventory().add(back)) {
            player.drop(back, false);
        } else if (!(candidate instanceof Player)) {
            candidate.spawnAtLocation(back);
        }
        offering = ItemStack.EMPTY;
    }

    private void complete() {
        over = true;
        if (ritual.outcome().joinOrder()) Orders.swear(level, order, candidate.getUUID());
        com.aetherianartificer.townstead.chronicle.Chronicles.addCounter(level.getServer(), officiant.getUUID(),
                ritual.id().getNamespace() + ":officiated/" + ritual.id().getPath(), 1);
        if (ritual.outcome().chronicle() != null && candidate instanceof VillagerEntityMCA) {
            ChronicleTaps.survival(candidate, ritual.outcome().chronicle(), Map.of("order", order.name()));
        }
        returnOffering(true);
        announce(Component.translatable("ritual.townstead.completed", candidate.getName(), order.name()));
    }

    void fail(String reason) {
        if (over) return;
        over = true;
        returnOffering(false);
        announce(Component.translatable(reason, candidate.getName()));
    }

    private void announce(Component message) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= SPEECH_RANGE * SPEECH_RANGE) player.displayClientMessage(message, false);
        }
    }
}
