package com.aetherianartificer.townstead.api.v1.result;

/** Outcome of a need mutation. {@code before} and {@code after} are on the need's own scale. */
public record NeedResult(
        Status status,
        String needId,
        int before,
        int after,
        int min,
        int max,
        String detail
) {
    public NeedResult {
        detail = detail == null ? "" : detail;
    }

    public boolean applied() {
        return status == Status.APPLIED;
    }

    public String statusId() {
        return status.id();
    }

    public static NeedResult failed(Status status, String needId, String detail) {
        return new NeedResult(status, needId, 0, 0, 0, 0, detail);
    }

    public enum Status {
        /** The value moved. */
        APPLIED("applied"),
        /** The request was valid but the value already sat where it was asked to go. */
        NO_CHANGE("no_change"),
        /** The need is not simulated for this villager, for example thirst without a thirst mod. */
        GATED("gated"),
        UNKNOWN_NEED("unknown_need"),
        NOT_A_VILLAGER("not_a_villager"),
        /** Writes from this source are switched off in Townstead's config. */
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
