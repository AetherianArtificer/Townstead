package com.aetherianartificer.townstead.temperature;

/**
 * The villager body model. {@link ThermalComfort#load} says how the surroundings feel in
 * temperature-equivalent degrees; only the part beyond the comfort zone moves the body, and the
 * body follows slowly, so passing a fire or a cold doorway barely registers while a winter
 * night or a warm hearth does.
 */
public final class BodyHeat {
    private BodyHeat() {}

    /** Environment load beyond the comfort zone, zero inside it. */
    public static float excess(float load, float zone) {
        return load > zone ? load - zone : load < -zone ? load + zone : 0f;
    }

    /** Where the body settles in this environment. Sleep halves cold, as a bed and blanket would. */
    public static float target(float load, float zone, ThermalProfile profile, boolean sleeping) {
        float pull = excess(load, zone) * TemperatureData.AMBIENT_PULL_PER_DEGREE;
        // Ectotherms have less metabolic reserve, but clothing, water and resistances still apply.
        // Express the target relative to the species' neutral, not human ambient vs core Celsius.
        if (profile.ectotherm()) pull *= 1.5f;
        if (sleeping && pull < 0) pull *= TemperatureData.SLEEP_COLD_FACTOR;
        return profile.neutral() + pull;
    }

    /**
     * Fraction of the gap to the target closed over {@code seconds}. A body heading back toward
     * neutral uses the faster recovery constant: warming at a fire is quicker than chilling.
     */
    public static double rate(float body, float target, float neutral, double seconds,
                              double driftSeconds, double recoverySeconds) {
        boolean recovering = (target - body) * (neutral - body) > 0;
        double constant = recovering ? recoverySeconds : driftSeconds;
        return constant <= 0 ? 1 : 1 - Math.exp(-seconds / constant);
    }

    /** Body tenths from the neutral, signed. */
    public static float deviation(int bodyTenths, ThermalProfile profile) {
        return TemperatureData.celsius(bodyTenths) - profile.neutral();
    }

    /** Comfortable bodies need no further enforced recovery. */
    public static boolean recovered(int bodyTenths, ThermalProfile profile) {
        return profile.comfortable(bodyTenths);
    }

    /** Resume the day while mild symptoms finish recovering in a safe environment. */
    public static boolean reliefComplete(float body, float target, ThermalProfile profile) {
        float distance = Math.abs(body - profile.neutral());
        if (distance <= profile.band() + .0001f) return true;
        return distance <= profile.band() * 2 + .0001f
                && Math.abs(target - profile.neutral()) <= profile.band() + .0001f
                && (target - body) * (profile.neutral() - body) > 0;
    }

    /** Bedtime takes precedence over mild recovery; dangerous exposure still needs relief. */
    public static boolean restTakesPriority(boolean resting, TemperatureData.Tier core, boolean freezingWater) {
        return resting && core.severity() < 2 && !freezingWater;
    }

    public static double predict(float body, float target, ThermalProfile profile, double seconds) {
        var settings = TemperatureSettings.get();
        return body + (target - body) * rate(body, target, profile.neutral(), seconds,
                settings.bodyResponseSeconds(), settings.bodyRecoverySeconds());
    }

    /** Severity threshold for full breaks; ThermalCare also applies the Cold/Hot grace period. */
    public static boolean needsBreak(float body, ThermalProfile profile) {
        return Math.abs(body - profile.neutral()) > profile.band() * 3 + .0001f;
    }
}
