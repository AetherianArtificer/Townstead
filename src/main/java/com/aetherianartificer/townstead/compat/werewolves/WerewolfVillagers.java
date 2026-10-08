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

    private WerewolfVillagers() {}

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
            return;
        }

        settleLooks(villager);
        float health = villager.getHealth();
        Float before = LAST_HEALTH.put(id, health);
        double rage = EntityStates.resolve(villager, RAGE).amount();
        if (before != null && health < before) rage += (before - health) * RAGE_PER_DAMAGE;
        rage = Math.max(0, rage - RAGE_DRAIN);

        long now = level.getGameTime();
        boolean night = isNight(level), moon = isFullMoon(level);
        if (form <= 0) {
            if (night && (moon || rage > RAGE_TO_TURN)) {
                UNTIL.put(id, moon ? WHOLE_NIGHT : now + transformSeconds() * 20L);
                rage = 0;
                if (villager.isSleeping()) villager.stopSleeping();
                setForm(villager, pick(villager));
                FORM_SINCE.put(id, now);
            }
        } else {
            Long until = UNTIL.get(id);
            // A full-moon change (or one whose timer a reload lost) ends with the full moon; a rage
            // change ends when its time is up; every change ends at sunrise.
            boolean over;
            if (!night) over = true;
            else if (until == null || until == WHOLE_NIGHT) over = !moon;
            else over = now > until;
            if (over) {
                setForm(villager, 0);
                UNTIL.remove(id);
            } else if (now - FORM_SINCE.getOrDefault(id, 0L) >= FORM_COOLDOWN) {
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
