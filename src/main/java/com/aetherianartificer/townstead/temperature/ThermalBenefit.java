package com.aetherianartificer.townstead.temperature;

/** Preview of one serving/service; the runtime transaction still owns consumption and effects. */
public record ThermalBenefit(float bodyDelta, float ambientDelta, ThermalProtection protection, int ticks) {
    public static final ThermalBenefit NONE = new ThermalBenefit(0, 0, ThermalProtection.NONE, 0);
    public static ThermalBenefit of(com.aetherianartificer.townstead.needs.NeedEffectProjection projection) {
        return new ThermalBenefit(projection.warmthTenths() / 10f, projection.influenceTenths() / 10f,
                ThermalProtection.NONE, projection.influenceTicks());
    }
    public ThermalExposure apply(ThermalExposure exposure) {
        return new ThermalExposure(exposure.ambient() + ambientDelta, exposure.wetness(), exposure.immersed(),
                exposure.raining(), exposure.activity(), exposure.protection().plus(protection), exposure.profile(), exposure.sleeping());
    }
    public float score(ThermalExposure exposure, float body) {
        float neutral = exposure.profile().neutral();
        if (bodyDelta != 0 && Math.abs(body + bodyDelta - neutral) >= Math.abs(body - neutral)) return 0;
        int activeSeconds = Math.min(120, Math.max(0, ticks / 20));
        float after = apply(exposure).forecast(body + bodyDelta, activeSeconds).body();
        after = exposure.forecast(after, 120 - activeSeconds).body();
        float baseline = exposure.forecast(body, 120).body();
        return Math.max(0, Math.abs(baseline - neutral) - Math.abs(after - neutral));
    }
}
