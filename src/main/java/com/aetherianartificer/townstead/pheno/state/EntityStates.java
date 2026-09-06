package com.aetherianartificer.townstead.pheno.state;

import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.ability.ResourceValues;
import com.aetherianartificer.townstead.root.gene.types.ResourceGeneType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

//? if neoforge {
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
//?} else {
/*import net.minecraft.world.effect.MobEffect;
*///?}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Resolver and writable Pheno-owned backing store for open semantic entity states. */
public final class EntityStates {
    private static final String STORAGE_KEY = "PhenoEntityStates";
    private static final String BACKINGS_KEY = "backings";
    private static volatile Map<ResourceLocation, EntityStateDefinition> definitions = Map.of();
    private static volatile List<StateBacking> backings = List.of();
    private static volatile List<StateEffect> effects = List.of();
    private static final Map<UUID, Map<ResourceLocation, Stored>> SESSION = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<ResourceLocation, Resolved>> PREVIOUS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<ResourceLocation, Long>> NEXT_PERIODIC = new ConcurrentHashMap<>();

    private EntityStates() {}

    public record Resolved(ResourceLocation state, boolean active, double amount,
                           @Nullable String tier, int tierIndex, long remaining,
                           @Nullable ResourceLocation source, long modified) {
        static Resolved inactive(EntityStateDefinition definition) {
            return new Resolved(definition.id(), false, definition.initial(), null, -1, 0, null, 0);
        }
    }

    private record Sample(StateBacking backing, double amount, long remaining, long modified) {}
    private record Stored(double amount, long expiresAt, long modified) {}

    public static @Nullable EntityStateDefinition definition(ResourceLocation id) {
        return definitions.get(id);
    }

    public static Map<ResourceLocation, EntityStateDefinition> definitions() { return definitions; }
    public static List<StateBacking> backings() { return backings; }
    public static List<StateEffect> effects() { return effects; }

    static void replaceDefinitions(Map<ResourceLocation, EntityStateDefinition> next) {
        definitions = Map.copyOf(next);
        resetRuntime();
    }

    static void replaceBackings(List<StateBacking> next) {
        backings = next.stream().sorted(Comparator.comparingInt(StateBacking::readPriority).reversed()
                .thenComparing(backing -> backing.id().toString())).toList();
        resetRuntime();
    }

    static void replaceEffects(List<StateEffect> next) {
        effects = next.stream().sorted(Comparator.comparingInt(StateEffect::priority).reversed()
                .thenComparing(effect -> effect.id().toString())).toList();
        resetRuntime();
    }

    static void resetRuntime() {
        PREVIOUS.clear();
        NEXT_PERIODIC.clear();
    }

    public static Resolved resolve(LivingEntity entity, ResourceLocation stateId) {
        EntityStateDefinition definition = definitions.get(stateId);
        if (definition == null) return new Resolved(stateId, false, 0, null, -1, 0, null, 0);
        long now = entity.level().getGameTime();
        List<Sample> samples = new ArrayList<>();
        for (StateBacking backing : backings) {
            if (!backing.state().equals(stateId) || !applies(backing, entity)) continue;
            Sample sample = sample(entity, definition, backing, now);
            if (sample != null) samples.add(sample);
        }
        if (samples.isEmpty()) return Resolved.inactive(definition);
        Sample provenance = samples.get(0);
        double amount;
        switch (definition.merge()) {
            case FIRST -> amount = provenance.amount();
            case MAX -> {
                provenance = samples.stream().max(Comparator.comparingDouble(Sample::amount)
                        .thenComparingInt(sample -> sample.backing().readPriority())).orElse(provenance);
                amount = provenance.amount();
            }
            case SUM -> {
                amount = samples.stream().mapToDouble(Sample::amount).sum();
                provenance = samples.stream().max(Comparator.comparingDouble(Sample::amount)).orElse(provenance);
            }
            case LATEST -> {
                provenance = samples.stream().max(Comparator.comparingLong(Sample::modified)
                        .thenComparingInt(sample -> sample.backing().readPriority())).orElse(provenance);
                amount = provenance.amount();
            }
            default -> amount = provenance.amount();
        }
        amount = definition.clamp(amount);
        EntityStateDefinition.Tier tier = definition.tier(amount);
        long remaining = samples.stream().mapToLong(Sample::remaining).max().orElse(0);
        return new Resolved(stateId, true, amount, tier == null ? null : tier.id(),
                definition.tierIndex(amount), remaining, provenance.backing().id(), provenance.modified());
    }

