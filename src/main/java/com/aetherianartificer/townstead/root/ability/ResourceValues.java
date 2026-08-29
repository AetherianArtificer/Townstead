package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.root.gene.Gene;
import com.aetherianartificer.townstead.root.gene.GeneRegistry;
import com.aetherianartificer.townstead.root.gene.types.ReachHook;
import com.aetherianartificer.townstead.root.gene.types.ActiveAbilityGeneType;
import com.aetherianartificer.townstead.root.gene.types.ResourceDisplay;
import com.aetherianartificer.townstead.root.gene.types.ResourceGeneType;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent per-entity values for {@code pheno:resource} powers. Values live in the entity's
 * saved persistent data instead of a process-local UUID map, so they survive unload, logout and
 * server restart. The only runtime cache retained here is the last HUD snapshot hash.
 */
public final class ResourceValues {

    static final String STORAGE_KEY = "TownsteadResources";
    static final String VALUES_KEY = "values";
    static final int STORAGE_VERSION = 1;

    private static final Map<UUID, Integer> LAST_SYNC_HASH = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<ResourceLocation, Integer>> REGEN_SEQUENCES =
            new ConcurrentHashMap<>();

    private ResourceValues() {}

    public static int get(LivingEntity entity, ResourceLocation resourceId) {
        ResourceGeneType.Instance instance = instanceOf(entity, resourceId);
        if (instance == null) return 0;
        Integer stored = readStored(entity, resourceId);
        if (stored == null) return instance.start();
        int clamped = Math.max(instance.min(), Math.min(instance.max(), stored));
        if (clamped != stored) writeStored(entity, resourceId, clamped);
        return clamped;
    }

    public static void change(LivingEntity entity, ResourceLocation resourceId, int delta) {
        ResourceGeneType.Instance instance = instanceOf(entity, resourceId);
        if (instance == null) return;
        set(entity, resourceId, get(entity, resourceId) + delta);
    }

    public static void set(LivingEntity entity, ResourceLocation resourceId, int value) {
        ResourceGeneType.Instance instance = instanceOf(entity, resourceId);
        if (instance == null) return;
        int prev = get(entity, resourceId);
        int next = Math.max(instance.min(), Math.min(instance.max(), value));
        writeStored(entity, resourceId, next);
        if (next > prev && !instance.onReach().isEmpty()) {
            fireReach(entity, resourceId, instance, prev, next);
        }
    }

    private static void fireReach(LivingEntity entity, ResourceLocation resourceId,
                                  ResourceGeneType.Instance instance, int prev, int next) {
        boolean reset = false;
        for (ReachHook hook : instance.onReach()) {
            if (!hook.crossed(prev, next)) continue;
            hook.action().run(new ActionContext(entity));
            if (hook.reset()) reset = true;
        }
        if (reset) {
            int start = Math.max(instance.min(), Math.min(instance.max(), instance.start()));
            writeStored(entity, resourceId, start);
        }
    }

    public static void tick(LivingEntity entity) {
        for (Power power : Powers.active(entity)) {
            if (!(power.component() instanceof ResourceGeneType.Instance instance)) continue;
            if (instance.regen() == 0 || entity.tickCount % instance.regenInterval() != 0) continue;
            int before = get(entity, power.id());
            change(entity, power.id(), instance.regen());
            if (get(entity, power.id()) != before) {
                REGEN_SEQUENCES.computeIfAbsent(entity.getUUID(), ignored -> new ConcurrentHashMap<>())
                        .merge(power.id(), 1, Integer::sum);
            }
        }
    }

