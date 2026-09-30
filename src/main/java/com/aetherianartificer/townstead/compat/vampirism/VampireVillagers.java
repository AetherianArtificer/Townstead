package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.pheno.state.EntityStates;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

//? if neoforge {
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
//?}

/**
 * MCA villagers as Vampirism vampires, in place: the same entity, id, family and memories, with the
 * {@code townstead_state:vampire} aspect carrying the change. Read by the Vampirism mixins, so every
 * Vampirism-typed value here goes through reflection and nothing loads without the mod.
 */
public final class VampireVillagers {
    public static final ResourceLocation STATE = ResourceLocation.tryParse("townstead_state:vampire");
    public static final ResourceLocation BLOOD = ResourceLocation.tryParse("townstead_state:vampire_blood");
    private static final String CURE_AT = "townstead:vampire_cure_at";
    private static final ResourceLocation SANGUINARE = ResourceLocation.tryParse("vampirism:sanguinare");
    // Faction checks run in AI target scans every tick; one answer per villager per tick is enough.
    private static final Map<LivingEntity, Long> VAMPIRE_CACHE = new WeakHashMap<>();
    private static volatile Object vampireFaction;

    private VampireVillagers() {}

    /** A villager at fledgling or above: a vampire to Vampirism and to everyone else. */
    public static boolean isVampire(LivingEntity entity) {
        if (!(entity instanceof VillagerEntityMCA) || entity.level().isClientSide) return false;
        long now = entity.level().getGameTime();
        synchronized (VAMPIRE_CACHE) {
            Long cached = VAMPIRE_CACHE.get(entity);
            if (cached != null && (cached >> 1) == now) return (cached & 1L) != 0;
        }
        EntityStates.Resolved state = EntityStates.resolve(entity, STATE);
        boolean vampire = state.active() && state.amount() >= 1;
        synchronized (VAMPIRE_CACHE) {
            VAMPIRE_CACHE.put(entity, (now << 1) | (vampire ? 1L : 0L));
        }
        return vampire;
    }

    /** Whether Sanguinare may take this villager: its Root allows vampirism and it is not one yet. */
    public static boolean canTurn(LivingEntity entity) {
        return entity instanceof VillagerEntityMCA && EntityStates.definition(STATE) != null
                && EntityStates.eligible(entity, STATE) && !isVampire(entity);
    }

    public static boolean canBeInfected(LivingEntity entity) {
        return canTurn(entity) && !hasSanguinare(entity);
    }

    /** Sanguinare ran its course: the villager becomes a level-1 vampire where it stands. */
    public static boolean turn(LivingEntity entity) {
        if (!canTurn(entity)) return false;
        boolean turned = EntityStates.set(entity, STATE, 1, 0, null);
        synchronized (VAMPIRE_CACHE) {
            VAMPIRE_CACHE.remove(entity);
        }
        if (turned) {
            com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps.survival(entity,
                    com.aetherianartificer.townstead.chronicle.emit.ChronicleTapKeys.TURNED_VAMPIRE, Map.of());
        }
        return turned;
    }