    public static boolean add(LivingEntity entity, ResourceLocation state, double amount,
                              long duration, @Nullable ResourceLocation source) {
        Resolved current = resolve(entity, state);
        return set(entity, state, current.amount() + amount, duration, source);
    }

    public static boolean set(LivingEntity entity, ResourceLocation state, double amount,
                              long duration, @Nullable ResourceLocation source) {
        EntityStateDefinition definition = definitions.get(state);
        if (definition == null || !Double.isFinite(amount)) return false;
        StateBacking target = writableBacking(entity, state, source);
        if (target == null) return false;
        double clamped = definition.clamp(amount);
        if (clamped <= definition.min()) {
            removeOwned(entity, definition, target.id());
            return true;
        }
        long now = entity.level().getGameTime();
        long expiresAt = duration > 0 ? safeAdd(now, duration) : 0;
        writeOwned(entity, definition, target.id(), new Stored(clamped, expiresAt, now));
        return true;
    }

    public static boolean clear(LivingEntity entity, ResourceLocation state,
                                @Nullable ResourceLocation source) {
        EntityStateDefinition definition = definitions.get(state);
        if (definition == null) return false;
        boolean cleared = false;
        for (StateBacking backing : backings) {
            if (!backing.state().equals(state) || !backing.writable() || !applies(backing, entity)) continue;
            if (source != null && !source.equals(backing.id())) continue;
            removeOwned(entity, definition, backing.id());
            cleared = true;
        }
        return cleared;
    }

