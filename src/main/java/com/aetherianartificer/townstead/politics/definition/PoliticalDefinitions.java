package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.social.BondKind;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic, immutable view of the loaded faction kinds. Bond kinds live in {@code BondKinds}. */
public final class PoliticalDefinitions {
    private static volatile Snapshot SNAPSHOT = Snapshot.EMPTY;

    private PoliticalDefinitions() {}

    public static Snapshot snapshot() {
        return SNAPSHOT;
    }

    public static void replace(Map<ResourceLocation, FactionKind> kinds) {
        SNAPSHOT = new Snapshot(kinds);
    }

    /** Every unresolved or misshapen bond reference of one kind; empty when coherent. */
    public static List<String> validate(FactionKind kind, Map<ResourceLocation, BondKind> bonds) {
        List<String> errors = new ArrayList<>();
        BondKind membership = bonds.get(kind.membership().bond());
        if (membership == null) errors.add("membership bond " + kind.membership().bond() + " is not loaded");
        else if (!membership.officeShaped()) errors.add("membership bond " + membership.id() + " needs one person role and one faction role");
        for (FactionKind.Office office : kind.offices()) {
            BondKind bond = bonds.get(office.bond());
            if (bond == null) errors.add("office " + office.bond() + " is not a loaded bond");
            else if (!bond.officeShaped()) errors.add("office " + office.bond() + " needs one person role and one faction role");
        }
        return List.copyOf(errors);
    }

    public record Snapshot(Map<ResourceLocation, FactionKind> factionKinds) {
        private static final Snapshot EMPTY = new Snapshot(Map.of());

        public Snapshot {
            factionKinds = Map.copyOf(new LinkedHashMap<>(factionKinds));
        }

        public @Nullable FactionKind kind(ResourceLocation id) {
            return id == null ? null : factionKinds.get(id);
        }

        public Collection<FactionKind> kinds() {
            return factionKinds.values();
        }
    }
}
