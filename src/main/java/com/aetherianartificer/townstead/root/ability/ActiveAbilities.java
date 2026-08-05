package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.root.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.root.gene.types.ActiveAbilityGeneType;
import com.aetherianartificer.townstead.root.gene.types.ActiveAbilityGeneType.AiTrigger;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side activation of {@code active_ability} genes: resolves an entity's
 * expressed actives, assigns them to the pooled key slots (declared slot first,
 * then auto-fill), validates cooldown and condition, runs the action, and tracks
 * per-entity cooldowns (transient; reset on reload). Players fire via an Root
 * Ability key; villagers fire via their opt-in {@link AiTrigger}.
 */
public final class ActiveAbilities {

    public static final int POOL_SIZE = 8;

    private static final Map<UUID, Map<ResourceLocation, Long>> READY_AT = new ConcurrentHashMap<>();
    private static final int AI_INTERVAL = 10;

    private ActiveAbilities() {}

    public record Resolved(ResourceLocation geneId, ActiveAbilityGeneType.Instance instance) {}

    /** One slot-bound thing: an active ability (momentary) or a toggle ability (on/off). */
    public record Slotted(ResourceLocation geneId, int declaredSlot, GeneInstanceKind kind, Object instance) {}

    public enum GeneInstanceKind { ACTIVE, TOGGLE, INVENTORY }

    public static List<Resolved> resolve(LivingEntity entity) {
        List<Resolved> out = new ArrayList<>();
        for (Power gene : Powers.active(entity)) {
            if (gene.component() instanceof ActiveAbilityGeneType.Instance instance) {
                out.add(new Resolved(gene.id(), instance));
            }
        }
        return out;
    }

    /** Everything that wants an Root Ability key: active abilities and toggle-mode abilities. */
    public static List<Slotted> slottables(LivingEntity entity) {
        List<Slotted> out = new ArrayList<>();
        for (Power gene : Powers.active(entity)) {
            if (gene.component() instanceof ActiveAbilityGeneType.Instance active) {
                out.add(new Slotted(gene.id(), active.slot(), GeneInstanceKind.ACTIVE, active));
            } else if (gene.component() instanceof AbilityGeneType.Instance ability
                    && ability.mode() == AbilityGeneType.Mode.TOGGLE) {
                out.add(new Slotted(gene.id(), ability.slot(), GeneInstanceKind.TOGGLE, ability));
            } else if (gene.component()
                    instanceof com.aetherianartificer.townstead.root.gene.types.ToggleGeneType.Instance toggle) {
                out.add(new Slotted(gene.id(), toggle.slot(), GeneInstanceKind.TOGGLE, toggle));
            } else if (gene.component()
                    instanceof com.aetherianartificer.townstead.root.gene.types.InventoryGeneType.Instance inventory) {
                out.add(new Slotted(gene.id(), inventory.slot(), GeneInstanceKind.INVENTORY, inventory));
            }
        }
        return out;
    }

    /**
     * Map of key slot (1..POOL_SIZE) to the primary thing (active or toggle) bound there.
     * Two ACTIVEs may declare the same slot (a cast and its counter-cast gated by mutually
     * exclusive conditions): the extras stay co-bound to the declared slot rather than
     * auto-filling elsewhere, and {@link #activate} fires all of them.
     */
    public static Map<Integer, Slotted> slotMap(LivingEntity entity) {
        Map<Integer, Slotted> map = new LinkedHashMap<>();
        List<Slotted> auto = new ArrayList<>();
        for (Slotted slotted : slottables(entity)) {
            int slot = slotted.declaredSlot();
            if (slot >= 1 && slot <= POOL_SIZE) {
                if (!map.containsKey(slot)) {
                    map.put(slot, slotted);
                    continue;
                }
                if (slotted.kind() == GeneInstanceKind.ACTIVE
                        && map.get(slot).kind() == GeneInstanceKind.ACTIVE) {
                    continue; // co-bound; fired by activate(), not shown as its own slot
                }
            }
            auto.add(slotted);
        }
        int next = 1;
        for (Slotted slotted : auto) {
            while (next <= POOL_SIZE && map.containsKey(next)) next++;
            if (next > POOL_SIZE) break;
            map.put(next, slotted);
        }
        return map;
    }

