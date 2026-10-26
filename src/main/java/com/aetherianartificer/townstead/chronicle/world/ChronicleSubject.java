package com.aetherianartificer.townstead.chronicle.world;

import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;

import java.util.UUID;

/**
 * A participant as generation sees it: the handful of facts role binding reads,
 * with no entity behind them. Live code adapts villagers and players into these
 * ({@link ServerChronicleWorld#subjects}); the offline harness fabricates them.
 */
public record ChronicleSubject(UUID uuid, String displayName, ChronicleRef.Kind kind,
                               boolean baby, String professionId, AgeBands ageBands) {

    public ChronicleSubject {
        displayName = displayName == null ? "" : displayName;
        professionId = professionId == null ? "" : professionId;
        ageBands = ageBands == null ? AgeBands.DEFAULT : ageBands;
    }

    public ChronicleSubject(UUID uuid, String displayName, ChronicleRef.Kind kind,
                            boolean baby, String professionId) {
        this(uuid, displayName, kind, baby, professionId, AgeBands.DEFAULT);
    }

    public static ChronicleSubject villager(UUID uuid, String displayName, boolean baby,
                                            String professionId) {
        return new ChronicleSubject(uuid, displayName, ChronicleRef.Kind.VILLAGER, baby, professionId);
    }

    public ChronicleRef ref() {
        return new ChronicleRef(kind, uuid, 0, 0, null, displayName);
    }
}
