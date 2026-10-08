package com.aetherianartificer.townstead.food;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Side-effect ordering primitive shared by every external serving source. */
final class AtomicServingCommit {
    enum Status { COMMITTED, REFUSED, COMMITTED_ERROR }

    record Outcome<T>(Status status, T value, String detail) {
        boolean committed() { return status != Status.REFUSED; }
    }

    private AtomicServingCommit() {}

    static <T> Outcome<T> execute(BooleanSupplier commitSource, Supplier<T> finish) {
        try {
            if (!commitSource.getAsBoolean()) {
                return new Outcome<>(Status.REFUSED, null,
                        "serving source refused the atomic reservation");
            }
        } catch (RuntimeException exception) {
            // A throwing source may have mutated before it failed. Mark it committed so callers
            // never retry and risk duplication; source implementations are required to be atomic.
            return new Outcome<>(Status.COMMITTED_ERROR, null,
                    "source commit threw " + exception.getClass().getSimpleName()
                            + ": " + exception.getMessage());
        }
        try {
            return new Outcome<>(Status.COMMITTED, finish.get(), "");
        } catch (RuntimeException exception) {
            return new Outcome<>(Status.COMMITTED_ERROR, null,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }
}
