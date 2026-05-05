package com.aetherianartificer.townstead.spirit;

/**
 * One row in the Spirit page's "X buildings of type Y contribute +Z" list.
 * Computed server-side so the client doesn't have to walk the building list
 * itself on the render thread.
 */
public record ContributorRow(String buildingType, int count, int points) {}
