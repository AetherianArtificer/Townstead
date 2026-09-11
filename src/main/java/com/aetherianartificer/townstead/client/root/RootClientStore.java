package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.RootSetC2SPayload;
import com.aetherianartificer.townstead.villager.TownsteadVillagerState;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side current-origin cache, keyed by network entity id (or
 * {@link RootSetC2SPayload#SELF} for the player's own origin). Fed by
 * {@code RootSyncS2CPayload}; read live by the picker to highlight the current
 * row and by the skin-tint layer. Also caches each entity's expressed gene ids
 * (fed by {@code ExpressedGenesS2CPayload}) so render layers can paint that
 * individual's genetics. Cleared on logout (see {@code Townstead}).
 */
public final class RootClientStore {

    private static final Map<Integer, String> BY_ENTITY = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<String>> EXPRESSED = new ConcurrentHashMap<>();
    private static final Map<Integer, Map<String, String>> VARIANTS = new ConcurrentHashMap<>();
    private static final Map<Integer, Set<String>> TOGGLES = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> HAIR = new ConcurrentHashMap<>();
    private static final Map<Integer, List<com.aetherianartificer.townstead.root.appearance.HairColorRange>> HAIR_COLORS = new ConcurrentHashMap<>();
    private static final Map<Integer, List<com.aetherianartificer.townstead.root.appearance.HairColorChoice>> HAIR_PALETTES = new ConcurrentHashMap<>();
    private static final Map<Integer, List<com.aetherianartificer.townstead.root.appearance.HairGradient>> HAIR_GRADIENTS = new ConcurrentHashMap<>();

    private RootClientStore() {}

    public static void set(int entityId, String rootId) {
        String normalized = rootId == null ? "" : rootId;
        String previous = BY_ENTITY.put(entityId, normalized);
        // The client resolves an entity's dimensions ONCE, at spawn. If the root had not synced yet the rig
        // hitbox resolved null and the entity cached MCA's default box -- so its name tag floats and its
        // interaction box is wrong even though the model (which re-resolves every frame) renders correctly.
        // When the root first arrives (or changes) re-run refreshDimensions so the rig's declared hitbox
        // finally takes effect. Deferred to the client thread so it is safe to call from the sync handler.
        if (!normalized.isEmpty() && !normalized.equals(previous)) {
            refreshEntityDimensions(entityId);
        }
    }

    private static void refreshEntityDimensions(int entityId) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.level == null) return;
            net.minecraft.world.entity.Entity e = mc.level.getEntity(entityId);
            if (e != null) e.refreshDimensions();
        });
    }

    /** Current origin id for the target, or empty string if unknown. */
    public static String get(int entityId) {
        return BY_ENTITY.getOrDefault(entityId, "");
    }

    public static String getSelf() {
        return get(RootSetC2SPayload.SELF);
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
        if (entity instanceof VillagerEntityMCA villager) return TownsteadVillagerState.snapshotRootId(villager);
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
    public static void setExpressed(int entityId, List<String> alleleEncodings, boolean hair,
            List<com.aetherianartificer.townstead.root.appearance.HairColorRange> hairColors,
            List<com.aetherianartificer.townstead.root.appearance.HairColorChoice> hairPalette,
            List<com.aetherianartificer.townstead.root.appearance.HairGradient> hairGradients) {
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
        HAIR.put(entityId, hair);
        HAIR_COLORS.put(entityId, List.copyOf(hairColors));
        HAIR_PALETTES.put(entityId, List.copyOf(hairPalette));
        HAIR_GRADIENTS.put(entityId, List.copyOf(hairGradients));
    }

    /** Whether MCA's native hair layer is enabled for this individual (true while unsynced). */
    public static boolean usesHair(LivingEntity entity) {
        return entity == null || HAIR.getOrDefault(entity.getId(), true);
    }

    /** Set a preview dummy's effective root policy before its realized per-entity sync exists. */
    public static void setHair(int entityId, boolean enabled,
            List<com.aetherianartificer.townstead.root.appearance.HairColorRange> colors,
            List<com.aetherianartificer.townstead.root.appearance.HairColorChoice> palette,
            List<com.aetherianartificer.townstead.root.appearance.HairGradient> gradients) {
        HAIR.put(entityId, enabled);
        HAIR_COLORS.put(entityId, List.copyOf(colors));
        HAIR_PALETTES.put(entityId, List.copyOf(palette));
        HAIR_GRADIENTS.put(entityId, List.copyOf(gradients));
    }

    public static com.aetherianartificer.townstead.root.appearance.HairSettings hairSettings(LivingEntity entity) {
        if (entity == null) return com.aetherianartificer.townstead.root.appearance.HairSettings.DEFAULT;
        return new com.aetherianartificer.townstead.root.appearance.HairSettings(
                HAIR.getOrDefault(entity.getId(), true),
                HAIR_COLORS.getOrDefault(entity.getId(), List.of()),
                HAIR_PALETTES.getOrDefault(entity.getId(), List.of()),
                HAIR_GRADIENTS.getOrDefault(entity.getId(), List.of()));
    }

    /**
     * Whether the entity expresses its species genes at all (renders its rig + runs gene effects):
     * always for a villager/mob, but for a player only in MCA's full-genetics "Villager" model mode
     * ({@code useVillagerRenderer}). In the "Player"/"Vanilla" model modes the player is a plain player
     * and its genes are inheritance data only (they decide what its children inherit), so no gene effect
     * (rig, abilities, buoyancy, needs, attachments, hidden features...) applies. The single client gate;
     * {@code RigModels.embodied} delegates here so render and gene expression never disagree.
     */
    public static boolean expresses(LivingEntity entity) {
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            return net.conczin.mca.MCAClient.useVillagerRenderer(player.getUUID());
        }
        return true;
    }

    /** The gene ids the entity expresses, or an empty set if not yet synced (or it does not express). */
    public static Set<String> expressedGenes(int entityId) {
        if (!expressesById(entityId)) return Set.of();
        return EXPRESSED.getOrDefault(entityId, Set.of());
    }

    /** Resolve an entity id to gate {@link #expressedGenes(int)} the same way the entity overload does. */
    private static boolean expressesById(int entityId) {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return true;
        net.minecraft.world.entity.Entity entity = level.getEntity(entityId);
        return !(entity instanceof LivingEntity living) || expresses(living);
    }

    /**
     * The gene ids the entity expresses, preferring the live per-entity sync but falling back to the
     * encodings persisted in the entity's own snapshot when nothing is synced for its id (a
     * CarryOn-reconstructed villager), so its real attachments and hidden features still render.
     */
    public static Set<String> expressedGenes(LivingEntity entity) {
        if (entity == null || !expresses(entity)) return Set.of();
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

    /**
     * Mirror a source entity's expressed-gene set onto a client-local dummy id (the villager
     * editor's preview entity), resolved through the same synced-then-snapshot path the world
     * render uses — so the preview wears THIS individual's genetics rather than the
     * origin-typical kit the empty-set fallback shows. An empty resolution clears the mirror
     * instead, leaving the fallback in place (a never-synced legacy villager).
     */
    public static void mirrorExpressed(LivingEntity source, int targetId) {
        if (source == null || source.getId() == targetId) return;
        Set<String> ids = expressedGenes(source);
        if (ids.isEmpty()) {
            EXPRESSED.remove(targetId);
            return;
        }
        Set<String> copy = ConcurrentHashMap.newKeySet();
        copy.addAll(ids);
        EXPRESSED.put(targetId, copy);
    }

    /** Drop an id's mirrored expressed set, so a browsed root previews its typical kit again. */
    public static void clearExpressed(int entityId) {
        EXPRESSED.remove(entityId);
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
        HAIR.remove(entityId);
        HAIR_COLORS.remove(entityId);
        HAIR_PALETTES.remove(entityId);
        HAIR_GRADIENTS.remove(entityId);
    }

    public static void clear() {
        BY_ENTITY.clear();
        EXPRESSED.clear();
        TOGGLES.clear();
        HAIR.clear();
        HAIR_COLORS.clear();
        HAIR_PALETTES.clear();
        HAIR_GRADIENTS.clear();
    }
}
