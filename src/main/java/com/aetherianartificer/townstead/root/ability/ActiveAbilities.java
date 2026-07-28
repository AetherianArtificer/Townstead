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

    /**
     * Prepared slots. Twenty-four, drawn as THREE LAYERS of eight on one dial: the wheel shows 1-8,
     * shift shows 9-16, and control shows 17-24, all in the same eight positions.
     *
     * <p>Layers rather than modes. Switching sets would mean slot 3 quietly meaning something else
     * until you switched back, and an arrangement you have built muscle memory for must never move
     * under your fingers. Held, it cannot: let go and you are on the first eight again.</p>
     *
     * <p>Three is where this stops. A fourth would need a modifier nobody has a spare finger for,
     * and twenty-four prepared is already well past the working set the whole idea of preparing
     * exists to keep small.</p>
     *
     * <p>Only the first eight have keys ({@code TownsteadKeybinds.ABILITY_KEYS}); the upper layers
     * are reachable through the wheel. That asymmetry is deliberate rather than an oversight.</p>
     */
    public static final int POOL_SIZE = 24;
    /** How many slots one turn of the dial shows. */
    public static final int LAYER_SIZE = 8;

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
        Map<Integer, ResourceLocation> prepared = preparedLoadout(entity);
        if (!prepared.isEmpty()) return preparedMap(entity, prepared);
        return declaredMap(entity);
    }

    /**
     * The player's own arrangement, slot by slot.
     *
     * <p>Root abilities and career skills share one id space (a skill's power is keyed by its skill
     * id, a gene's by its gene id), so one map covers both without knowing the difference. A
     * prepared ability the entity no longer owns leaves its slot EMPTY rather than closing the gap:
     * a respec must not move the other seven abilities under the player's fingers.</p>
     */
    private static Map<Integer, Slotted> preparedMap(LivingEntity entity,
                                                     Map<Integer, ResourceLocation> prepared) {
        List<Slotted> slottables = slottables(entity);
        Map<Integer, Slotted> map = new LinkedHashMap<>();
        for (int slot = 1; slot <= POOL_SIZE; slot++) {
            ResourceLocation id = prepared.get(slot);
            if (id == null) continue;
            for (Slotted slotted : slottables) {
                if (slotted.geneId().equals(id)) {
                    map.put(slot, slotted);
                    break;
                }
            }
        }
        return map;
    }

    /** What a player has prepared, or empty for anyone who cannot prepare (villagers use AI). */
    private static Map<Integer, ResourceLocation> preparedLoadout(LivingEntity entity) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player)) return Map.of();
        com.aetherianartificer.townstead.profession.career.CareerProfile profile =
                com.aetherianartificer.townstead.profession.career.CareerProfiles.of(entity);
        return profile == null ? Map.of() : profile.activeLoadout();
    }

    /**
     * The pre-preparation default: declared slots first, then auto-fill.
     *
     * <p>Kept as the bootstrap so a player who has never opened the loadout still has their first
     * few abilities on keys. It TRUNCATES past {@link #POOL_SIZE}, which is the whole reason
     * preparing exists: with hundreds owned this can only ever reach the first eight, and it cannot
     * tell the player which eight it picked.</p>
     */
    private static Map<Integer, Slotted> declaredMap(LivingEntity entity) {
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

    /**
     * Records the player's prepared order, keeping only what they actually own.
     *
     * <p>Validated against {@link #slottables} rather than against a learned-skill list, because
     * that is the same source the slots resolve from: anything that cannot appear in a slot map has
     * no business being stored as though it could. Duplicates and unknown ids are dropped rather
     * than refused, so a stale client sending an ability you have since respecced out of costs you
     * that entry and nothing else.</p>
     */
    public static void prepare(ServerPlayer player, Map<Integer, ResourceLocation> bySlot) {
        java.util.Set<ResourceLocation> owned = new java.util.LinkedHashSet<>();
        for (Slotted slotted : slottables(player)) owned.add(slotted.geneId());
        Map<Integer, ResourceLocation> valid = new LinkedHashMap<>();
        for (Map.Entry<Integer, ResourceLocation> entry : bySlot.entrySet()) {
            ResourceLocation id = entry.getValue();
            if (id != null && owned.contains(id)) valid.put(entry.getKey(), id);
        }
        com.aetherianartificer.townstead.profession.career.PlayerCareers.mutate(player,
                profile -> profile.setActiveLoadout(valid, POOL_SIZE));
        // Echo the arrangement the server actually kept, not the one that was asked for: dropped
        // entries have to show up on the wheel or the player is editing a fiction.
        syncView(player);
    }

    /** The player pressed the key bound to {@code slot} (1-based): fire an active, or flip a toggle. */
    public static boolean activate(ServerPlayer player, int slot) {
        Map<Integer, Slotted> map = slotMap(player);
        Slotted slotted = map.get(slot);
        if (slotted == null) return false;
        if (slotted.kind() == GeneInstanceKind.TOGGLE) {
            AbilityToggles.flip(player, slotted.geneId());
            AbilityToggles.syncTo(player);
            syncView(player);
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
        // Pairs are matched on the FIRED ABILITY's declared slot, not on the key that was pressed.
        // Those were only ever the same number while nothing moved: auto-fill could already place an
        // ability on a key other than the one it declared, and a prepared loadout makes the two
        // axes independent by design. Comparing against the key slot co-fired whichever unrelated
        // ability happened to declare that number.
        int pairSlot = slotted.declaredSlot();
        for (Slotted candidate : slottables(player)) {
            if (fired || pairSlot < 1) break;
            if (candidate.kind() != GeneInstanceKind.ACTIVE) continue;
            if (candidate.equals(slotted) || candidate.declaredSlot() != pairSlot) continue;
            if (map.containsValue(candidate)) continue;
            fired = fire(player, new Resolved(candidate.geneId(), (ActiveAbilityGeneType.Instance) candidate.instance()));
        }
        // A cooldown that just started is the wheel's most time-sensitive fact, so it goes back
        // immediately rather than waiting for whatever next happens to sync.
        if (fired) syncView(player);
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

    // ── The wheel's view ───────────────────────────────────────────────────

    /** When this ability is next usable, as a game time; 0 when it is ready now. */
    private static long readyAt(LivingEntity entity, ResourceLocation geneId) {
        Map<ResourceLocation, Long> map = READY_AT.get(entity.getUUID());
        return map == null ? 0L : map.getOrDefault(geneId, 0L);
    }

    /**
     * The slots as the wheel needs them: resolved, named, and with cooldowns already answered.
     *
     * <p>Built server-side because every input is: the power layer decides what is slottable, the
     * cooldown table is transient server state, and a skill's name lives in its def. Shipping the
     * answer keeps the client from needing a second copy of any of it.</p>
     */
    public static AbilityLoadoutS2CPayload view(ServerPlayer player) {
        List<AbilityLoadoutS2CPayload.Entry> entries = new ArrayList<>();
        for (Map.Entry<Integer, Slotted> entry : slotMap(player).entrySet()) {
            Slotted slotted = entry.getValue();
            ResourceLocation id = slotted.geneId();
            int cooldown = 0;
            int costAmount = 0;
            String costLabel = "";
            if (slotted.instance() instanceof ActiveAbilityGeneType.Instance active) {
                cooldown = Math.max(0, active.cooldownTicks());
                if (active.costResource() != null && active.costAmount() > 0) {
                    costAmount = active.costAmount();
                    costLabel = AbilityNames.resource(active.costResource());
                }
            }
            boolean toggle = slotted.kind() == GeneInstanceKind.TOGGLE;
            entries.add(new AbilityLoadoutS2CPayload.Entry(entry.getKey(), id.toString(),
                    AbilityNames.display(id), AbilityNames.icon(id), toggle,
                    toggle && AbilityToggles.isOn(player, id),
                    cooldown, readyAt(player, id), costAmount, costLabel));
        }
        // Everything ownable rides along, so opening the picker needs no second round trip and the
        // list can never disagree with the slots it is editing.
        List<AbilityLoadoutS2CPayload.Option> available = new ArrayList<>();
        java.util.Set<ResourceLocation> seen = new java.util.LinkedHashSet<>();
        for (Slotted slotted : slottables(player)) {
            ResourceLocation id = slotted.geneId();
            if (!seen.add(id)) continue;
            available.add(new AbilityLoadoutS2CPayload.Option(id.toString(),
                    AbilityNames.display(id), AbilityNames.icon(id), AbilityNames.source(id)));
        }
        return new AbilityLoadoutS2CPayload(List.copyOf(entries), List.copyOf(available));
    }

    /** Pushes the wheel's view after anything that could change it. */
    public static void syncView(ServerPlayer player) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, view(player));
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, view(player));
        *///?}
    }
}