    /** The player pressed the key bound to {@code slot} (1-based): fire an active, or flip a toggle. */
    public static boolean activate(ServerPlayer player, int slot) {
        Map<Integer, Slotted> map = slotMap(player);
        Slotted slotted = map.get(slot);
        if (slotted == null) return false;
        if (slotted.kind() == GeneInstanceKind.TOGGLE) {
            AbilityToggles.flip(player, slotted.geneId());
            AbilityToggles.syncTo(player);
            return true;
        }
        if (slotted.kind() == GeneInstanceKind.INVENTORY) {
            com.aetherianartificer.townstead.root.inventory.PersonalInventory.open(player, slotted.geneId(),
                    ((com.aetherianartificer.townstead.root.gene.types.InventoryGeneType.Instance) slotted.instance()).size());
            return true;
        }
        boolean fired = fire(player, new Resolved(slotted.geneId(), (ActiveAbilityGeneType.Instance) slotted.instance()));
        // Co-bound actives declared on this slot (not bound anywhere in the map themselves):
        // conditions and cooldowns decide which runs. A press does exactly ONE thing — stop at
        // the first success, or a cast that flips state (vanish) hands the very same press to
        // its counter-cast (unveil), whose condition now passes against the mutated state, and
        // the pair self-cancels within one tick.
        for (Slotted candidate : slottables(player)) {
            if (fired) break;
            if (candidate.kind() != GeneInstanceKind.ACTIVE || candidate.declaredSlot() != slot) continue;
            if (map.containsValue(candidate)) continue;
            fired = fire(player, new Resolved(candidate.geneId(), (ActiveAbilityGeneType.Instance) candidate.instance()));
        }
        return fired;
    }

    /**
     * Villager auto-use, throttled: fires each opt-in active whose trigger and gate
     * pass, and manages opt-in toggle genes (held ON while their trigger is true,
     * released when it stops — a mender switching on beside a hurt neighbour).
     */
    public static void aiTick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;
        if ((villager.level().getGameTime() + villager.getId()) % AI_INTERVAL != 0) return;
        List<Resolved> actives = new ArrayList<>();
        boolean toggleChanged = false;
        for (Power gene : Powers.active(villager)) {
            if (gene.component() instanceof ActiveAbilityGeneType.Instance instance) {
                actives.add(new Resolved(gene.id(), instance));
            } else if (gene.component()
                    instanceof com.aetherianartificer.townstead.root.gene.types.ToggleGeneType.Instance toggle
                    && toggle.aiTrigger() != AiTrigger.NEVER) {
                boolean want = shouldAiUse(villager, toggle.aiTrigger());
                if (want != AbilityToggles.isOn(villager, gene.id())) {
                    AbilityToggles.set(villager, gene.id(), want);
                    toggleChanged = true;
                }
            }
        }
        if (toggleChanged) AbilityToggles.syncEntity(villager);
        for (Resolved resolved : actives) {
            if (resolved.instance().aiTrigger() == AiTrigger.NEVER) continue;
            if (!shouldAiUse(villager, resolved.instance().aiTrigger())) continue;
            fire(villager, resolved);
        }
    }

    private static boolean shouldAiUse(VillagerEntityMCA villager, AiTrigger trigger) {
        return switch (trigger) {
            case ALWAYS -> true;
            case WHEN_HURT -> villager.getHealth() < villager.getMaxHealth() * 0.5f;
            case WHEN_THREATENED -> villager.getTarget() != null || villager.getLastHurtByMob() != null;
            case WHEN_FLYING -> GlideAI.wantsLift(villager);
            case WHEN_HURT_NEARBY -> hurtNonHostileNearby(villager);
            case NEVER -> false;
        };
    }

    /** A hurt villager, player, or animal close enough that a helping aura would reach soon. */
    private static boolean hurtNonHostileNearby(VillagerEntityMCA villager) {
        var nearby = villager.level().getEntitiesOfClass(LivingEntity.class,
                villager.getBoundingBox().inflate(5.0));
        for (LivingEntity candidate : nearby) {
            if (candidate == villager || candidate instanceof net.minecraft.world.entity.monster.Enemy) continue;
            if (candidate.getHealth() < candidate.getMaxHealth() - 0.01f) return true;
        }
        return false;
    }

    private static boolean fire(LivingEntity entity, Resolved resolved) {
        long now = entity.level().getGameTime();
        if (!isReady(entity, resolved.geneId(), now)) return false;
        ActiveAbilityGeneType.Instance instance = resolved.instance();
        if (instance.condition() != null && !instance.condition().test(new ConditionContext(entity))) return false;
        if (instance.costResource() != null
                && ResourceValues.get(entity, instance.costResource()) < instance.costAmount()) {
            return false;
        }
        ActionContext actionContext = new ActionContext(entity);
        instance.action().run(actionContext);
        // Failed effects (currently safe teleports with no valid destination) are not activations:
        // do not charge their resource and do not begin their cooldown.
        if (!actionContext.succeeded()) return false;
        if (instance.costResource() != null) {
            ResourceValues.change(entity, instance.costResource(), -instance.costAmount());
        }
        setCooldown(entity, resolved.geneId(), now + instance.cooldownTicks());
        return true;
    }

    private static boolean isReady(LivingEntity entity, ResourceLocation geneId, long now) {
        Map<ResourceLocation, Long> map = READY_AT.get(entity.getUUID());
        return map == null || map.getOrDefault(geneId, 0L) <= now;
    }

    private static void setCooldown(LivingEntity entity, ResourceLocation geneId, long readyAt) {
        READY_AT.computeIfAbsent(entity.getUUID(), k -> new ConcurrentHashMap<>()).put(geneId, readyAt);
    }

    public static void clear(UUID uuid) {
        READY_AT.remove(uuid);
    }
}
