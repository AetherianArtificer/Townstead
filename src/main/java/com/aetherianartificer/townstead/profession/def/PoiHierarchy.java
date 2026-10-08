package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;

/**
 * Pure predicates over a def's ordered {@code poi} acquisition hierarchy: the first entry is
 * the primary surface, and subordinate {@code townstead:job_block} entries may declare
 * {@code via} — the (alias) profession whose vanilla POI claim manifests that surface. Village
 * capacity math built on these lives in
 * {@link com.aetherianartificer.townstead.profession.ProfessionCapacity}; this class stays free
 * of MCA types so schema logic loads in unit tests.
 */
public final class PoiHierarchy {

    private PoiHierarchy() {}

    /** Whether the def's poi list declares subordinate acquisition surfaces at all. */
    public static boolean hasAcquisitionHierarchy(ProfessionDef def) {
        if (def == null) return false;
        for (JobSiteProvider provider : def.jobSites()) {
            if (provider instanceof JobSiteProvider.JobBlock block && block.via() != null) return true;
        }
        return false;
    }

    /** Whether {@code professionId} is one of the def's declared {@code via} surfaces. */
    public static boolean isAcquisitionSurface(ProfessionDef def, ResourceLocation professionId) {
        if (def == null || professionId == null) return false;
        for (JobSiteProvider provider : def.jobSites()) {
            if (provider instanceof JobSiteProvider.JobBlock block && professionId.equals(block.via())) return true;
        }
        return false;
    }
}
