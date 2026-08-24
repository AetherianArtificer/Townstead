package com.aetherianartificer.townstead.chronicle.emit;

/**
 * Stable keys for Chronicle events emitted directly by code rather than by a data-authored
 * Job or work task. Work completions use the executable resource id automatically; the
 * Chronicle templates and work-history documents that listen to them remain data.
 */
public final class ChronicleTapKeys {

    public static final String MASTERED = "townstead:mastered";
    public static final String LEARNED_CRAFT = "townstead:learned_craft";

    // survival
    public static final String STARVING = "townstead:starving";
    public static final String CURED = "townstead:cured";

    private ChronicleTapKeys() {}
}
