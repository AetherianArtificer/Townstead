package com.aetherianartificer.townstead.compat.werewolves;

import com.aetherianartificer.townstead.pheno.state.EntityStates;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Werewolf villagers change the way the Werewolves mod's own werewolves do. Every hit they take
 * builds rage (ten per point of damage), which drains slowly; at night, past 150 rage, they turn for
 * the mod's own transform time, and a full moon turns them all night. While turned they are four
 * legged when prowling and upright once something hostile is close. The form is a state
 * ({@code townstead_state:beast_form}), whose tiers give the beast rig and keep them from talking and
 * sleeping; a behavior profile has them hunt enemies of the town.
 */
public final class WerewolfVillagers {
    public static final ResourceLocation WEREWOLF = ResourceLocation.tryParse("townstead_state:werewolf");
    public static final ResourceLocation RAGE = ResourceLocation.tryParse("townstead_state:rage");
    public static final ResourceLocation FORM = ResourceLocation.tryParse("townstead_state:beast_form");
    private static final double RAGE_TO_TURN = 150, RAGE_PER_DAMAGE = 10, RAGE_DRAIN = 0.1;
    private static final double UPRIGHT_RANGE = 4;
    private static final int FORM_COOLDOWN = 60;
    private static final long WHOLE_NIGHT = Long.MAX_VALUE;

    private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();
    private static final Map<UUID, Long> UNTIL = new HashMap<>();
    private static final Map<UUID, Long> FORM_SINCE = new HashMap<>();
    /** A form a command holds, written on the villager: the tick keeps them as they are until released. */
    private static final String HELD = "townstead:werewolf_held";
    /** Timed changes a command started: they run their full time, day or night. */
    private static final java.util.Set<UUID> COMMANDED = new java.util.HashSet<>();
    /** The day a command turned someone back on a full moon: that moon does not turn them again. */
    private static final Map<UUID, Long> KEPT_BACK = new HashMap<>();

    private WerewolfVillagers() {}

    private static volatile Object werewolfFaction;

    /**
     * A villager at werewolf level 1 or above: a werewolf to the Werewolves mod and to Vampirism, so
     * silver, wolfsbane and the mod's other werewolf rules reach them. Players need no help, since
     * their werewolf level is the mod's own.
     */
    public static boolean isWerewolf(LivingEntity entity) {
        if (!(entity instanceof VillagerEntityMCA) || entity.level().isClientSide
                || EntityStates.definition(WEREWOLF) == null) return false;
        EntityStates.Resolved state = EntityStates.resolve(entity, WEREWOLF);
        return state.active() && state.amount() >= 1;
    }

    /** The Werewolves mod's werewolf faction, or null without the mod. */
    public static Object werewolfFaction() {
        Object faction = werewolfFaction;
        if (faction != null) return faction;
        try {
            faction = Class.forName("de.teamlapen.werewolves.api.WReference").getField("WEREWOLF_FACTION").get(null);
            werewolfFaction = faction;
        } catch (Throwable ignored) {
            return null;
        }
        return faction;
    }

    /** Drops what is kept in memory for a villager who will not tick again. */
    public static void forget(VillagerEntityMCA villager) {
        UUID id = villager.getUUID();
        LAST_HEALTH.remove(id);
        UNTIL.remove(id);
        FORM_SINCE.remove(id);
        COMMANDED.remove(id);
        KEPT_BACK.remove(id);
    }

    /** Server stop: these are timers on one world's clock. */
    public static void clear() {
        LAST_HEALTH.clear();
        UNTIL.clear();
        FORM_SINCE.clear();
        COMMANDED.clear();
        KEPT_BACK.clear();
    }

    /** Holds a werewolf villager in {@code form} (0 human, 1 four legged, 2 upright) until {@link #release}. */
    public static void hold(VillagerEntityMCA villager, double form) {
        villager.getPersistentData().putDouble(HELD, form);
        setForm(villager, form);
    }

