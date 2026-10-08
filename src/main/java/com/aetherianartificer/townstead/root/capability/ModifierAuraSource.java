package com.aetherianartificer.townstead.root.capability;

import com.aetherianartificer.townstead.pheno.capability.CapabilityCollector;
import com.aetherianartificer.townstead.pheno.capability.CapabilityKey;
import com.aetherianartificer.townstead.pheno.capability.CapabilitySource;
import com.aetherianartificer.townstead.pheno.capability.Provenance;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.gene.types.ModifierGeneType;
import com.aetherianartificer.townstead.root.modifier.ModifierCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contributes {@code pheno:modifier} auras ({@code applies_to}) to the creatures around their
 * bearers. Bearers are indexed once a second, so resolving any capability for any entity costs
 * nothing extra while no aura is active nearby. The bearer's own share comes from
 * {@link GeneCapabilitySource}.
 */
public final class ModifierAuraSource implements CapabilitySource {

    private record Emitter(UUID bearer, Vec3 pos, ResourceLocation powerId, ModifierGeneType.Instance modifier) {}

    private static final int SCAN_INTERVAL = 20;
    private static final Map<ResourceLocation, List<Emitter>> BY_LEVEL = new ConcurrentHashMap<>();

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % SCAN_INTERVAL != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            List<Emitter> emitters = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
                // Powers live on players and villagers; resolving every mob each second would not.
                if (!(living instanceof net.minecraft.world.entity.player.Player)
                        && !(living instanceof net.conczin.mca.entity.VillagerEntityMCA)) continue;
                ConditionContext ctx = null;
                for (Power power : Powers.active(living)) {
                    if (!(power.component() instanceof ModifierGeneType.Instance modifier)
                            || modifier.aura() == null) continue;
                    if (modifier.condition() != null) {
                        if (ctx == null) ctx = new ConditionContext(living);
                        if (!modifier.condition().test(ctx)) continue;
                    }
                    emitters.add(new Emitter(living.getUUID(), living.position(), power.id(), modifier));
                }
            }
            ResourceLocation key = level.dimension().location();
            if (emitters.isEmpty()) BY_LEVEL.remove(key);
            else BY_LEVEL.put(key, List.copyOf(emitters));
        }
    }

    @Override
    public void contribute(LivingEntity entity, CapabilityCollector out) {
        if (entity.level().isClientSide) return;
        List<Emitter> emitters = BY_LEVEL.get(entity.level().dimension().location());
        if (emitters == null) return;
        ConditionContext ctx = null;
        for (Emitter emitter : emitters) {
            if (emitter.bearer().equals(entity.getUUID())) continue;
            ModifierGeneType.Instance modifier = emitter.modifier();
            double radius = modifier.aura().radius();
            if (entity.position().distanceToSqr(emitter.pos()) > radius * radius) continue;
            boolean active = modifier.appliesToItem(entity.level(), out.subjectItem());
            if (active && modifier.aura().condition() != null) {
                if (ctx == null) ctx = new ConditionContext(entity);
                active = modifier.aura().condition().test(ctx);
            }
            CapabilityKey key = ModifierCapability.key(modifier.modifier(), modifier.discriminator());
            for (ModifierGeneType.Mod mod : modifier.mods()) {
                out.numeric(key, ModifierCapability.op(mod.op()), mod.value(),
                        Provenance.gene(emitter.powerId()), active);
            }
        }
    }
}
