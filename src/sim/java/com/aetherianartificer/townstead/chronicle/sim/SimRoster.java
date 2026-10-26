package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.world.ChronicleSubject;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stand-in villagers: enough of a population for roles to bind against. */
public final class SimRoster {

    /** Mixed so pre-history role filters (farmer, cook) have something to bind to. */
    private static final String[] PROFESSIONS = {
            "minecraft:farmer", "townstead:cook", "minecraft:farmer",
            "minecraft:mason", "", "townstead:scribe"
    };

    private SimRoster() {}

    public static List<ChronicleSubject> of(int count, RandomSource rng) {
        List<ChronicleSubject> residents = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            boolean baby = i >= count - 2;
            residents.add(ChronicleSubject.villager(
                    new UUID(rng.nextLong(), rng.nextLong()),
                    SimNames.pick(rng.nextBoolean(), rng),
                    baby,
                    baby ? "" : PROFESSIONS[i % PROFESSIONS.length]));
        }
        return residents;
    }

    /** A named subject, for {@code person} mode. A fixed uuid keeps the life stable. */
    public static ChronicleSubject subject(String name, String profession, UUID uuid,
                                           ChronicleRef.Kind kind) {
        return new ChronicleSubject(uuid, name, kind, false, profession);
    }

    public static ChronicleRef.Kind kind(String name) {
        return "player".equalsIgnoreCase(name) ? ChronicleRef.Kind.PLAYER : ChronicleRef.Kind.VILLAGER;
    }
}
