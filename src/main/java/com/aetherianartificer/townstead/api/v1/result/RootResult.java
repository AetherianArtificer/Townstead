package com.aetherianartificer.townstead.api.v1.result;

/** Outcome of a root assignment. Ids are canonical root ids; {@code before} is empty on failure. */
public record RootResult(Status status, String before, String after, String detail) {
    public RootResult {
        before = before == null ? "" : before;
        after = after == null ? "" : after;
        detail = detail == null ? "" : detail;
    }

    public boolean applied() {
        return status == Status.APPLIED;
    }

    public String statusId() {
        return status.id();
    }

    public static RootResult failed(Status status, String detail) {
        return new RootResult(status, "", "", detail);
    }

    public enum Status {
        /** The root changed; body metrics were re-rolled inside the new root's ranges. */
        APPLIED("applied"),
        /** The target already has that root. Nothing was touched. */
        NO_CHANGE("no_change"),
        UNKNOWN_ROOT("unknown_root"),
        /** The root exists but the server's root blocklist hides it. */
        BLOCKED("blocked"),
        /** Neither an MCA villager nor a server player. */
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
