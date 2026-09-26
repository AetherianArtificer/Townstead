package com.aetherianartificer.townstead.api.v1.result;

import com.aetherianartificer.townstead.api.v1.model.ScheduleSnapshot;

import java.util.Optional;

/** Outcome of a schedule write; {@code after} is the schedule as applied. */
public record ScheduleResult(Status status, Optional<ScheduleSnapshot> after, String detail) {
    public ScheduleResult {
        after = after == null ? Optional.empty() : after;
        detail = detail == null ? "" : detail;
    }

    public boolean applied() {
        return status == Status.APPLIED;
    }

    public String statusId() {
        return status.id();
    }

    public static ScheduleResult failed(Status status, String detail) {
        return new ScheduleResult(status, Optional.empty(), detail);
    }

    public enum Status {
        APPLIED("applied"),
        UNKNOWN_TEMPLATE("unknown_template"),
        INVALID("invalid"),
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
