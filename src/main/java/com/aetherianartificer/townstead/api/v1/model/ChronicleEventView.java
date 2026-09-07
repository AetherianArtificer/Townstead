package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One ground-truth chronicle event. {@link #reach} is one of {@code none}, {@code witnesses},
 * {@code village}, {@code world}. {@link #causeEventId} and {@link #arcId} are {@code -1} when
 * absent.
 */
public record ChronicleEventView(
        long eventId,
        String templateId,
        long worldDay,
        long gameTime,
        ResourceLocation dimension,
        BlockPos position,
        Optional<VillageId> village,
        String category,
        float magnitude,
        String reach,
        long causeEventId,
        long arcId,
        boolean kept,
        List<Participant> participants,
        Map<String, String> params
) {
    public ChronicleEventView {
        village = village == null ? Optional.empty() : village;
        participants = participants == null ? List.of() : List.copyOf(participants);
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public boolean newsworthy() {
        return !"none".equals(reach);
    }

    public Optional<Participant> participant(String role) {
        for (Participant participant : participants) {
            if (participant.role.equals(role)) return Optional.of(participant);
        }
        return Optional.empty();
    }

    /**
     * Who or what took part. {@code kind} is one of {@code villager}, {@code player},
     * {@code animal}, {@code building}, {@code village}, {@code concept}, {@code item}. Entity kinds
     * carry a {@code uuid}; buildings carry village and building ids in {@code intA}/{@code intB};
     * concepts and items carry their id in {@code id}. {@code witness} is a role, not a kind.
     */
    public record Participant(
            String role,
            String kind,
            Optional<UUID> uuid,
            int intA,
            int intB,
            String id,
            String displayName
    ) {
        public Participant {
            uuid = uuid == null ? Optional.empty() : uuid;
            id = id == null ? "" : id;
            displayName = displayName == null ? "" : displayName;
        }

        public boolean isWitness() {
            return "witness".equals(role);
        }
    }
}
