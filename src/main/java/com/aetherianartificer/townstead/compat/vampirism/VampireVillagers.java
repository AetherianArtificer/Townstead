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
    /** Where a cure in progress used to be kept; read once to move it onto {@link #CURE}. */
    private static final String CURE_AT = "townstead:vampire_cure_at";
    /** A cure in progress: the state's own timer runs it, and its state effect finishes it. */
    public static final ResourceLocation CURE = ResourceLocation.tryParse("townstead_state:vampire_cure");
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
     * cure that takes two to four minutes. Handed to a villager, the apple is a gift reaction
     * ({@code gift_reaction/vampire_cure}); this is the same cure for a wild vampire turned back.
     * True when the apple was taken as the cure.
     */
    public static boolean tryStartCure(VillagerEntityMCA villager, net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
        if (!stack.is(net.minecraft.world.item.Items.GOLDEN_APPLE) || !isVampire(villager)
                || !villager.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)
                || curing(villager)) return false;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        curedBy(player, villager);
        beginCure(villager);
        return true;
    }

    /**
     * The same cures work on a wild vampire mob that shows a person: it becomes that person, a vampire
     * villager and a newcomer to wherever they are, and the cure starts on them. True when the item
     * was taken as a cure.
     */
    public static boolean tryCureWild(net.minecraft.world.entity.Mob mob, net.minecraft.server.level.ServerPlayer player,
                                      net.minecraft.world.item.ItemStack stack) {
        boolean apple = stack.is(net.minecraft.world.item.Items.GOLDEN_APPLE)
                && mob.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
        if (!apple && !isGarlicInjection(stack)) return false;
        VillagerEntityMCA person = com.aetherianartificer.townstead.replace.WildCostume.person(mob);
        if (person == null || !isVampire(person)) return false;
        VillagerEntityMCA villager = com.aetherianartificer.townstead.replace.WildCostume.convert(mob, false);
        if (villager == null) return false;
        return apple ? tryStartCure(villager, player) : tryInjectCure(villager, player, stack);
    }

    private static final ResourceLocation GARLIC_INJECTION =ResourceLocation.tryParse("vampirism:injection_garlic");
    private static final ResourceLocation EMPTY_INJECTION = ResourceLocation.tryParse("vampirism:injection_empty");

    public static boolean isGarlicInjection(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && GARLIC_INJECTION.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** A garlic injection given to a vampire villager starts the same cure, and leaves an empty injection. */
    public static boolean tryInjectCure(VillagerEntityMCA villager, net.minecraft.server.level.ServerPlayer player,
                                        net.minecraft.world.item.ItemStack stack) {
        if (!isGarlicInjection(stack) || curing(villager)) return false;
        boolean early = !isVampire(villager);
        if (early && !hasSanguinare(villager)) return false;
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            BuiltInRegistries.ITEM.getOptional(EMPTY_INJECTION).ifPresent(empty -> {
                net.minecraft.world.item.ItemStack left = new net.minecraft.world.item.ItemStack(empty);
                if (!player.getInventory().add(left)) player.drop(left, false);
            });
        }
        curedBy(player, villager);
        if (early) {
            removeSanguinare(villager);
            villager.playSound(net.minecraft.sounds.SoundEvents.ZOMBIE_VILLAGER_CURE, 1f, 1f);
            VampireChronicles.cured(villager, 0);
            return true;
        }
        beginCure(villager);
        return true;
    }

    /** Chronicle counter on a player for every cure of vampirism they start. */
    public static final String CURED_BY = "townstead:cured_vampirism";

    private static void curedBy(net.minecraft.server.level.ServerPlayer player, VillagerEntityMCA villager) {
        com.aetherianartificer.townstead.chronicle.Chronicles.addCounter(player.server, player.getUUID(), CURED_BY, 1);
        com.aetherianartificer.townstead.cure.CureReactions.curedBy(villager, player);
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
        EntityStates.set(villager, CURE, 1, 2400 + villager.getRandom().nextInt(2400), null);
        villager.playSound(net.minecraft.sounds.SoundEvents.ZOMBIE_VILLAGER_CURE, 1f, 1f);
    }

    private static boolean curing(VillagerEntityMCA villager) {
        return EntityStates.definition(CURE) != null && EntityStates.resolve(villager, CURE).active();
    }

    /** Looks after vampire villagers: rank, garlic, aging, night shift, and an old-style cure in progress. */
    public static void tick(VillagerEntityMCA villager) {
        if (EntityStates.definition(STATE) != null) VampireChronicles.observeRank(villager);
        com.aetherianartificer.townstead.cure.CureReactions.tick(villager);
        if (EntityStates.definition(STATE) != null) VampireGarlic.tick(villager);
        if (villager.tickCount % 100 == 0) nightShift(villager);
        DhampirAging.tick(villager);
        // A cure begun before cures became a state carries on as one, for the time it had left.
        var data = villager.getPersistentData();
        if (data.contains(CURE_AT) && EntityStates.definition(CURE) != null) {
            long left = Math.max(1, data.getLong(CURE_AT) - villager.level().getGameTime());
            data.remove(CURE_AT);
            if (isVampire(villager)) EntityStates.set(villager, CURE, 1, left, null);
        }
    }

    private static final ResourceLocation NIGHT_SHIFT = ResourceLocation.tryParse("townstead:night_dweller");
    private static final String GAVE_NIGHT_SHIFT = "townstead:vampire_night_shift";

    /**
     * A vampire works the night shift, unless someone set their shift by hand; a cured one still on
     * it goes back to the ordinary day. The shift stays a normal shift a player can change.
     */
    private static void nightShift(VillagerEntityMCA villager) {
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.SHIFTS)
                || villager.level().getServer() == null) return;
        var data = villager.getPersistentData();
        boolean vampire = isVampire(villager);
        if (vampire == data.getBoolean(GAVE_NIGHT_SHIFT)) return;
        var schedule = com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).schedule();
        if (vampire) {
            data.putBoolean(GAVE_NIGHT_SHIFT, true);
            if (schedule.hasNonDefaultCustomShifts()) return;
            com.aetherianartificer.townstead.shift.template.ShiftTemplateRegistry.resolve(villager.level().getServer(), NIGHT_SHIFT)
                    .ifPresent(shift -> {
                        schedule.setShifts(shift.copyShifts());
                        schedule.setTemplateId(NIGHT_SHIFT.toString());
                    });
        } else {
            data.remove(GAVE_NIGHT_SHIFT);
            if (!NIGHT_SHIFT.toString().equals(schedule.templateId())) return;
            schedule.setShifts(com.aetherianartificer.townstead.shift.ShiftData.getVanillaDefault());
            schedule.setTemplateId("");
        }
        com.aetherianartificer.townstead.villager.TownsteadVillagers.flush(villager);
        com.aetherianartificer.townstead.shift.ShiftScheduleApplier.apply(villager);
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
