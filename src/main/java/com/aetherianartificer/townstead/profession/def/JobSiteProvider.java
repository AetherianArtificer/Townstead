package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * One way a profession's job becomes available in the world, declared in the def's {@code poi}
 * list. Providers are data: the engine derives slot policy and (in later passes) assignment and
 * job-change surfaces from them, instead of hardcoding which profession needs which site.
 *
 * <p>Built-in types: {@code townstead:job_block} (a vanilla-style job-site block, e.g. the
 * composter enabling Farmer), {@code townstead:building} (a Townstead/MCA building type by id
 * prefix, e.g. kitchens enabling Cook), and {@code townstead:always} (no site required, e.g.
 * martial roles). New types register through {@link JobSiteProviders}.</p>
 */
public interface JobSiteProvider {

    String typeKey();

    /** A vanilla-style job-site block. */
    record JobBlock(Set<ResourceLocation> blocks) implements JobSiteProvider {
        public JobBlock { blocks = Set.copyOf(blocks); }
        public static final String KEY = "townstead:job_block";
        @Override public String typeKey() { return KEY; }
    }

    /** A building whose type id starts with one of the given prefixes. */
    record Building(List<String> typePrefixes) implements JobSiteProvider {
        public Building { typePrefixes = List.copyOf(typePrefixes); }
        public static final String KEY = "townstead:building";
        @Override public String typeKey() { return KEY; }
    }

    /** No physical site required. */
    record Always() implements JobSiteProvider {
        public static final String KEY = "townstead:always";
        @Override public String typeKey() { return KEY; }
    }
}
