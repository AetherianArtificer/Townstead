package com.aetherianartificer.townstead.client.origin;

import com.aetherianartificer.townstead.origin.OriginSetC2SPayload;
import com.aetherianartificer.townstead.villager.TownsteadVillagerState;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side current-origin cache, keyed by network entity id (or
 * {@link OriginSetC2SPayload#SELF} for the player's own origin). Fed by
 * {@code OriginSyncS2CPayload}; read live by the picker to highlight the current
 * row and by the skin-tint layer. Also caches each entity's expressed gene ids
 * (fed by {@code ExpressedGenesS2CPayload}) so render layers can paint that
 * individual's genetics. Cleared on logout (see {@code Townstead}).
 */
public final class OriginClientStore {

    private static final Map<Integer, String> BY_ENTITY = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<String>> EXPRESSED = new ConcurrentHashMap<>();
    private static final Map<Integer, Map<String, String>> VARIANTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<String>> TOGGLES = new ConcurrentHashMap<>();

    private OriginClientStore() {}

    public static void set(int entityId, String originId) {
        BY_ENTITY.put(entityId, originId == null ? "" : originId);
    }

    /** Current origin id for the target, or empty string if unknown. */
    public static String get(int entityId) {
        return BY_ENTITY.getOrDefault(entityId, "");
    }

    public static String getSelf() {
        return get(OriginSetC2SPayload.SELF);
    }

    /**
     * The entity's current origin id, preferring the live per-entity sync but falling back to the
     * entity's own persisted snapshot when nothing is synced for its id. The fallback covers a
     * CarryOn-reconstructed villager, which is rebuilt from NBT with a brand-new id that the server
     * never tracked, so render layers keep its species (rig, skin tint, proportions) while carried.
     */
    public static String resolve(LivingEntity entity) {
        if (entity == null) return "";
        String synced = get(entity.getId());
        if (synced != null && !synced.isEmpty()) return synced;
        if (entity instanceof VillagerEntityMCA villager) return TownsteadVillagerState.snapshotOriginId(villager);
        return "";
    }

    /** This entity's rolled variant for {@code geneId}, synced if present, else from its snapshot. */
    public static String resolveCarriedVariant(LivingEntity entity, String geneId) {
        if (entity == null) return "";
        String synced = carriedVariants(entity.getId()).get(geneId);
        if (synced != null && !synced.isEmpty()) return synced;
        if (entity instanceof VillagerEntityMCA villager) {
            return TownsteadVillagerState.snapshotCarriedVariant(villager, geneId);
        }
        return "";
    }

    /**
     * Store an entity's expressed alleles: gene ids (for the expressed set) and, for variant genes,
     * the rolled variant id keyed by gene id (so a per-entity skin-tone variant can be resolved).
     */
    public static void setExpressed(int entityId, List<String> alleleEncodings) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        Map<String, String> variants = new ConcurrentHashMap<>();
        for (String encoded : alleleEncodings) {
            if (encoded == null || encoded.isEmpty() || encoded.equals("~")) continue;
            int hash = encoded.indexOf('#');
            if (hash < 0) {
                ids.add(encoded);
            } else {
                ids.add(encoded.substring(0, hash));
                variants.put(encoded.substring(0, hash), encoded.substring(hash + 1));
            }
        }
        EXPRESSED.put(entityId, ids);
        VARIANTS.put(entityId, variants);
    }

    /** The gene ids the entity expresses, or an empty set if not yet synced. */
    public static Set<String> expressedGenes(int entityId) {
        return EXPRESSED.getOrDefault(entityId, Set.of());
    }

    /**
     * The gene ids the entity expresses, preferring the live per-entity sync but falling back to the
     * encodings persisted in the entity's own snapshot when nothing is synced for its id (a
     * CarryOn-reconstructed villager), so its real attachments and hidden features still render.
     */
    public static Set<String> expressedGenes(LivingEntity entity) {
        if (entity == null) return Set.of();
        Set<String> synced = EXPRESSED.get(entity.getId());
        if (synced != null) return synced;
        if (entity instanceof VillagerEntityMCA villager) {
            return geneIdsOf(TownsteadVillagerState.snapshotExpressedAlleles(villager));
        }
        return Set.of();
    }

    private static Set<String> geneIdsOf(List<String> encodings) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        for (String encoded : encodings) {
            if (encoded == null || encoded.isEmpty() || encoded.equals("~")) continue;
            int hash = encoded.indexOf('#');
            ids.add(hash < 0 ? encoded : encoded.substring(0, hash));
        }
        return ids;
    }

    /** The entity's rolled variant id per variant-gene id, or an empty map if not yet synced. */
    public static Map<String, String> carriedVariants(int entityId) {
        return VARIANTS.getOrDefault(entityId, Map.of());
    }

    /** Override one carried variant client-side (the editor's live preview before the server commits). */
    public static void setCarriedVariant(int entityId, String geneId, String variantId) {
        VARIANTS.computeIfAbsent(entityId, k -> new ConcurrentHashMap<>()).put(geneId, variantId);
    }

    /** Whether the entity is known to express the given gene id. */
    public static boolean expresses(int entityId, String geneId) {
        return EXPRESSED.getOrDefault(entityId, Set.of()).contains(geneId);
    }

    /** Store the gene ids whose toggle-mode ability is currently ON for the entity. */
    public static void setToggles(int entityId, List<String> geneIds) {
        Set<String> set = ConcurrentHashMap.newKeySet();
        set.addAll(geneIds);
        TOGGLES.put(entityId, set);
    }

    /** Whether the entity's toggle-mode ability gene is currently ON. */
    public static boolean isToggled(int entityId, String geneId) {
        Set<String> set = TOGGLES.get(entityId);
        return set != null && set.contains(geneId);
    }

    /** Drop a single entry; used to evict an editor's throwaway dummy when its screen closes. */
    public static void remove(int entityId) {
        BY_ENTITY.remove(entityId);
        EXPRESSED.remove(entityId);
        TOGGLES.remove(entityId);
    }

    public static void clear() {
        BY_ENTITY.clear();
        EXPRESSED.clear();
        TOGGLES.clear();
    }
}
