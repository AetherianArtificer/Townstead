package com.aetherianartificer.townstead.compat.mess;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
*///?}

import java.util.List;

/**
 * Bit of a Mess, when it's installed. It owns everything that flies, stains and lands on a body, and Traces.
 * Without it there are none of these, and every call here does nothing. Nothing outside {@link MessLink} and
 * {@link MessClientLink} touches its classes, so Townstead runs the same without it; the client half is apart so a server
 * never loads Bit of a Mess's client classes.
 */
public final class MessBridge {

    public static final String MOD_ID = "bitofamess";
    /** How strong a wash is: rain, water, a bath, soap or a cure. */
    public static final int WASH_RAIN = 1, WASH_WATER = 2, WASH_BATH = 3, WASH_SOAP = 4;
    private static final boolean ACTIVE = ModList.get() != null && ModList.get().isLoaded(MOD_ID);

    /** Whether Bit of a Mess is in. */
    public static boolean isActive() {
        return ACTIVE;
    }

    private MessBridge() {}

    public static boolean active() {
        return ACTIVE;
    }

    public static void init() {
        if (!ACTIVE) return;
        MessLink.init();
        com.aetherianartificer.townstead.temperature.RoomHeat.setPools((level, pos) -> pool(level, pos).map(pool ->
                new com.aetherianartificer.townstead.temperature.RoomHeat.Pool(pool.anchor(), pool.celsius(), pool.volume(),
                        pool.airWatts(), pool.vapourKgPerSecond(), pool.heaters())));
        if (FMLEnvironment.dist == Dist.CLIENT) MessClientLink.init();
    }

    /** How sharp what lies on top of {@code ground} is, 0 to 1, such as broken glass. 0 without Bit of a Mess. */
    public static float sharpAt(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos ground) {
        return ACTIVE ? MessLink.sharpAt(level, ground) : 0f;
    }

    /** Sits a body in the washtub at {@code tub} to bathe. False when it can't. */
    public static boolean sitInTub(LivingEntity body, net.minecraft.core.BlockPos tub) {
        return ACTIVE && MessLink.sitInTub(body, tub);
    }

    /** A villager made {@code count} of {@code made} at {@code station}: the mess that work leaves, by Bit of a Mess's rules. */
    public static void worked(LivingEntity worker, net.minecraft.core.BlockPos station, net.minecraft.world.item.ItemStack made, float count) {
        if (ACTIVE && count > 0f) MessLink.worked(worker, station, made, count);
    }

    /** A pool of heated water: its lowest block, temperature, size, heat and water it gives the air, and its heaters. */
    public record Pool(long anchor, double celsius, int volume, double airWatts, double vapourKgPerSecond, long[] heaters) {}

    /** The temperature of the water here, when it is in one of Bit of a Mess's pools. */
    public static java.util.OptionalDouble poolCelsius(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        return ACTIVE ? MessLink.poolCelsius(level, pos) : java.util.OptionalDouble.empty();
    }

    /** The pool the water here is in, if any. */
    public static java.util.Optional<Pool> pool(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        return ACTIVE ? MessLink.pool(level, pos) : java.util.Optional.empty();
    }

    /** The colors a villager's bathing wear is given in, before anyone changes it: muted, as old bathing wear was dyed. */
    private static final int[] SUIT_COLORS = {0x2F4A90, 0x22305E, 0x7A2626, 0x2E5E3A, 0x5A3E6E, 0x8A6A2A, 0x3E5E6E, 0x6E2E4A};

    /**
     * A villager with nothing to bathe in yet is given something at random from what suits their gender, in a color of its own,
     * kept for life. Its Wardrobe changes it after.
     */
    public static void dressForBathing(net.minecraft.world.entity.Entity entity) {
        if (!ACTIVE || !(entity instanceof net.conczin.mca.entity.VillagerEntityMCA villager)) return;
        if (MessLink.wearOf(villager).isPresent()) return;
        // A villager just spawned has no gender yet: they're dressed once they have one, on the Wardrobe's own rounds.
        String gender = switch (villager.getGenetics().getGender()) {
            case FEMALE -> "feminine";
            case MALE -> "masculine";
            case NEUTRAL -> "neutral";
            default -> null;
        };
        if (gender == null) return;
        long seed = villager.getUUID().getLeastSignificantBits();
        MessLink.pickWear(gender, seed).ifPresent(wear -> MessLink.setWear(villager, wear, SUIT_COLORS[(int) Math.floorMod(seed >>> 8, SUIT_COLORS.length)]));
    }

    /** Sets what a body bathes in, and its color. */
    public static void setBathingWear(LivingEntity body, String wear, int color) {
        if (ACTIVE) MessLink.setWear(body, wear, color);
    }

    public static void addTrace(LivingEntity body, String trace, float amount) {
        if (ACTIVE) MessLink.addTrace(body, trace, amount);
    }

    public static float traceAmount(LivingEntity body, String trace) {
        return ACTIVE ? MessLink.traceAmount(body, trace) : 0f;
    }

    /** Washes off everything this strength reaches, by {@code amount}. Naming a {@code trace} takes just that one. */
    public static void wash(LivingEntity body, int strength, float amount, String trace) {
        if (ACTIVE) MessLink.wash(body, strength, amount, trace);
    }

    /** Something happened to this person: {@code when_attack}, or {@code when_work:till} with its {@code when_work}. */
    public static void event(LivingEntity body, String event) {
        if (ACTIVE) MessLink.event(body, event);
    }

    /** This person just finished eating or drinking this, by a way the game itself did not see. */
    public static void ate(LivingEntity body, net.minecraft.world.item.ItemStack food) {
        if (ACTIVE) MessLink.ate(body, food);
    }

    /** Throws a material from a point on a body, with no wound to go on bleeding. */
    public static void spray(LivingEntity body, String material, Vec3 at, Vec3 way, float force) {
        if (ACTIVE) MessLink.spray(body, material, at, way, force);
    }

    /** As above, thrown in a named pattern ({@code bitofamess:high_pressure}, {@code bitofamess:arc}...). */
    public static void spray(LivingEntity body, String material, Vec3 at, Vec3 way, float force, String pattern) {
        if (ACTIVE) MessLink.spray(body, material, at, way, force, pattern);
    }

    /** A blow that a part took for itself, which the body behind it never felt. */
    public static void partHit(LivingEntity body, Vec3 at, Vec3 way, float amount) {
        if (ACTIVE) MessLink.partHit(body, at, way, amount);
    }

    /** This attachment grew back clean. Empty means all of them. */
    public static void partRegrown(LivingEntity body, String attachment) {
        if (ACTIVE) MessLink.partRegrown(body, attachment);
    }

    /** A part came off here, and {@code trace} is what it leaves. */
    public static void partLost(LivingEntity body, String trace, Vec3 at) {
        if (ACTIVE) MessLink.partLost(body, trace, at);
    }

    public static List<String> traceIds() {
        return ACTIVE ? MessLink.traceIds() : List.of();
    }

    public static boolean hasMaterial(String id) {
        return ACTIVE && MessLink.hasMaterial(id);
    }
}
