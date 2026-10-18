package com.aetherianartificer.townstead.root.locomotion;

import com.aetherianartificer.townstead.root.ability.Ability;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * How a flier moves through the air. Set by the {@code style} field on a
 * {@code pheno:ability} gene; {@link FlightProfile} infers it when the field is absent.
 *
 * <ul>
 * <li>{@code cruiseHeight}: blocks above the terrain while travelling; for {@link #DRIFT},
 * the float height kept at all times.</li>
 * <li>{@code clearance}: free space kept around the body on open-air legs.</li>
 * <li>{@code glideRatio}: blocks forward per block of sink with no lift; infinite without gravity.</li>
 * <li>{@code climbSlope} / {@code descentSlope}: steepest rise and fall per horizontal block.
 * Zero climb means the style never gains height, so every leg must sink at least
 * one block per {@code glideRatio}.</li>
 * <li>{@code fullLift}: blocks of height a full stamina bar buys (see {@code FlightStamina}).</li>
 * <li>{@code landingRadius}: how far from the destination a landing spot may be.</li>
 * <li>{@code openLanding}: the landing spot needs open ground around it.</li>
 * <li>{@code cruiseSpeed}: blocks per tick in level flight, for weighing a flight against a walk.</li>
 * </ul>
 */
public enum FlightStyle {
    // Glide ratios sit under the real elytra glide (about 8:1, 11:1 with the pilot's sink trim) so plans never overpromise.
    GLIDE("glide", Physics.ELYTRA, 12.0, 1.5, 5.0, 0.0, 1.0, 0.0, 6, false, 0.6),
    FLAP("flap", Physics.ELYTRA, 8.0, 1.5, 5.0, 0.6, 1.0, 32.0, 6, false, 0.6),
    SOAR("soar", Physics.ELYTRA, 16.0, 3.0, 7.0, 0.35, 0.6, 48.0, 10, true, 0.8),
    HOVER("hover", Physics.NO_GRAVITY, 3.0, 0.5, Double.POSITIVE_INFINITY, 4.0, 4.0, 32.0, 4, false, 0.3),
    DRIFT("drift", Physics.NO_GRAVITY, 0.2, 0.25, Double.POSITIVE_INFINITY, 4.0, 4.0, Double.POSITIVE_INFINITY, 3, false, 0.18);

    /** Elytra fall-flight steered by look angle, or gravity-free movement driven directly. */
    public enum Physics { ELYTRA, NO_GRAVITY }

    private final String key;
    private final Physics physics;
    private final double cruiseHeight;
    private final double clearance;
    private final double glideRatio;
    private final double climbSlope;
    private final double descentSlope;
    private final double fullLift;
    private final int landingRadius;
    private final boolean openLanding;
    private final double cruiseSpeed;

    FlightStyle(String key, Physics physics, double cruiseHeight, double clearance, double glideRatio,
                double climbSlope, double descentSlope, double fullLift, int landingRadius, boolean openLanding,
                double cruiseSpeed) {
        this.key = key;
        this.physics = physics;
        this.cruiseHeight = cruiseHeight;
        this.clearance = clearance;
        this.glideRatio = glideRatio;
        this.climbSlope = climbSlope;
        this.descentSlope = descentSlope;
        this.fullLift = fullLift;
        this.landingRadius = landingRadius;
        this.openLanding = openLanding;
        this.cruiseSpeed = cruiseSpeed;
    }

    public String key() { return key; }
    public Physics physics() { return physics; }
    public double cruiseHeight() { return cruiseHeight; }
    public double clearance() { return clearance; }
    public double glideRatio() { return glideRatio; }
    public double climbSlope() { return climbSlope; }
    public double descentSlope() { return descentSlope; }
    public double fullLift() { return fullLift; }
    public int landingRadius() { return landingRadius; }
    public boolean openLanding() { return openLanding; }
    public double cruiseSpeed() { return cruiseSpeed; }

    /** Steepest rise per horizontal block; negative for a style that can only sink. */
    public double maxRiseSlope() {
        return climbSlope > 0 ? climbSlope : -1.0 / glideRatio;
    }

    /** Elytra styles launch and land under open sky; gravity-free styles may fly indoors. */
    public boolean needsOpenSky() {
        return physics == Physics.ELYTRA;
    }

    /** Wings carry the glider styles and hover; the {@code hover} ability carries hover and drift. */
    public boolean allowedOn(Ability ability) {
        return switch (ability) {
            case ELYTRA_FLIGHT -> this != DRIFT;
            case HOVER -> this == HOVER || this == DRIFT;
            default -> false;
        };
    }

    @Nullable
    public static FlightStyle byKey(@Nullable String key) {
        if (key == null) return null;
        String needle = key.toLowerCase(Locale.ROOT);
        for (FlightStyle style : values()) {
            if (style.key.equals(needle)) return style;
        }
        return null;
    }
}
