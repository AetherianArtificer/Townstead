package com.aetherianartificer.townstead.politics.state;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** The persisted result of founding a settlement; reloads never reinterpret this identity. */
public record SettlementFoundingRecord(SettlementRef settlement,
                                       ResourceLocation profile,
                                       @Nullable ResourceLocation culture,
                                       @Nullable ResourceLocation government,
                                       @Nullable ResourceLocation foundingBiome,
                                       float naturalWeight,
                                       long foundedAt) {
    public SettlementFoundingRecord {
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(profile, "profile");
        if (naturalWeight < 0.0F || !Float.isFinite(naturalWeight)) naturalWeight = 0.0F;
    }
}