    public static void syncTo(ServerPlayer player) {
        List<ResourceSyncS2CPayload.Bar> bars = snapshot(player);
        int hash = bars.hashCode();
        Integer previous = LAST_SYNC_HASH.put(player.getUUID(), hash);
        if (previous != null && previous == hash && player.tickCount % 200 != 0) return;

        ResourceSyncS2CPayload payload = new ResourceSyncS2CPayload(bars);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    static List<ResourceSyncS2CPayload.Bar> snapshot(LivingEntity entity) {
        List<Power> active = Powers.active(entity);
        Set<ResourceLocation> referenced = new HashSet<>();
        for (Power power : active) {
            if (power.component() instanceof ResourceConsumer consumer && consumer.costResource() != null) {
                referenced.add(consumer.costResource());
            }
        }

        List<ResourceSyncS2CPayload.Bar> bars = new ArrayList<>();
        Set<ResourceLocation> included = new HashSet<>();
        for (Power power : active) {
            if (!(power.component() instanceof ResourceGeneType.Instance instance)) continue;
            ResourceDisplay display = instance.resourceDisplay();
            if (display.eligibility() == ResourceDisplay.Eligibility.NEVER) continue;
            if (display.eligibility() == ResourceDisplay.Eligibility.WHEN_REFERENCED
                    && !referenced.contains(power.id())) continue;

            ResourceHudDefinitions.ColorTheme colorTheme = ResourceHudDefinitions.colorTheme(display.colorTheme());
            ResourceHudDefinitions.Frame frame = ResourceHudDefinitions.frame(display.frame());
            ResourceHudDefinitions.FrameArt frameArt = frame.art(display.shape());
            int value = get(entity, power.id());
            bars.add(new ResourceSyncS2CPayload.Bar(
                    power.id().toString(), value, instance.min(), instance.max(),
                    instance.start(), instance.color(),
                    display.shape().name(), display.fillMode().name(), effectsOf(display.effects()),
                    reactionsOf(display.reactions()),
                    abilityReady(entity, active, power.id(), value),
                    regenerationSequence(entity, power.id()),
                    display.frame().toString(), display.colorTheme().toString(), display.anchor().name(),
                    display.pipStyle().name(), display.segments(),
                    display.priority(), frame.backgroundColor(), colorTheme.framePrimaryColor(),
                    colorTheme.frameSecondaryColor(),
                    frame.thickness(), frame.spriteTexture() == null ? "" : frame.spriteTexture().toString(),
                    frame.spriteRow(), frameTexture(frameArt == null ? null : frameArt.baseTexture()),
                    frameTexture(frameArt == null ? null : frameArt.primaryTexture()),
                    frameTexture(frameArt == null ? null : frameArt.secondaryTexture())));
            included.add(power.id());
        }
        for (com.aetherianartificer.townstead.api.resource.ResourceHudProvider.Meter meter
                : com.aetherianartificer.townstead.api.resource.ResourceHudProviders.collect(entity)) {
            if (!included.add(meter.id())) continue;
            ResourceHudDefinitions.ColorTheme colorTheme = ResourceHudDefinitions.colorTheme(meter.colorTheme());
            ResourceHudDefinitions.Frame frame = ResourceHudDefinitions.frame(meter.frame());
            ResourceHudDefinitions.FrameArt frameArt = frame.art(meter.shape());
            bars.add(new ResourceSyncS2CPayload.Bar(
                    meter.id().toString(), meter.value(), meter.min(), meter.max(), meter.restingValue(),
                    meter.color() < 0 ? 0x3FA0FF : meter.color() & 0xFFFFFF,
                    meter.shape().name(), meter.fillMode().name(), effectsOf(meter.effects()),
                    reactionsOf(meter.reactions()), meter.abilityReady(), meter.regenerationSequence(),
                    meter.frame().toString(), meter.colorTheme().toString(),
                    meter.anchor().name(), meter.pipStyle().name(), meter.segments(), meter.priority(),
                    frame.backgroundColor(), colorTheme.framePrimaryColor(),
                    colorTheme.frameSecondaryColor(), frame.thickness(),
                    frame.spriteTexture() == null ? "" : frame.spriteTexture().toString(),
                    frame.spriteRow(), frameTexture(frameArt == null ? null : frameArt.baseTexture()),
                    frameTexture(frameArt == null ? null : frameArt.primaryTexture()),
                    frameTexture(frameArt == null ? null : frameArt.secondaryTexture())));
        }
        bars.sort(Comparator.comparingInt(ResourceSyncS2CPayload.Bar::priority).reversed()
                .thenComparing(ResourceSyncS2CPayload.Bar::resourceId));
        return List.copyOf(bars);
    }

    private static String frameTexture(ResourceLocation texture) {
        return texture == null ? "" : texture.toString();
    }

    private static List<ResourceSyncS2CPayload.Effect> effectsOf(
            List<ResourceDisplay.BarEffect> effects) {
        if (effects == null || effects.isEmpty()) return List.of();
        List<ResourceSyncS2CPayload.Effect> resolved = new ArrayList<>(effects.size());
        for (ResourceDisplay.BarEffect effect : effects) {
            resolved.add(new ResourceSyncS2CPayload.Effect(
                    effect.type().toString(), effect.strength(), effect.speed(), effect.interval(),
                    effect.frequency(), effect.color(),
                    effect.gradientShape(), effect.highlightColor(), effect.shadowColor(),
                    effect.surfacePoints(), effect.tension(), effect.damping(),
                    effect.splash(), effect.movementInfluence(),
                    effect.lobeCount(), effect.viscosity(), effect.stringiness(),
                    effect.bubbleCount(), effect.bubbleSize(), effect.bubbleWobble(),
                    effect.emberCount(), effect.emberDrift(), effect.emberFlicker(),
                    effect.emberEscape(),
                    effect.flameCount(), effect.flameHeight(), effect.flameFlicker(),
                    effect.flamePlacement(),
                    effect.steamCount(), effect.steamSize(), effect.steamDrift(),
                    effect.electricCount(), effect.electricBranching(), effect.electricReach(),
                    effect.wispCount(), effect.wispTrail(), effect.wispWander(),
                    effect.sparkleCount(), effect.sparkleSize(), effect.sparkleTwinkle(),
                    effect.crystalCount(), effect.crystalDepth(), effect.crystalGlint(),
                    effect.runeMode(), effect.runeSpacing(), effect.runeTexture(),
                    effect.runeGlyphWidth(), effect.runeGlyphHeight(),
                    effect.runeColumns(), effect.runeRows(), effect.runeEscape(),
                    effect.corruptionCount(), effect.corruptionSize(),
                    effect.voidCount(), effect.voidInstability(), effect.prismaticWidth(),
                    effect.sporeCount(), effect.sporeSize(), effect.sporeDrift(),
                    effect.fallingCount(), effect.fallingSize(), effect.fallingDrift(),
                    effect.fallingTexture(), effect.fallingMarkWidth(), effect.fallingMarkHeight(),
                    effect.fallingColumns(), effect.fallingRows()));
        }
        return List.copyOf(resolved);
    }

    private static List<ResourceSyncS2CPayload.Reaction> reactionsOf(
            List<ResourceDisplay.BarReaction> reactions) {
        if (reactions == null || reactions.isEmpty()) return List.of();
        List<ResourceSyncS2CPayload.Reaction> resolved = new ArrayList<>(reactions.size());
        for (ResourceDisplay.BarReaction reaction : reactions) {
            resolved.add(new ResourceSyncS2CPayload.Reaction(reaction.type().toString(),
                    reaction.strength(), reaction.duration(), reaction.speed(), reaction.color(),
                    reaction.threshold(), reaction.mode(), reaction.continuing()));
        }
        return List.copyOf(resolved);
    }

    private static int regenerationSequence(LivingEntity entity, ResourceLocation resourceId) {
        Map<ResourceLocation, Integer> sequences = REGEN_SEQUENCES.get(entity.getUUID());
        return sequences == null ? 0 : sequences.getOrDefault(resourceId, 0);
    }

    private static boolean abilityReady(LivingEntity entity, List<Power> active,
                                        ResourceLocation resourceId, int value) {
        long now = entity.level().getGameTime();
        for (Power power : active) {
            if (!(power.component() instanceof ActiveAbilityGeneType.Instance ability)
                    || !resourceId.equals(ability.costResource())) continue;
            if (!com.aetherianartificer.townstead.assign.AssignCooldowns.isReady(
                    entity, power.id(), now)) continue;
            if (ability.condition() != null
                    && !ability.condition().test(new ConditionContext(entity))) continue;
            if (value >= ability.costAmount()) return true;
        }
        return false;
    }

    /** Copy persistent values during a player clone, resetting death-sensitive meters. */
    public static void onClone(Player original, Player replacement, boolean wasDeath) {
        CompoundTag originalValues = valuesTag(readStorage(original));
        CompoundTag copied = new CompoundTag();
        for (String key : originalValues.getAllKeys()) {
            if (!originalValues.contains(key, Tag.TAG_INT)) continue;
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null) continue;
            ResourceGeneType.Instance instance = instanceOf(original, id);
            if (!wasDeath || (instance != null && instance.persistOnDeath())) {
                copied.putInt(key, originalValues.getInt(key));
            }
        }
        writeValues(replacement, copied);
        LAST_SYNC_HASH.remove(replacement.getUUID());
    }

