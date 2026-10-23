package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Forms, ends and reads political bonds under the rules their kinds declare. */
public final class FactionBonds {
    public static final ResourceLocation CITIZENSHIP = id("townstead:citizenship");
    public static final String CITIZEN_ROLE = "citizen";
    public static final String STATE_ROLE = "state";
    static final String FACTION_ROLE = "faction";
    static final String HOLDER_ROLE = "holder";
    public static final ResourceLocation RESIDENCE = id("townstead:residence");

    private FactionBonds() {}

    public enum Refusal { NONE, UNKNOWN_KIND, ROLES, DUPLICATE, FULL, REQUIREMENT, CYCLE, INACTIVE_FACTION }

    public record Result(@Nullable BondInstance bond, Refusal refusal) {
        public boolean formed() { return bond != null; }
    }

    /** The two sides of an office or membership bond, named by the kind's own faction and person roles. */
    public static List<BondInstance.Side> sides(ResourceLocation kind, ResourceLocation faction, UUID person) {
        BondKind definition = BondKinds.all().get(kind);
        String factionRole = FACTION_ROLE, personRole = HOLDER_ROLE;
        if (definition != null && definition.officeShaped()) {
            factionRole = definition.roleFor(BondKind.Party.FACTION).name();
            personRole = definition.roleFor(BondKind.Party.PERSON).name();
        } else if (kind.equals(CITIZENSHIP)) {
            factionRole = STATE_ROLE;
            personRole = CITIZEN_ROLE;
        }
        return List.of(new BondInstance.Side(factionRole, Party.faction(faction)),
                new BondInstance.Side(personRole, Party.person(person)));
    }

    public static @Nullable BondInstance membership(PoliticalSavedData data, UUID person, ResourceLocation faction,
                                                   ResourceLocation kind) {
        List<BondInstance> found = data.activeBetween(Party.person(person), Party.faction(faction), kind);
        return found.isEmpty() ? null : found.get(0);
    }

    public static boolean member(PoliticalSavedData data, UUID person, ResourceLocation faction, ResourceLocation kind) {
        return membership(data, person, faction, kind) != null;
    }

    /** Everyone holding an active bond of this kind with the faction, in the order they took it. */
    public static List<UUID> holders(PoliticalSavedData data, ResourceLocation faction, ResourceLocation kind) {
        List<UUID> out = new ArrayList<>();
        Party party = Party.faction(faction);
        for (BondInstance bond : data.activeBonds(party)) {
            if (!bond.kind().equals(kind)) continue;
            Party other = bond.other(party);
            if (other != null && other.person() != null) out.add(other.person());
        }
        return out;
    }

