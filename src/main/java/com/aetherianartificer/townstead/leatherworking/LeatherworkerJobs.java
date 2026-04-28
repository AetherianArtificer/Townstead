package com.aetherianartificer.townstead.leatherworking;

import com.aetherianartificer.townstead.compat.butchery.ButcheryCompat;
import com.aetherianartificer.townstead.compat.butchery.SkinRackJob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of leatherworker jobs evaluated by {@link LeatherworkerWorkTask}
 * in declared order. Vanilla jobs come first; compat jobs self-register
 * behind their mod-loaded gate so the list stays empty when no leather
 * stations are present.
 */
public final class LeatherworkerJobs {
    private static final List<LeatherworkerJob> JOBS;

    static {
        List<LeatherworkerJob> jobs = new ArrayList<>();
        // Compat: Butchery skin rack pipeline (place → salt → soak → collect).
        if (ButcheryCompat.isLoaded()) {
            jobs.add(new SkinRackJob());
        }
        // Future: vanilla cauldron tanning, leather trade prep, etc.
        JOBS = Collections.unmodifiableList(jobs);
    }

    private LeatherworkerJobs() {}

    public static List<LeatherworkerJob> all() {
        return JOBS;
    }
}
