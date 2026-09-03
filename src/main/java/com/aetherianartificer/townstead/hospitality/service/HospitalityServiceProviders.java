package com.aetherianartificer.townstead.hospitality.service;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic registry for native and optional-mod service providers. */
public final class HospitalityServiceProviders {
    private static final Map<ResourceLocation, HospitalityServiceProvider> PROVIDERS = new LinkedHashMap<>();

    private HospitalityServiceProviders() {}

    public static synchronized void register(HospitalityServiceProvider provider) {
        Objects.requireNonNull(provider, "provider");
        HospitalityServiceProvider previous = PROVIDERS.putIfAbsent(provider.id(), provider);
        if (previous != null && previous != provider) {
            throw new IllegalStateException("Hospitality service provider already registered: " + provider.id());
        }
    }

    public static synchronized HospitalityServiceProvider get(ResourceLocation id) {
        return PROVIDERS.get(id);
    }

    public static synchronized List<HospitalityServiceProvider> all() {
        return List.copyOf(PROVIDERS.values());
    }

    static synchronized void replaceForTest(List<HospitalityServiceProvider> providers) {
        PROVIDERS.clear();
        List<HospitalityServiceProvider> copy = new ArrayList<>(providers);
        copy.forEach(HospitalityServiceProviders::register);
    }
}