    /** The kinds of the person's active bonds with this faction. */
    public static Set<ResourceLocation> kinds(PoliticalSavedData data, UUID person, ResourceLocation faction) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        Party target = Party.faction(faction);
        for (BondInstance bond : data.activeBonds(Party.person(person))) if (bond.involves(target)) out.add(bond.kind());
        return out;
    }

    /** The faction this one gives its land to, or null for a sovereign. */
    public static @Nullable ResourceLocation parent(PoliticalSavedData data, ResourceLocation faction) {
        Party self = Party.faction(faction);
        for (BondInstance bond : data.activeBonds(self)) {
            BondKind kind = BondKinds.all().get(bond.kind());
            String role = bond.roleOf(self);
            BondKind.Role definition = kind == null || role == null ? null : kind.role(role);
            if (definition == null || !definition.gives().contains(BondKind.LAND)) continue;
            Party other = bond.other(self);
            if (other != null && other.isFaction()) return other.faction();
        }
        return null;
    }

    /** The top of this faction's chain of parents. */
    public static ResourceLocation sovereign(PoliticalSavedData data, ResourceLocation faction) {
        Set<ResourceLocation> seen = new HashSet<>();
        ResourceLocation current = faction;
        while (seen.add(current)) {
            ResourceLocation parent = parent(data, current);
            if (parent == null) return current;
            current = parent;
        }
        return current;
    }

    public static Result form(PoliticalSavedData data, ResourceLocation kind, List<BondInstance.Side> sides,
                              ResourceLocation provenance, long now) {
        BondKind definition = BondKinds.all().get(kind);
        if (definition == null) return new Result(null, Refusal.UNKNOWN_KIND);
        if (sides.size() != 2 || sides.get(0).party().equals(sides.get(1).party())) return new Result(null, Refusal.ROLES);
        for (BondInstance.Side side : sides) {
            BondKind.Role role = definition.role(side.role());
            if (role == null || (role.party() == BondKind.Party.PERSON) != side.party().isPerson()) return new Result(null, Refusal.ROLES);
            if (side.party().isFaction()) {
                Faction faction = data.faction(side.party().faction());
                if (faction == null || !faction.active()) return new Result(null, Refusal.INACTIVE_FACTION);
            }
        }
        if (!definition.symmetric() && sides.get(0).role().equals(sides.get(1).role())) return new Result(null, Refusal.ROLES);
        Party a = sides.get(0).party(), b = sides.get(1).party();
        if (definition.uniquePerPair() && !data.activeBetween(a, b, kind).isEmpty()) return new Result(null, Refusal.DUPLICATE);
        if (!seatFree(data, kind, sides)) return new Result(null, Refusal.FULL);
        for (BondInstance.Side side : sides) {
            BondKind.Role role = definition.role(side.role());
            if (!role.unlimited() && count(data, side.party(), kind, side.role()) >= role.max()) return new Result(null, Refusal.FULL);
            Party other = side.party().equals(a) ? b : a;
            if (role.requires() != null && data.activeBetween(side.party(), other, role.requires()).isEmpty()) {
                return new Result(null, Refusal.REQUIREMENT);
            }
            if (role.gives().contains(BondKind.LAND) && other.isFaction() && side.party().isFaction()
                    && descends(data, other.faction(), side.party().faction())) {
                return new Result(null, Refusal.CYCLE);
            }
        }
        BondInstance bond = new BondInstance(newId(), kind, sides, now, BondInstance.ONGOING, provenance, "");
        data.putBond(bond);
        return new Result(bond, Refusal.NONE);
    }

    /** An office's seats are counted per faction, as its faction kind declares them. */
    private static boolean seatFree(PoliticalSavedData data, ResourceLocation kind, List<BondInstance.Side> sides) {
        for (BondInstance.Side side : sides) {
            if (!side.party().isFaction()) continue;
            Faction faction = data.faction(side.party().faction());
            FactionKind factionKind = faction == null ? null : PoliticalDefinitions.snapshot().kind(faction.kind());
            FactionKind.Office office = factionKind == null ? null : factionKind.office(kind);
            if (office != null && office.full(holders(data, faction.id(), kind).size())) return false;
        }
        return true;
    }

    /** Whether this person could take a bond of this kind with the faction: every bond it requires is in place. */
    public static boolean eligible(PoliticalSavedData data, ResourceLocation kind, ResourceLocation faction, UUID person) {
        BondKind definition = BondKinds.all().get(kind);
        BondKind.Role role = definition == null ? null : definition.roleFor(BondKind.Party.PERSON);
        return role != null && (role.requires() == null
                || !data.activeBetween(Party.person(person), Party.faction(faction), role.requires()).isEmpty());
    }

    /** Ends the bond and every bond that required it between the same two parties. */
    public static void end(PoliticalSavedData data, BondInstance bond, long now, String reason) {
        if (bond == null || !bond.active()) return;
        data.putBond(bond.ended(now, reason));
        Party a = bond.sides().get(0).party(), b = bond.sides().get(1).party();
        if (!data.activeBetween(a, b, bond.kind()).isEmpty()) return;
        for (BondInstance dependent : data.activeBonds(a)) {
            if (!dependent.involves(b)) continue;
            BondKind kind = BondKinds.all().get(dependent.kind());
            if (kind == null) continue;
            for (BondKind.Role role : kind.roles().values()) {
                if (bond.kind().equals(role.requires())) {
                    end(data, dependent, now, reason);
                    break;
                }
            }
        }
    }

    /** Ends every active bond a party holds, as when a person dies or starts a new life. */
    public static int endAll(PoliticalSavedData data, Party party, long now, String reason) {
        int ended = 0;
        for (BondInstance bond : data.activeBonds(party)) {
            BondInstance current = data.bond(bond.id());
            if (current == null || !current.active()) continue;
            end(data, current, now, reason);
            ended++;
        }
        return ended;
    }

    private static long count(PoliticalSavedData data, Party party, ResourceLocation kind, String role) {
        long count = 0;
        for (BondInstance bond : data.activeBonds(party)) {
            if (bond.kind().equals(kind) && role.equals(bond.roleOf(party))) count++;
        }
        return count;
    }

    /** True when {@code candidate} is {@code ancestor} or sits beneath it. */
    private static boolean descends(PoliticalSavedData data, ResourceLocation candidate, ResourceLocation ancestor) {
        Set<ResourceLocation> seen = new HashSet<>();
        ResourceLocation current = candidate;
        while (current != null && seen.add(current)) {
            if (current.equals(ancestor)) return true;
            current = parent(data, current);
        }
        return false;
    }

    public static ResourceLocation newId() {
        return id("townstead:bond/" + UUID.randomUUID());
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalStateException(value);
        return id;
    }
}
