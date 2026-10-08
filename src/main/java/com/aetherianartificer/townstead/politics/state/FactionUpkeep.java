package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.politics.definition.FactionKind;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps a faction's bonds in step with who lives in its land. Residents are its citizens; a citizen
 * who moves away or dies stops being one, and their offices end with it. A generated faction fills
 * offices that fall below their minimum from its residents.
 */
public final class FactionUpkeep {
    private FactionUpkeep() {}

    public record Result(int citizensAdded, int citizensEnded, int officesFilled) {
        static final Result NONE = new Result(0, 0, 0);
    }

    /** {@code residents} is everyone living in any of the faction's settlements. */
    public static Result run(PoliticalSavedData data, Faction faction, FactionKind kind,
                             Collection<UUID> residents, List<Set<ResourceLocation>> seats, long now) {
        if (data == null || faction == null || kind == null || !faction.active()) return Result.NONE;
        List<UUID> people = sorted(residents);
        Set<UUID> living = new LinkedHashSet<>(people);
        ResourceLocation membership = kind.membership().bond();
        int added = 0, ended = 0;
        if (!faction.settlements().isEmpty()) {
            for (UUID resident : people) {
                for (BondInstance bond : data.activeBonds(Party.person(resident))) {
                    Party other = bond.other(Party.person(resident));
                    if (bond.kind().equals(membership) && bond.provenance().equals(FactionBonds.RESIDENCE)
                            && other != null && !other.equals(faction.party())) {
                        FactionBonds.end(data, bond, now, "moved");
                    }
                }
                if (FactionBonds.member(data, resident, faction.id(), membership)) continue;
                if (FactionBonds.form(data, membership, FactionBonds.sides(membership, faction.id(), resident),
                        FactionBonds.RESIDENCE, now).formed()) added++;
            }
            for (BondInstance bond : data.activeBonds(faction.party())) {
                if (!bond.kind().equals(membership) || !bond.provenance().equals(FactionBonds.RESIDENCE)) continue;
                Party other = bond.other(faction.party());
                UUID person = other == null ? null : other.person();
                if (person != null && !living.contains(person)) {
                    FactionBonds.end(data, bond, now, "left");
                    ended++;
                }
            }
        }
        int filled = kind.generated() ? fill(data, faction, kind, people, seats, now) : 0;
        return new Result(added, ended, filled);
    }

    /**
     * An empty government takes the founding profile's seats in order; otherwise each office below its
     * minimum is filled, preferring people who already hold another office.
     */
    public static int fill(PoliticalSavedData data, Faction faction, FactionKind kind, List<UUID> residents,
                    List<Set<ResourceLocation>> seats, long now) {
        ResourceLocation membership = kind.membership().bond();
        List<UUID> citizens = new ArrayList<>();
        for (UUID resident : residents) if (FactionBonds.member(data, resident, faction.id(), membership)) citizens.add(resident);
        if (citizens.isEmpty()) return 0;
        int filled = 0;
        boolean empty = kind.offices().stream().allMatch(o -> FactionBonds.holders(data, faction.id(), o.bond()).isEmpty());
        if (empty && !seats.isEmpty()) {
            for (int i = 0; i < Math.min(seats.size(), citizens.size()); i++) {
                for (ResourceLocation office : seats.get(i)) {
                    if (kind.office(office) == null) continue;
                    if (FactionBonds.form(data, office, FactionBonds.sides(office, faction.id(), citizens.get(i)),
                            FactionKind.GENERATED, now).formed()) filled++;
                }
            }
        }
        for (FactionKind.Office office : kind.offices()) {
            List<UUID> holders = FactionBonds.holders(data, faction.id(), office.bond());
            if (holders.size() >= office.minimum()) continue;
            List<UUID> candidates = new ArrayList<>(citizens);
            candidates.removeAll(holders);
            candidates.sort(Comparator.comparing((UUID person) -> officeCount(data, faction, kind, person) == 0)
                    .thenComparing(UUID::toString));
            for (UUID candidate : candidates) {
                if (FactionBonds.holders(data, faction.id(), office.bond()).size() >= office.minimum()) break;
                if (FactionBonds.form(data, office.bond(), FactionBonds.sides(office.bond(), faction.id(), candidate),
                        FactionKind.GENERATED, now).formed()) filled++;
            }
        }
        return filled;
    }

    private static int officeCount(PoliticalSavedData data, Faction faction, FactionKind kind, UUID person) {
        int count = 0;
        for (ResourceLocation held : FactionBonds.kinds(data, person, faction.id())) if (kind.office(held) != null) count++;
        return count;
    }

    private static List<UUID> sorted(Collection<UUID> residents) {
        if (residents == null) return List.of();
        return residents.stream().filter(java.util.Objects::nonNull).distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();
    }
}
