package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.profession.def.JobSiteProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.function.BiPredicate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everyone employed in one career of one village, whether or not they are in the world.
 *
 * <p>Seats used to be matched against the workers that happened to be loaded. A worker in an
 * unloaded chunk therefore looked like a vacancy: an idle villager was hired into the seat, and
 * when the first worker loaded again the newcomer was released. Tenure is village state, so it is
 * kept here and outlives the entity.</p>
 *
 * <p>A row leaves the roll only on evidence: the worker is loaded and no longer does this work,
 * or the worker is off the village's books.</p>
 */
public final class SeatRoll {

    /** One worker. {@code since} orders seniority; a lower value keeps a seat first. */
    public record Tenure(UUID worker, @Nullable ResourceLocation profession,
                         @Nullable String path, long since) {}

    /** What a loaded worker looks like right now. */
    public record Present(@Nullable ResourceLocation profession, @Nullable String path,
                          long since) {}

    private static final Comparator<Tenure> BY_SENIORITY = Comparator
            .comparingLong(Tenure::since)
            .thenComparing(tenure -> tenure.worker().toString());

    private final List<Tenure> rows = new ArrayList<>();
    private boolean seeded;

    /** Workers in seniority order. */
    public List<Tenure> staff() {
        return List.copyOf(rows);
    }

    public boolean holds(UUID worker) {
        return indexOf(worker) >= 0;
    }

    public boolean seeded() {
        return seeded;
    }

    public void markSeeded() {
        seeded = true;
    }

    /** Adds a worker, or refreshes one. A worker already on the roll keeps their seniority. */
    public boolean enroll(UUID worker, @Nullable ResourceLocation profession,
                          @Nullable String path, long since) {
        int index = indexOf(worker);
        if (index < 0) {
            rows.add(new Tenure(worker, profession, path, since));
            rows.sort(BY_SENIORITY);
            return true;
        }
        Tenure old = rows.get(index);
        Tenure next = new Tenure(worker, profession, path, old.since());
        if (next.equals(old)) return false;
        rows.set(index, next);
        return true;
    }

    public boolean dismiss(UUID worker) {
        return rows.removeIf(row -> row.worker().equals(worker));
    }

    /**
     * Brings the roll in line with the loaded residents.
     *
     * @param working loaded residents who do this work now
     * @param loaded  every loaded resident, working or not
     * @param onBooks every resident the village lists, loaded or not
     * @return whether anything changed
     */
    public boolean sync(Map<UUID, Present> working, Set<UUID> loaded, Set<UUID> onBooks) {
        boolean changed = rows.removeIf(row -> {
            UUID id = row.worker();
            if (working.containsKey(id)) return false;
            // Loaded and doing something else is proof; unloaded is not.
            return loaded.contains(id) || !onBooks.contains(id);
        });
        for (Map.Entry<UUID, Present> entry : working.entrySet()) {
            Present present = entry.getValue();
            changed |= enroll(entry.getKey(), present.profession(), present.path(), present.since());
        }
        return changed;
    }

    /** The site this worker holds, or -1 when there is no seat for them. */
    public int seatOf(UUID worker, List<ProfessionSites.Site> sites,
                      List<JobSiteProvider> providers) {
        return seatOf(worker, sites, providers, null);
    }

    /** As above, with seats a worker may not hold (a plaque that does not list them) passed over. */
    public int seatOf(UUID worker, List<ProfessionSites.Site> sites,
                      List<JobSiteProvider> providers,
                      @Nullable BiPredicate<UUID, ProfessionSites.Site> mayHold) {
        return match(rows, worker, sites, providers, mayHold);
    }

    /**
     * The site this villager would hold after taking {@code profession}. Someone not yet on the
     * roll is the most junior, so a hire only ever fills a seat nobody holds.
     */
    public int seatIfHired(UUID worker, @Nullable ResourceLocation profession,
                           @Nullable String path, List<ProfessionSites.Site> sites,
                           List<JobSiteProvider> providers) {
        return seatIfHired(worker, profession, path, sites, providers, null);
    }

    public int seatIfHired(UUID worker, @Nullable ResourceLocation profession,
                           @Nullable String path, List<ProfessionSites.Site> sites,
                           List<JobSiteProvider> providers,
                           @Nullable BiPredicate<UUID, ProfessionSites.Site> mayHold) {
        List<Tenure> hypothetical = new ArrayList<>(rows);
        int index = indexOf(worker);
        if (index >= 0) {
            hypothetical.set(index, new Tenure(worker, profession, path, rows.get(index).since()));
        } else {
            hypothetical.add(new Tenure(worker, profession, path, Long.MAX_VALUE));
            hypothetical.sort(BY_SENIORITY);
        }
        return match(hypothetical, worker, sites, providers, mayHold);
    }

    private static int match(List<Tenure> staff, UUID worker, List<ProfessionSites.Site> sites,
                             List<JobSiteProvider> providers,
                             @Nullable BiPredicate<UUID, ProfessionSites.Site> mayHold) {
        int asking = -1;
        List<String> paths = new ArrayList<>(staff.size());
        List<ResourceLocation> professions = new ArrayList<>(staff.size());
        for (int i = 0; i < staff.size(); i++) {
            Tenure row = staff.get(i);
            if (row.worker().equals(worker)) asking = i;
            paths.add(row.path());
            professions.add(row.profession());
        }
        if (asking < 0) return -1;
        java.util.function.BiPredicate<Integer, Integer> eligible = mayHold == null ? null
                : (w, site) -> mayHold.test(staff.get(w).worker(), sites.get(site));
        return ProfessionSites.assignedSiteIndex(paths, professions, sites, providers, asking, eligible);
    }

    private int indexOf(UUID worker) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).worker().equals(worker)) return i;
        }
        return -1;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("seeded", seeded);
        ListTag list = new ListTag();
        for (Tenure row : rows) {
            CompoundTag t = new CompoundTag();
            t.putUUID("worker", row.worker());
            if (row.profession() != null) t.putString("profession", row.profession().toString());
            if (row.path() != null) t.putString("path", row.path());
            t.putLong("since", row.since());
            list.add(t);
        }
        tag.put("staff", list);
        return tag;
    }

    public static SeatRoll load(CompoundTag tag) {
        SeatRoll roll = new SeatRoll();
        roll.seeded = tag.getBoolean("seeded");
        ListTag list = tag.getList("staff", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("worker")) continue;
            roll.rows.add(new Tenure(t.getUUID("worker"),
                    t.contains("profession") ? ResourceLocation.tryParse(t.getString("profession")) : null,
                    t.contains("path") ? t.getString("path") : null,
                    t.getLong("since")));
        }
        roll.rows.sort(BY_SENIORITY);
        return roll;
    }
}
