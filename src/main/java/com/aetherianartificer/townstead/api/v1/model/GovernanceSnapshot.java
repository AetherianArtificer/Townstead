package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Who governs a polity and how firmly. {@code form} is the government organization's kind, such
 * as {@code townstead:village_council}. {@code headOffice} and {@code head} name the office that
 * leads and its current holder. Every office of the form is listed with its holders, including
 * empty ones. {@code legitimacy} is 0 to 100 and {@code band} one of {@code resented},
 * {@code uneasy}, {@code tolerated}, {@code accepted}, {@code beloved}; both are empty for a form
 * without governance data. When another mod governs the polity in Townstead's place,
 * {@code provider} names it (for example {@code mcacapitals}) and that mod's authority applies.
 */
public record GovernanceSnapshot(ResourceLocation polity,
                                 ResourceLocation government,
                                 ResourceLocation form,
                                 Optional<ResourceLocation> headOffice,
                                 Optional<UUID> head,
                                 List<Office> offices,
                                 OptionalInt legitimacy,
                                 Optional<String> band,
                                 Optional<ResourceLocation> succession,
                                 Optional<String> provider) {
    public GovernanceSnapshot {
        offices = List.copyOf(offices);
    }

    /** One office of the form and the people holding it now. */
    public record Office(ResourceLocation role, int minimum, int maximum, List<UUID> holders) {
        public Office {
            holders = List.copyOf(holders);
        }
    }
}