    /** Hands the villager's form back to night, moon and rage. */
    public static void release(VillagerEntityMCA villager) {
        villager.getPersistentData().remove(HELD);
    }

    /**
     * Turns a werewolf villager now: the change lasts the transform time, even by day. Form 0 turns
     * them back, and on a full moon keeps them back for the rest of that night.
     */
    public static void turn(VillagerEntityMCA villager, double form) {
        UUID id = villager.getUUID();
        Level level = villager.level();
        long now = level.getGameTime();
        if (form > 0) {
            UNTIL.put(id, now + transformSeconds() * 20L);
            COMMANDED.add(id);
            KEPT_BACK.remove(id);
        } else {
            UNTIL.remove(id);
            COMMANDED.remove(id);
            if (isFullMoon(level)) KEPT_BACK.put(id, level.getDayTime() / 24000L);
        }
        FORM_SINCE.put(id, now);
        setForm(villager, form);
    }

    /** The held form, or null when the villager's form follows the night. */
    public static Double held(VillagerEntityMCA villager) {
        return villager.getPersistentData().contains(HELD) ? villager.getPersistentData().getDouble(HELD) : null;
    }

    /** Ticks until a timed change ends; -1 for none, {@link Long#MAX_VALUE} for a full-moon change. */
    public static long remaining(VillagerEntityMCA villager) {
        Long until = UNTIL.get(villager.getUUID());
        if (until == null) return -1;
        return until == WHOLE_NIGHT ? WHOLE_NIGHT : Math.max(0, until - villager.level().getGameTime());
    }

    /** The game time of the next full-moon nightfall. */
    public static long nextFullMoonNight(Level level) {
        long now = level.getDayTime();
        long target = now - Math.floorMod(now, 192000L) + 13000;
        return target <= now ? target + 192000 : target;
    }

    public static void tick(VillagerEntityMCA villager) {
        if ((villager.tickCount + villager.getId()) % 20 != 0 || !(villager.level() instanceof ServerLevel level)) return;
        if (EntityStates.definition(WEREWOLF) == null || EntityStates.definition(FORM) == null) return;
        UUID id = villager.getUUID();
        EntityStates.Resolved werewolf = EntityStates.resolve(villager, WEREWOLF);
        double form = EntityStates.resolve(villager, FORM).amount();
        if (!werewolf.active() || werewolf.amount() < 1) {
            if (form > 0) setForm(villager, 0);
            LAST_HEALTH.remove(id);
            UNTIL.remove(id);
            FORM_SINCE.remove(id);
            COMMANDED.remove(id);
            KEPT_BACK.remove(id);
            release(villager);
            return;
        }

        settleLooks(villager);
        Double held = held(villager);
        if (held != null) {
            if (form != held) setForm(villager, held);
            return;
        }
        float health = villager.getHealth();
        Float before = LAST_HEALTH.put(id, health);
        double rage = EntityStates.resolve(villager, RAGE).amount();
        if (before != null && health < before) rage += (before - health) * RAGE_PER_DAMAGE;
        rage = Math.max(0, rage - RAGE_DRAIN);

        long now = level.getGameTime();
        boolean night = isNight(level), moon = isFullMoon(level);
        if (form <= 0) {
            Long keptBack = KEPT_BACK.get(id);
            if (keptBack != null && keptBack != level.getDayTime() / 24000L) {
                KEPT_BACK.remove(id);
                keptBack = null;
            }
            if (night && (moon && keptBack == null || rage > RAGE_TO_TURN)) {
                UNTIL.put(id, moon ? WHOLE_NIGHT : now + transformSeconds() * 20L);
                rage = 0;
                if (villager.isSleeping()) villager.stopSleeping();
                setForm(villager, pick(villager));
                FORM_SINCE.put(id, now);
            }
        } else {
            Long until = UNTIL.get(id);
            // A commanded change runs its full time and keeps its form. Otherwise a full-moon change
            // (or one whose timer a reload lost) ends with the full moon; a rage change ends when
            // its time is up; every change ends at sunrise.
            boolean commanded = until != null && COMMANDED.contains(id);
            boolean over;
            if (commanded) over = now > until;
            else if (!night) over = true;
            else if (until == null || until == WHOLE_NIGHT) over = !moon;
            else over = now > until;
            if (over) {
                setForm(villager, 0);
                UNTIL.remove(id);
                COMMANDED.remove(id);
            } else if (!commanded && now - FORM_SINCE.getOrDefault(id, 0L) >= FORM_COOLDOWN) {
                double wanted = pick(villager);
                if (wanted != form) {
                    setForm(villager, wanted);
                    FORM_SINCE.put(id, now);
                }
            }
        }
        EntityStates.set(villager, RAGE, rage, 0, null);
    }