    /** Clears only runtime synchronization state; persisted values remain saved. */
    public static void clear(UUID uuid) {
        LAST_SYNC_HASH.remove(uuid);
        REGEN_SEQUENCES.remove(uuid);
    }

    public static int colorOf(LivingEntity entity, ResourceLocation resourceId) {
        ResourceGeneType.Instance instance = resourceId == null ? null : instanceOf(entity, resourceId);
        if (instance == null) return 0;
        return instance.color();
    }

    private static ResourceGeneType.Instance instanceOf(LivingEntity entity, ResourceLocation resourceId) {
        for (Power power : Powers.active(entity)) {
            if (power.id().equals(resourceId)
                    && power.component() instanceof ResourceGeneType.Instance instance) {
                return instance;
            }
        }
        Gene gene = GeneRegistry.byId(resourceId);
        return gene != null && gene.instance() instanceof ResourceGeneType.Instance instance ? instance : null;
    }

    private static Integer readStored(LivingEntity entity, ResourceLocation resourceId) {
        CompoundTag values = valuesTag(readStorage(entity));
        String key = resourceId.toString();
        return values.contains(key, Tag.TAG_INT) ? values.getInt(key) : null;
    }

    private static void writeStored(LivingEntity entity, ResourceLocation resourceId, int value) {
        CompoundTag storage = readStorage(entity);
        CompoundTag values = valuesTag(storage);
        values.putInt(resourceId.toString(), value);
        storage.putInt("version", STORAGE_VERSION);
        storage.put(VALUES_KEY, values);
        writeStorage(entity, storage);
    }

    private static CompoundTag readStorage(LivingEntity entity) {
        return ownerTag(entity).getCompound(STORAGE_KEY);
    }

    private static void writeValues(LivingEntity entity, CompoundTag values) {
        CompoundTag storage = new CompoundTag();
        storage.putInt("version", STORAGE_VERSION);
        storage.put(VALUES_KEY, values);
        writeStorage(entity, storage);
    }

    private static void writeStorage(LivingEntity entity, CompoundTag storage) {
        CompoundTag owner = ownerTag(entity);
        owner.put(STORAGE_KEY, storage);
        if (entity instanceof Player player) {
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, owner);
        }
    }

    private static CompoundTag ownerTag(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        }
        return entity.getPersistentData();
    }

    static CompoundTag valuesTag(CompoundTag storage) {
        return storage.contains(VALUES_KEY, Tag.TAG_COMPOUND)
                ? storage.getCompound(VALUES_KEY) : new CompoundTag();
    }
}
