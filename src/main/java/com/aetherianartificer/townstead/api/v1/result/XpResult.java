package com.aetherianartificer.townstead.api.v1.result;

/**
 * Outcome of a profession XP award. A partial award is still {@link Status#APPLIED};
 * {@code applied} says how much landed.
 */
public record XpResult(
        Status status,
        String professionId,
        int requested,
        int applied,
        int xpBefore,
        int xpAfter,
        int tierBefore,
        int tierAfter,
        String detail
) {
    public XpResult {
        detail = detail == null ? "" : detail;
    }

    public boolean tierUp() {
        return tierAfter > tierBefore;
    }

    public boolean capped() {
        return status == Status.APPLIED && applied < requested;
    }

    public String statusId() {
        return status.id();
    }

    public static XpResult failed(Status status, String professionId, int requested, String detail) {
        return new XpResult(status, professionId, requested, 0, 0, 0, 0, 0, detail);
    }

    public enum Status {
        APPLIED("applied"),
        /** Nothing landed because today's allowance is spent. */
        DAILY_CAP("daily_cap"),
        /** Nothing landed because the track's ceiling is reached. */
        AT_MAX("at_max"),
        /** The profession has no progression configured. */
        NO_PROGRESSION("no_progression"),
        NOT_A_VILLAGER("not_a_villager"),
        INVALID("invalid"),
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
