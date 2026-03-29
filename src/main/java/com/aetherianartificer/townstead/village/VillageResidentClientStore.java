package com.aetherianartificer.townstead.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class VillageResidentClientStore {
    private static volatile List<Resident> residents = List.of();

    private VillageResidentClientStore() {}

    public record Resident(UUID villagerUuid, String name, String professionId, int professionLevel, int[] shifts) {
        public Resident {
            name = name != null ? name : "???";
            professionId = professionId != null ? professionId : "minecraft:none";
            professionLevel = Math.max(1, professionLevel);
            shifts = shifts != null ? shifts.clone() : new int[0];
        }

        public Resident withShifts(int[] updatedShifts) {
            return new Resident(villagerUuid, name, professionId, professionLevel, updatedShifts);
        }

        public Resident withProfession(String updatedProfessionId, int updatedLevel) {
            return new Resident(villagerUuid, name, updatedProfessionId, updatedLevel, shifts);
        }
    }

    public static void set(List<Resident> updatedResidents) {
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
