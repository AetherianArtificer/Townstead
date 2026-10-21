package com.aetherianartificer.townstead.climate;

/**
 * Evaporation, the one rule behind rain cooling the air, sweat, wet clothes and drying: anything
 * wet cools only as far as the air's wet-bulb temperature, and gets there faster the thirstier
 * the air is.
 */
public final class Evaporation {
    private Evaporation() {}

    /** Saturation vapour pressure over water, in hPa. */
    public static double saturationPressure(double celsius) {
        return 6.105 * Math.exp(17.27 * celsius / (237.7 + celsius));
    }

    /** How thirsty the air is: saturation pressure less the vapour already there, in hPa. */
    public static double deficit(double celsius, double relativeHumidity) {
        return saturationPressure(celsius) * (1 - Math.max(0, Math.min(1, relativeHumidity)));
    }

    /**
     * Wet-bulb temperature from air temperature and relative humidity (0 to 1), by Stull's
     * empirical fit. Good from about -20 to 50 C and 5 to 99 % humidity.
     */
    public static double wetBulb(double celsius, double relativeHumidity) {
        double rh = Math.max(5, Math.min(99, relativeHumidity * 100));
        return celsius * Math.atan(0.151977 * Math.sqrt(rh + 8.313659))
                + Math.atan(celsius + rh) - Math.atan(rh - 1.676331)
                + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh) - 4.686035;
    }

    /** Cooling from evaporating {@code fraction} of the way to the wet bulb; never negative. */
    public static float cooling(float celsius, float relativeHumidity, float fraction) {
        return (float) Math.max(0, fraction * (celsius - wetBulb(celsius, relativeHumidity)));
    }
}
