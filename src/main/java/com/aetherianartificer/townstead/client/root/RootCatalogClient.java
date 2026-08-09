package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.RootCatalogSyncPayload;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.aetherianartificer.townstead.root.rig.RigRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side copy of the selectable-origin catalog + gene dictionary, fed by
 * {@code RootCatalogSyncPayload}. The picker reads this rather than the
 * server-only registries.
 */
public final class RootCatalogClient {

    private static volatile List<RootCatalogEntry> ROOTS = List.of();
    private static volatile Map<String, GeneCatalogEntry> GENES = Map.of();
    private static volatile Map<String, RigDefinition> RIGS = Map.of();

    private RootCatalogClient() {}

    public static void setFrom(RootCatalogSyncPayload payload) {
        ROOTS = List.copyOf(payload.entries());
        Map<String, GeneCatalogEntry> genes = new LinkedHashMap<>();
        for (GeneCatalogEntry g : payload.genes()) {
            genes.put(g.id(), g);
        }
        GENES = Map.copyOf(genes);
        Map<String, RigDefinition> rigs = new LinkedHashMap<>();
        for (RigDefinition r : payload.rigs()) {
            rigs.put(r.id(), r);
        }
        RIGS = Map.copyOf(rigs);
        // Rig defs may have changed (e.g. a new camera bone); drop the derived eye-height cache.
        com.aetherianartificer.townstead.client.species.RigCamera.invalidate();
        // A rig's declared hitbox may have changed too (e.g. an edited height on /reload). Client dimensions
        // are only recomputed in refreshDimensions, so re-run it on the loaded rigged entities now, otherwise
        // an in-place data-pack tweak won't apply until each entity respawns.
        refreshRiggedDimensions();
        // Bridge data-pack traits into MCA's registry client-side so the editor lists them.
        // (enabledTraits comes from the server via MCA's own config sync.) Defensive against MCA drift.
        boolean any = false;
        for (com.aetherianartificer.townstead.root.TraitCatalogEntry t : payload.traits()) {
            try {
                any |= com.aetherianartificer.townstead.root.trait.TraitBridge.register(
                        t.id(), t.chance(), t.inherit(), t.usableOnPlayer() && !t.hidden());
            } catch (Throwable e) {
                com.aetherianartificer.townstead.Townstead.LOGGER.warn(
                        "Could not register synced trait {} with MCA: {}", t.id(), e.toString());
            }
        }
        if (any) com.aetherianartificer.townstead.root.trait.TraitJsonLoader.enableRegisteredTraits();
    }

    /** Recompute dimensions for loaded rigged entities so an edited rig hitbox applies on /reload (client). */
    private static void refreshRiggedDimensions() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.level == null) return;
            for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof net.conczin.mca.entity.VillagerEntityMCA) e.refreshDimensions();
            }
            if (mc.player != null) mc.player.refreshDimensions();
        });
    }

    public static List<RootCatalogEntry> origins() {
        return ROOTS;
    }

    /** The catalog entry for an origin id, or {@code null} if unknown. */
    public static RootCatalogEntry origin(String id) {
        if (id == null || id.isEmpty()) return null;
        for (RootCatalogEntry e : ROOTS) {
            if (e.id().equals(id)) return e;
        }
        return null;
    }

    /** Gene display data for a granted-gene id, or {@code null} if unknown. */
    public static GeneCatalogEntry gene(String id) {
        return GENES.get(id);
    }

    /** The synced rig definition for a rig id (resolving vanilla aliases), or {@code null} if unknown. */
    public static RigDefinition rig(String id) {
        return RigRegistry.resolve(RIGS, id);
    }

    public static boolean isEmpty() {
        return ROOTS.isEmpty();
    }
}