    /**
     * Vampirism's cure for its converted villagers: a golden apple given while weakened starts a
     * cure that takes two to four minutes. True when the gift was taken as the cure.
     */
    public static boolean tryStartCure(VillagerEntityMCA villager, net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
        if (!stack.is(net.minecraft.world.item.Items.GOLDEN_APPLE) || !isVampire(villager)
                || !villager.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)
                || villager.getPersistentData().contains(CURE_AT)) return false;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        curedBy(player);
        beginCure(villager);
        return true;
    }

    private static final ResourceLocation GARLIC_INJECTION = ResourceLocation.tryParse("vampirism:injection_garlic");
    private static final ResourceLocation EMPTY_INJECTION = ResourceLocation.tryParse("vampirism:injection_empty");

    public static boolean isGarlicInjection(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && GARLIC_INJECTION.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** A garlic injection given to a vampire villager starts the same cure, and leaves an empty injection. */
    public static boolean tryInjectCure(VillagerEntityMCA villager, net.minecraft.server.level.ServerPlayer player,
                                        net.minecraft.world.item.ItemStack stack) {
        if (!isGarlicInjection(stack) || villager.getPersistentData().contains(CURE_AT)) return false;
        boolean early = !isVampire(villager);
        if (early && !hasSanguinare(villager)) return false;
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            BuiltInRegistries.ITEM.getOptional(EMPTY_INJECTION).ifPresent(empty -> {
                net.minecraft.world.item.ItemStack left = new net.minecraft.world.item.ItemStack(empty);
                if (!player.getInventory().add(left)) player.drop(left, false);
            });
        }
        curedBy(player);
        if (early) {
            removeSanguinare(villager);
            villager.playSound(net.minecraft.sounds.SoundEvents.ZOMBIE_VILLAGER_CURE, 1f, 1f);
            com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps.survival(villager,
                    com.aetherianartificer.townstead.chronicle.emit.ChronicleTapKeys.VAMPIRE_CURED, Map.of());
            return true;
        }
        beginCure(villager);
        return true;
    }

    /** Chronicle counter on a player for every cure of vampirism they start. */
    public static final String CURED_BY = "townstead:cured_vampirism";

    private static void curedBy(net.minecraft.server.level.ServerPlayer player) {
        com.aetherianartificer.townstead.chronicle.Chronicles.addCounter(player.server, player.getUUID(), CURED_BY, 1);
    }

    private static void removeSanguinare(LivingEntity entity) {
        if (!BuiltInRegistries.MOB_EFFECT.containsKey(SANGUINARE)) return;
        //? if neoforge {
        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(Registries.MOB_EFFECT, SANGUINARE))
                .ifPresent(entity::removeEffect);
        //?} else {
        /*entity.removeEffect(BuiltInRegistries.MOB_EFFECT.get(SANGUINARE));
        *///?}
    }

    private static void beginCure(VillagerEntityMCA villager) {
        long at = villager.level().getGameTime() + 2400 + villager.getRandom().nextInt(2400);
        villager.getPersistentData().putLong(CURE_AT, at);
        villager.playSound(net.minecraft.sounds.SoundEvents.ZOMBIE_VILLAGER_CURE, 1f, 1f);
    }

    /** Finishes a pending cure. Cheap for everyone else: one missing tag. */
    public static void tick(VillagerEntityMCA villager) {
        if (EntityStates.definition(STATE) != null) VampireChronicles.observeRank(villager);
        if (EntityStates.definition(STATE) != null) VampireGarlic.tick(villager);
        DhampirAging.tick(villager);
        var data = villager.getPersistentData();
        if (!data.contains(CURE_AT) || villager.level().getGameTime() < data.getLong(CURE_AT)) return;
        data.remove(CURE_AT);
        if (!isVampire(villager)) return;
        EntityStates.clear(villager, STATE, null);
        EntityStates.clear(villager, BLOOD, null);
        synchronized (VAMPIRE_CACHE) {
            VAMPIRE_CACHE.remove(villager);
        }
        villager.level().levelEvent(null, 1027, villager.blockPosition(), 0);
        com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps.survival(villager,
                com.aetherianartificer.townstead.chronicle.emit.ChronicleTapKeys.VAMPIRE_CURED, Map.of());
    }

    /** Vampirism's vampire faction object, for villagers that are vampires. */
    public static @Nullable Object vampireFaction() {
        Object faction = vampireFaction;
        if (faction != null) return faction;
        try {
            Field field = Class.forName("de.teamlapen.vampirism.api.VReference").getField("VAMPIRE_FACTION");
            faction = field.get(null);
            vampireFaction = faction;
        } catch (Throwable ignored) {
            return null;
        }
        return faction;
    }

    private static boolean hasSanguinare(LivingEntity entity) {
        if (!BuiltInRegistries.MOB_EFFECT.containsKey(SANGUINARE)) return false;
        //? if neoforge {
        return BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(Registries.MOB_EFFECT, SANGUINARE))
                .map(entity::hasEffect).orElse(false);
        //?} else {
        /*return entity.hasEffect(BuiltInRegistries.MOB_EFFECT.get(SANGUINARE));
        *///?}
    }
}