    private static final ResourceLocation COAT = ResourceLocation.tryParse("townstead_state:werewolf_coat");
    private static final ResourceLocation EYES = ResourceLocation.tryParse("townstead_state:werewolf_eyes");
    private static final ResourceLocation GLOW = ResourceLocation.tryParse("townstead_state:werewolf_glow");

    /**
     * Writes down the coat and eyes a werewolf has had all along (fixed by who they are) the first time,
     * so the eyes on their human face and on the beast always match, and a choice later replaces them.
     */
    private static void settleLooks(VillagerEntityMCA villager) {
        settle(villager, COAT, "coat", 11);
        settle(villager, EYES, "eyes", 9);
        // Eyes glow unless someone turns it off (state 2 is on, 1 is off).
        if (EntityStates.definition(GLOW) != null && !(EntityStates.resolve(villager, GLOW).amount() >= 1)) {
            EntityStates.set(villager, GLOW, 2, 0, null);
        }
    }

    private static void settle(VillagerEntityMCA villager, ResourceLocation state, String name, int count) {
        if (EntityStates.definition(state) == null) return;
        EntityStates.Resolved current = EntityStates.resolve(villager, state);
        if (current.active() && current.amount() >= 1) return;
        int value = Math.floorMod(com.aetherianartificer.townstead.pheno.state.StateForms.fallback(villager, name), count);
        EntityStates.set(villager, state, value + 1, 0, null);
    }

    /** Upright when something hostile is within reach, else on four legs. */
    private static double pick(VillagerEntityMCA villager) {
        LivingEntity target = villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        return target != null && target.isAlive() && villager.distanceToSqr(target) <= UPRIGHT_RANGE * UPRIGHT_RANGE ? 2 : 1;
    }

    private static void setForm(VillagerEntityMCA villager, double form) {
        EntityStates.set(villager, FORM, form, 0, null);
    }

    // The Werewolves mod's own night and full moon, so villagers turn when its werewolves do.
    static boolean isNight(Level level) {
        long time = level.getDayTime() % 24000;
        return !level.dimensionType().hasFixedTime() && time > 12786 && time < 23216;
    }

    static boolean isFullMoon(Level level) {
        long time = level.getDayTime() % 192000;
        return !level.dimensionType().hasFixedTime() && time > 12786 && time < 23216;
    }

    private static volatile Integer seconds;

    /** The Werewolves config's transform time, in seconds (25 by default). */
    private static int transformSeconds() {
        Integer cached = seconds;
        if (cached != null) return cached;
        int value = 25;
        try {
            Object balance = Class.forName("de.teamlapen.werewolves.config.WerewolvesConfig").getField("BALANCE").get(null);
            Object props = balance.getClass().getField("MOBPROPS").get(balance);
            Object spec = props.getClass().getField("werewolf_transform_duration").get(props);
            value = ((Number) spec.getClass().getMethod("get").invoke(spec)).intValue();
        } catch (Throwable ignored) {
            // The mod's default.
        }
        seconds = value;
        return value;
    }
}
