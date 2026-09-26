package com.aetherianartificer.townstead.api.v1.result;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Outcome of teaching or removing a skill. {@code removed} lists every skill a removal cascaded to. */
public record SkillResult(
        Status status,
        ResourceLocation skillId,
        Set<ResourceLocation> removed,
        String detail
) {
    public SkillResult {
        removed = removed == null ? Set.of() : Set.copyOf(removed);
        detail = detail == null ? "" : detail;
    }

    public boolean applied() {
        return status == Status.APPLIED;
    }

    /** True when the entity ends in the requested state, whether or not anything changed. */
    public boolean satisfied() {
        return status == Status.APPLIED || status == Status.ALREADY;
    }

    public String statusId() {
        return status.id();
    }

    public static SkillResult failed(Status status, ResourceLocation skillId, String detail) {
        return new SkillResult(status, skillId, Set.of(), detail);
    }

    public enum Status {
        APPLIED("applied"),
        /** Already known, or already absent. */
        ALREADY("already"),
        UNKNOWN_SKILL("unknown_skill"),
        PREREQUISITES_UNMET("prerequisites_unmet"),
        /** The profession's retraining policy forbids removal without {@code force}. */
        LOCKED("locked"),
        NOT_A_VILLAGER("not_a_villager"),
        DISABLED("disabled"),
        ERROR("error");

        private final String id;

        Status(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }
}
