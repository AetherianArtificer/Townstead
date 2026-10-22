package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.compat.temperature.LsoEntityCompat;
import com.aetherianartificer.townstead.compat.temperature.RoomHeatBackend;
import com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver;
import com.aetherianartificer.townstead.compat.temperature.ToughAsNailsEntityCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a player feels, by the same rules as a villager, put on the active temperature mod's scale.
 * Townstead owns clothing, wetness's effect, humidity and Root traits for players whenever it owns
 * their environment; the mod keeps its body, HUD, damage, potions and food.
 */
public final class PlayerFeelsLike {
    private PlayerFeelsLike() {}

    private record Cached(long tick, float environment, float wetness, float sent) {}
    private static final Map<Player, Cached> CACHE = new WeakHashMap<>();
    private static final Map<Player, Float> EXERTION = new WeakHashMap<>();

    /** Whether Townstead decides what players feel; the same switch as block heat. */
    public static boolean active() {
        return RoomHeatBackend.ownsBlockHeat();
    }

    /**
     * Feels like for this player in an environment of {@code environment} degrees (air and radiant
     * heat), with {@code wetness} from the mod's own tracking, on the mod's comfort scale.
     */
    public static float sent(Player player, float environment, float wetness) {
        if (!(player.level() instanceof ServerLevel level)) return environment;
        long now = level.getGameTime();
        Cached cached = CACHE.get(player);
        if (cached != null && now - cached.tick() < 10 && cached.environment() == environment && cached.wetness() == wetness)
            return cached.sent();
        ThermalProfile profile = ThermalProfile.of(player);
        ComfortRange base = ComfortRange.active();
        ComfortRange body = body(player, profile, base);
        float sent = toModScale(feels(player, profile, body, environment, wetness), profile, base, body);
        CACHE.put(player, new Cached(now, environment, wetness, sent));
        return sent;
    }

    /** For diagnostics: {feels like, the value sent to the mod} for this player in this environment. */
    public static float[] describe(Player player, float environment, float wetness) {
        ThermalProfile profile = ThermalProfile.of(player);
        ComfortRange base = ComfortRange.active();
        ComfortRange body = body(player, profile, base);
        float feels = feels(player, profile, body, environment, wetness);
        return new float[]{feels, toModScale(feels, profile, base, body), body.low(), body.high()};
    }

    /** The player's own range: the mod's, moved by the Root's offset and, where Townstead keeps it, acclimation. */
    static ComfortRange body(Player player, ThermalProfile profile, ComfortRange base) {
        ComfortRange body = base.offset(profile.comfortOffset());
        if (!ThermalAcclimation.townsteadOwns(player, profile)) return body;
        float acclimation = ThermalAcclimation.player(player);
        return Float.isNaN(acclimation) ? body : ThermalAcclimation.shift(body, acclimation, profile.acclimationLimits());
    }

    static float feels(Player player, ThermalProfile profile, ComfortRange body, float environment, float wetness) {
        ServerLevel level = (ServerLevel) player.level();
        boolean immersed = player.isInWater();
        float humidity = ThermalAir.humidity(level, player.blockPosition(), environment);
        float water = immersed ? ThermalAir.water(level, player.blockPosition()) : environment;
        return ThermalComfort.feelsLike(environment, humidity, immersed ? 1 : wetness, immersed, water,
                exertion(player), protection(player), profile, body);
    }

    /**
     * Moves the player's acclimation toward what they feel, when Townstead keeps it. A player with
     * none yet starts adapted to where they stand.
     */
    public static void acclimate(Player player, float minutes) {
        if (!active() || !(player.level() instanceof ServerLevel level)) return;
        ThermalProfile profile = ThermalProfile.of(player);
        if (!ThermalAcclimation.townsteadOwns(player, profile)) return;
        float current = ThermalAcclimation.player(player);
        if (Float.isNaN(current)) {
            ThermalAcclimation.setPlayer(player, ThermalAcclimation.seed(
                    com.aetherianartificer.townstead.climate.ClimateModel.sample(level, player.blockPosition()).mean()));
            return;
        }
        var environment = RoomHeatBackend.environment(level, player.blockPosition());
        if (environment.isEmpty()) return;
        var bridge = TemperatureBridgeResolver.get();
        float wet = bridge == null ? Float.NaN : bridge.playerWetness(player);
        ComfortRange body = ComfortRange.active().offset(profile.comfortOffset());
        float feels = feels(player, profile, body, (float) environment.getAsDouble(), Float.isFinite(wet) ? wet : 0f);
        ThermalAcclimation.setPlayer(player, ThermalAcclimation.step(current, ThermalAcclimation.target(feels, body),
                ThermalAcclimation.ratePerMinute(), profile.acclimationLimits(), minutes));
    }

    /** Worn clothing and fur-like gear, plus LSO's resistance effects, whose own modifier is set aside. */
    public static ThermalProtection protection(Player player) {
        ThermalProtection protection = Insulation.clothingProtection(player).plus(ToughAsNailsEntityCompat.equipmentProtection(player));
        if (TemperatureBridgeResolver.get() == com.aetherianartificer.townstead.compat.temperature.LsoTemperatureBridge.INSTANCE)
            protection = protection.plus(LsoEntityCompat.effects(player).protection());
        return protection;
    }

    /** Builds the player's exertion from what they are doing now. */
    public static void exert(Player player, float seconds) {
        EXERTION.put(player, ThermalComfort.exertion(exertion(player), activity(player), seconds));
    }

    /** Heat the player makes, as a multiple of rest, built up over the last few minutes. */
    public static float exertion(Player player) {
        Float value = EXERTION.get(player);
        return value == null ? 1 : value;
    }

    /** The same scale as {@link ThermalExposure#activity}: walking 2, working 4, running 6. */
    public static float activity(Player player) {
        if (player.isSprinting()) return 6;
        if (player.swinging) return 4;
        return player.getDeltaMovement().horizontalDistanceSqr() > .0004 ? 2 : 0;
    }

    /**
     * Maps a body's feels like onto the mod's range, so the mod judges it as this body would: the
     * body's own range lines up with the mod's, and degrees past either end count by the body's
     * sensitivity to cold or heat.
     */
    static float toModScale(float feels, ThermalProfile profile, ComfortRange base, ComfortRange body) {
        if (feels < body.low()) return base.low() + (feels - body.low()) * profile.cold();
        if (feels > body.high()) return base.high() + (feels - body.high()) * profile.heat();
        float span = body.high() - body.low();
        return span <= 0 ? base.mid() : base.low() + (feels - body.low()) * (base.high() - base.low()) / span;
    }
}
