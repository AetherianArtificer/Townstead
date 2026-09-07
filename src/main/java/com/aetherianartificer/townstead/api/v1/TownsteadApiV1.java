package com.aetherianartificer.townstead.api.v1;

/**
 * Townstead's stable integration surface, version 1.
 *
 * <p>Everything reachable from here lives in {@code com.aetherianartificer.townstead.api.v1} and
 * names only {@code java.*}, stable {@code net.minecraft.*} types, and itself. No MCA type, no
 * Townstead internal type, and no internal enum ever crosses this boundary, so a consumer compiled
 * or reflected against it is never linked to an MCA package layout.
 *
 * <p>Contract, in full:
 * <ul>
 *   <li>Frozen once shipped. Methods are added, never removed or re-signatured. New interface
 *       methods carry {@code default} bodies. Record components are appended, never renamed or
 *       reordered; a record's canonical constructor is not contract, only its accessors are.</li>
 *   <li>{@link #getApiVersion()} changes only on a breaking change, which v1 never makes.
 *       {@link #getApiRevision()} increments on every additive change; {@code docs/API.md} lists
 *       what each revision added. Both read a private constant through a method so a compiler
 *       cannot inline them into a consumer.</li>
 *   <li>Every read is total: an empty {@code Optional} or collection, never {@code null}, never a
 *       throw for "not a villager", "not loaded" or "unknown id". Every mutation returns a result
 *       record with a status and never throws.</li>
 *   <li>Server thread only, except methods returning {@code CompletableFuture}, which complete on
 *       a Townstead reader thread and must be hopped back with {@code server.execute}.</li>
 * </ul>
 */
public interface TownsteadApiV1 {

    /** The implementation class Townstead installs. Resolved lazily; a consumer never names it. */
    String IMPLEMENTATION = "com.aetherianartificer.townstead.api.impl.v1.TownsteadApiV1Impl";

    /**
     * The live API. Available from mod construction onward; safe to call from any thread, but
     * the methods on the returned facades are server-thread only.
     */
    static TownsteadApiV1 get() {
        return ApiHolder.get();
    }

    /** Breaking-change generation. Always {@code 1} for this package. */
    int getApiVersion();

    /** Additive-change counter. Probe it once and cache the answer. */
    int getApiRevision();

    /** Townstead's own version string, for logs. */
    String getModVersion();

    VillagersApi villagers();

    VillagesApi villages();

    ProfessionsApi professions();

    CalendarApi calendar();

    SocialApi social();

    ChroniclesApi chronicles();

    EventsApi events();
}