    /** Runs transition and periodic contributions. Called from the existing villager server tick. */
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        UUID uuid = entity.getUUID();
        Map<ResourceLocation, Resolved> previous = PREVIOUS.computeIfAbsent(uuid, ignored -> new HashMap<>());
        long now = entity.level().getGameTime();
        for (EntityStateDefinition definition : definitions.values()) {
            Resolved before = previous.getOrDefault(definition.id(), Resolved.inactive(definition));
            Resolved current = resolve(entity, definition.id());
            for (StateEffect effect : effects) {
                if (!effect.state().equals(definition.id())) continue;
                if (!before.active() && current.active() && matches(effect, current) && effect.onEnter() != null) {
                    effect.onEnter().run(new ActionContext(entity));
                }
                if (before.active() && current.active() && before.tierIndex() != current.tierIndex()
                        && matches(effect, current) && effect.onTierChange() != null) {
                    effect.onTierChange().run(new ActionContext(entity));
                }
                if (before.active() && !current.active() && matches(effect, before) && effect.onExit() != null) {
                    effect.onExit().run(new ActionContext(entity));
                }
                runPeriodic(entity, effect, current, now);
            }
            previous.put(definition.id(), current);
        }
    }

    public static void forget(LivingEntity entity) {
        UUID uuid = entity.getUUID();
        PREVIOUS.remove(uuid);
        NEXT_PERIODIC.remove(uuid);
        SESSION.remove(uuid);
    }

    /** Applies each identity's death policy while preserving state across non-death player clones. */
    public static void onClone(LivingEntity original, LivingEntity replacement, boolean wasDeath) {
        CompoundTag copied = new CompoundTag();
        CompoundTag originalValues = persistentBackings(original);
        for (String key : originalValues.getAllKeys()) {
            ResourceLocation backingId = ResourceLocation.tryParse(key);
            StateBacking backing = backingId == null ? null : backing(backingId);
            EntityStateDefinition definition = backing == null ? null : definitions.get(backing.state());
            if (backing == null || definition == null) continue;
            if (!wasDeath || definition.deathPolicy() == EntityStateDefinition.DeathPolicy.KEEP) {
                copied.put(key, originalValues.get(key).copy());
                if (backing.type() == StateBacking.SourceType.OWNED
                        && resourceAvailable(original, backing.ownedResource())
                        && resourceAvailable(replacement, backing.ownedResource())) {
                    ResourceValues.set(replacement, backing.ownedResource(),
                            ResourceValues.get(original, backing.ownedResource()));
                }
            }
        }
        writePersistentBackings(replacement, copied);

        Map<ResourceLocation, Stored> session = SESSION.get(original.getUUID());
        if (session != null && wasDeath) {
            session.entrySet().removeIf(entry -> {
                StateBacking backing = backing(entry.getKey());
                EntityStateDefinition definition = backing == null ? null : definitions.get(backing.state());
                return definition == null || definition.deathPolicy() == EntityStateDefinition.DeathPolicy.CLEAR;
            });
        }
        PREVIOUS.remove(replacement.getUUID());
        NEXT_PERIODIC.remove(replacement.getUUID());
    }

    private static void runPeriodic(LivingEntity entity, StateEffect effect, Resolved current, long now) {
        if (effect.whileActive() == null) return;
        Map<ResourceLocation, Long> dueByEffect = NEXT_PERIODIC.computeIfAbsent(entity.getUUID(), ignored -> new HashMap<>());
        if (!current.active() || !matches(effect, current)) {
            dueByEffect.remove(effect.id());
            return;
        }
        long due = dueByEffect.getOrDefault(effect.id(), now + effect.whileActive().interval());
        if (now < due) {
            dueByEffect.putIfAbsent(effect.id(), due);
            return;
        }
        dueByEffect.put(effect.id(), safeAdd(now, effect.whileActive().interval()));
        if (entity.getRandom().nextDouble() <= effect.whileActive().chance()) {
            effect.whileActive().action().run(new ActionContext(entity));
        }
    }

    private static boolean matches(StateEffect effect, Resolved state) {
        return effect.tier() == null || effect.tier().equals(state.tier());
    }

    private static boolean applies(StateBacking backing, LivingEntity entity) {
        return backing.appliesTo() == null || backing.appliesTo().test(new ConditionContext(entity));
    }

    private static @Nullable StateBacking backing(ResourceLocation id) {
        for (StateBacking backing : backings) if (backing.id().equals(id)) return backing;
        return null;
    }

    private static @Nullable StateBacking writableBacking(LivingEntity entity, ResourceLocation state,
                                                           @Nullable ResourceLocation source) {
        return backings.stream().filter(backing -> backing.state().equals(state)
                        && backing.writable() && backing.type() == StateBacking.SourceType.OWNED
                        && (source == null || source.equals(backing.id())) && applies(backing, entity)
                        && resourceAvailable(entity, backing.ownedResource()))
                .max(Comparator.comparingInt(StateBacking::writePriority)
                        .thenComparing(backing -> backing.id().toString())).orElse(null);
    }

    private static @Nullable Sample sample(LivingEntity entity, EntityStateDefinition definition,
                                           StateBacking backing, long now) {
        if (backing.type() == StateBacking.SourceType.OWNED) {
            if (definition.persistence() == EntityStateDefinition.Persistence.PERSISTENT
                    && !resourceAvailable(entity, backing.ownedResource())) return null;
            Stored stored = readOwned(entity, definition, backing.id());
            if (stored == null) return null;
            if (stored.expiresAt() > 0 && stored.expiresAt() <= now) {
                removeOwned(entity, definition, backing.id());
                return null;
            }
            long remaining = stored.expiresAt() == 0 ? Long.MAX_VALUE : Math.max(0, stored.expiresAt() - now);
            double amount = definition.persistence() == EntityStateDefinition.Persistence.SESSION
                    ? stored.amount() : ResourceValues.get(entity, backing.ownedResource());
            return new Sample(backing, definition.clamp(amount), remaining, stored.modified());
        }
        MobEffectInstance instance = statusEffect(entity, backing.statusEffect());
        if (instance == null) return null;
        double amount = mappedAmount(definition, backing, instance.getAmplifier());
        return new Sample(backing, definition.clamp(amount), instance.getDuration(), now);
    }

    private static double mappedAmount(EntityStateDefinition definition, StateBacking backing, int amplifier) {
        StateBacking.Level selected = null;
        int selectedAmplifier = -1;
        for (Map.Entry<Integer, StateBacking.Level> entry : backing.amplifierLevels().entrySet()) {
            if (entry.getKey() <= amplifier && entry.getKey() > selectedAmplifier) {
                selected = entry.getValue();
                selectedAmplifier = entry.getKey();
            }
        }
        if (selected == null) return backing.presenceValue();
        if (selected.amount() != null) return selected.amount();
        EntityStateDefinition.Tier tier = definition.tier(selected.tier());
        return tier == null ? backing.presenceValue() : tier.min();
    }

    private static @Nullable MobEffectInstance statusEffect(LivingEntity entity, @Nullable ResourceLocation id) {
        if (id == null || !BuiltInRegistries.MOB_EFFECT.containsKey(id)) return null;
        //? if neoforge {
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT
                .getHolder(ResourceKey.create(Registries.MOB_EFFECT, id)).orElse(null);
        return effect == null ? null : entity.getEffect(effect);
        //?} else {
        /*MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        return effect == null ? null : entity.getEffect(effect);
        *///?}
    }

    private static @Nullable Stored readOwned(LivingEntity entity, EntityStateDefinition definition,
                                               ResourceLocation backingId) {
        if (definition.persistence() == EntityStateDefinition.Persistence.SESSION) {
            Map<ResourceLocation, Stored> values = SESSION.get(entity.getUUID());
            return values == null ? null : values.get(backingId);
        }
        CompoundTag values = persistentBackings(entity);
        if (!values.contains(backingId.toString(), Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = values.getCompound(backingId.toString());
        if (!tag.getBoolean("active") && !tag.contains("amount", Tag.TAG_DOUBLE)) return null;
        return new Stored(tag.getDouble("amount"), tag.getLong("expires"), tag.getLong("modified"));
    }

    private static void writeOwned(LivingEntity entity, EntityStateDefinition definition,
                                   ResourceLocation backingId, Stored stored) {
        if (definition.persistence() == EntityStateDefinition.Persistence.SESSION) {
            SESSION.computeIfAbsent(entity.getUUID(), ignored -> new ConcurrentHashMap<>()).put(backingId, stored);
            return;
        }
        StateBacking target = backing(backingId);
        if (target == null || !resourceAvailable(entity, target.ownedResource())) return;
        ResourceValues.set(entity, target.ownedResource(), (int) Math.round(stored.amount()));
        CompoundTag values = persistentBackings(entity);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("active", true);
        tag.putLong("expires", stored.expiresAt());
        tag.putLong("modified", stored.modified());
        values.put(backingId.toString(), tag);
        writePersistentBackings(entity, values);
    }

    private static void removeOwned(LivingEntity entity, EntityStateDefinition definition,
                                    ResourceLocation backingId) {
        if (definition.persistence() == EntityStateDefinition.Persistence.SESSION) {
            Map<ResourceLocation, Stored> values = SESSION.get(entity.getUUID());
            if (values != null) values.remove(backingId);
            return;
        }
        StateBacking target = backing(backingId);
        if (target != null && resourceAvailable(entity, target.ownedResource())) {
            ResourceValues.set(entity, target.ownedResource(), (int) Math.round(definition.initial()));
        }
        CompoundTag values = persistentBackings(entity);
        values.remove(backingId.toString());
        writePersistentBackings(entity, values);
    }

    private static CompoundTag persistentBackings(LivingEntity entity) {
        CompoundTag root = entity.getPersistentData().getCompound(STORAGE_KEY);
        return root.contains(BACKINGS_KEY, Tag.TAG_COMPOUND) ? root.getCompound(BACKINGS_KEY) : new CompoundTag();
    }

    private static void writePersistentBackings(LivingEntity entity, CompoundTag values) {
        CompoundTag root = entity.getPersistentData().getCompound(STORAGE_KEY);
        root.putInt("version", 1);
        root.put(BACKINGS_KEY, values);
        entity.getPersistentData().put(STORAGE_KEY, root);
    }

    private static boolean resourceAvailable(LivingEntity entity, @Nullable ResourceLocation resource) {
        if (resource == null) return false;
        return Powers.active(entity).stream().anyMatch(power -> power.id().equals(resource)
                && power.component() instanceof ResourceGeneType.Instance);
    }

    private static long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
