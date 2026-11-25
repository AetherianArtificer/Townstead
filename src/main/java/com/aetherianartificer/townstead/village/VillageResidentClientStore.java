package com.aetherianartificer.townstead.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class VillageResidentClientStore {
    private static volatile List<Resident> residents = List.of();
    private static volatile long receivedAtMillis;

    private VillageResidentClientStore() {}

    /**
     * Whether a worker has a workplace. {@code state} is one of the {@code JobRetention}
     * states, {@code endsInTicks} counts from when the list arrived, and {@code blockId} names
     * the job block when the profession has exactly one.
     */
    public record JobStatus(int state, int endsInTicks, boolean seat, String blockId) {
        public static final JobStatus WORKING = new JobStatus(0, -1, false, "");

        public JobStatus {
            blockId = blockId != null ? blockId : "";
        }

        /** Ticks left after {@code elapsedTicks}, or -1 when the job is not ending. */
        public int remainingTicks(long elapsedTicks) {
            if (endsInTicks < 0) return -1;
            return (int) Math.max(0L, endsInTicks - Math.max(0L, elapsedTicks));
        }

        /** Minutes and seconds of real time, as {@code 14:20}. */
        public static String clock(int ticks) {
            int seconds = Math.max(0, ticks) / 20;
            return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
        }
    }

    public record Resident(UUID villagerUuid, String name, String professionId, int professionLevel, int[] shifts,
                           String chronotype, String chronotypeLabel, int[] sleepHours, String templateId,
                           JobStatus job) {
        public Resident {
            job = job != null ? job : JobStatus.WORKING;
            name = name != null ? name : "???";
            professionId = professionId != null ? professionId : "minecraft:none";
            professionLevel = Math.max(1, professionLevel);
            shifts = shifts != null ? shifts.clone() : new int[0];
            chronotype = chronotype != null ? chronotype : "standard";
            chronotypeLabel = chronotypeLabel != null ? chronotypeLabel : "";
            sleepHours = sleepHours != null ? sleepHours.clone() : new int[0];
            templateId = templateId != null ? templateId : "";
        }

        public Resident withShifts(int[] updatedShifts) {
            return new Resident(villagerUuid, name, professionId, professionLevel, updatedShifts,
                    chronotype, chronotypeLabel, sleepHours, templateId, job);
        }

        public Resident withProfession(String updatedProfessionId, int updatedLevel) {
            return new Resident(villagerUuid, name, updatedProfessionId, updatedLevel, shifts,
                    chronotype, chronotypeLabel, sleepHours, templateId, JobStatus.WORKING);
        }
    }

    /** Ticks since the list arrived, for counting down between syncs. */
    public static long ticksSinceReceived() {
        return Math.max(0L, (System.currentTimeMillis() - receivedAtMillis) / 50L);
    }

    public static void set(List<Resident> updatedResidents) {
        receivedAtMillis = System.currentTimeMillis();
        if (updatedResidents == null || updatedResidents.isEmpty()) {
            residents = List.of();
            return;
        }
        List<Resident> copy = new ArrayList<>(updatedResidents.size());
        for (Resident resident : updatedResidents) {
            if (resident != null) copy.add(resident);
        }
        copy.sort(Comparator.comparing(Resident::name, String.CASE_INSENSITIVE_ORDER));
        residents = List.copyOf(copy);
    }

    public static List<Resident> getResidents() {
        return residents;
    }

    public static Resident get(UUID villagerUuid) {
        if (villagerUuid == null) return null;
        for (Resident resident : residents) {
            if (villagerUuid.equals(resident.villagerUuid())) return resident;
        }
        return null;
    }

    public static void updateShifts(UUID villagerUuid, int[] shifts) {
        update(villagerUuid, resident -> resident.withShifts(shifts));
    }

    public static void updateProfession(UUID villagerUuid, String professionId, int professionLevel) {
        update(villagerUuid, resident -> resident.withProfession(professionId, professionLevel));
    }

    public static void clear() {
        residents = List.of();
    }

    private static void update(UUID villagerUuid, java.util.function.Function<Resident, Resident> updater) {
        if (villagerUuid == null || updater == null) return;
        List<Resident> current = residents;
        if (current.isEmpty()) return;
        List<Resident> updated = new ArrayList<>(current.size());
        boolean changed = false;
        for (Resident resident : current) {
            if (villagerUuid.equals(resident.villagerUuid())) {
                updated.add(updater.apply(resident));
                changed = true;
            } else {
                updated.add(resident);
            }
        }
        if (changed) {
            updated.sort(Comparator.comparing(Resident::name, String.CASE_INSENSITIVE_ORDER));
            residents = List.copyOf(updated);
        }
    }
}
