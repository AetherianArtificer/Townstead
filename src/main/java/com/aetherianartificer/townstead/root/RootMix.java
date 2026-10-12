package com.aetherianartificer.townstead.root;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A heritage of the player's own making: two to {@value #MAX} roots of one species, each holding a
 * share in quarters that together make a whole. Built in the root browser's Mix mode, rolled into a
 * founder by {@link Heredity#seedMixed}.
 */
public record RootMix(List<Member> members) {

    public static final int MAX = 4;
    /** Shares step in quarters. */
    public static final int WHOLE = 4;

    public record Member(String rootId, int quarters) {}

    public RootMix {
        members = List.copyOf(members);
    }

    /** Two or more distinct roots, every share at least a quarter, the shares a whole. */
    public boolean wellFormed() {
        if (members.size() < 2 || members.size() > MAX) return false;
        Set<String> seen = new HashSet<>();
        int total = 0;
        for (Member m : members) {
            if (m.quarters() < 1 || !seen.add(m.rootId())) return false;
            total += m.quarters();
        }
        return total == WHOLE;
    }

    /** The root holding the largest share; the first listed wins a tie. */
    public String largest() {
        Member best = members.get(0);
        for (Member m : members) if (m.quarters() > best.quarters()) best = m;
        return best.rootId();
    }

    /** A share as players read it: "1/4", "1/2", "3/4". */
    public static String shareLabel(int quarters) {
        return switch (quarters) {
            case 1 -> "1/4";
            case 2 -> "1/2";
            case 3 -> "3/4";
            default -> "1";
        };
    }

    /** As weighted shares, or null when a root is unknown, blocked, or of another species. */
    public List<RootSelector.Weighted> resolve() {
        if (!wellFormed()) return null;
        List<RootSelector.Weighted> out = new ArrayList<>(members.size());
        ResourceLocation species = null;
        for (int i = 0; i < members.size(); i++) {
            ResourceLocation id = RootServerLogic.resolveKnown(members.get(i).rootId());
            if (id == null) return null;
            ResourceLocation s = RootRegistry.effectiveSpecies(id);
            if (i == 0) species = s;
            else if (!java.util.Objects.equals(species, s)) return null;
            out.add(new RootSelector.Weighted(id, members.get(i).quarters() / (float) WHOLE));
        }
        return out;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(members.size());
        for (Member m : members) {
            buf.writeUtf(m.rootId());
            buf.writeVarInt(m.quarters());
        }
    }

    public static RootMix read(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX + 1);
        List<Member> members = new ArrayList<>(n);
        for (int i = 0; i < n; i++) members.add(new Member(buf.readUtf(), buf.readVarInt()));
        return new RootMix(members);
    }
}
