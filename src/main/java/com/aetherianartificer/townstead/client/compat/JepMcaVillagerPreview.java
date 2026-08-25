package com.aetherianartificer.townstead.client.compat;

import com.aetherianartificer.townstead.profession.ClientProfessionClothing;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.IdentityHashMap;
import java.util.Map;

/** Supplies JEP with an MCA-initialized, client-only villager preview. */
public final class JepMcaVillagerPreview {

    // MCA's own PreviewEntityAnimation uses a 20 Hz wall clock rather than world time. Inventory
    // screens pause an integrated server, so level.getGameTime() can otherwise freeze a mannequin
    // on the two ticks for which MCA draws its closed-eye texture.
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final int MCA_PREVIEW_TICK_WRAP = 27_720;

    private static final Map<VillagerProfession, VillagerEntityMCA> CACHE = new IdentityHashMap<>();
    private static ClientLevel cachedLevel;

    private JepMcaVillagerPreview() {}

    public static Villager get(VillagerProfession requestedProfession) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || requestedProfession == null) return null;

        if (cachedLevel != level) {
            clear();
            cachedLevel = level;
        }

        VillagerEntityMCA preview = CACHE.computeIfAbsent(requestedProfession,
                profession -> create(level, profession));
        if (preview != null) {
            // JEP may build its recipe cache before a world data reload has populated Townstead's
            // Profession definitions and MCA's resource-pack clothing catalogue. This is stable:
            // The client resolver leaves an already-valid outfit alone, but can resolve it once
            // both catalogues become available (or after a resource reload changes the preference).
            ClientProfessionClothing.ensureRenderable(preview);
            // JEP does not add its mannequin to the level, so it never ticks. Match MCA's editor
            // preview clock exactly: it continues while the screen has paused the world.
            preview.tickCount = (int) ((System.nanoTime() / NANOS_PER_TICK) % MCA_PREVIEW_TICK_WRAP);
        }
        return preview;
    }

    private static VillagerEntityMCA create(ClientLevel level, VillagerProfession profession) {
        VillagerEntityMCA villager = Gender.getRandom().binary().getVillagerType().create(level);
        if (villager == null) return null;

        // The preview is not a world entity. Keep its ID far outside the server's entity range so
        // client stores keyed by entity ID cannot lend it another villager's Root, eyes, rig, or
        // animation state.
        villager.setId(-2_000_000_000 + CACHE.size());

        // Set the Profession before MCA initializes its presentation so its native wardrobe pool
        // (rather than the generic civilian pool) is used on the first and only randomization.
        villager.setVillagerData(villager.getVillagerData().setProfession(profession));
        initializePresentation(villager);
        return villager;
    }

    /**
     * Initializes only renderable MCA state. The full spawn initializer also creates a citizen
     * name and family context, and MCA correctly expects a ServerLevel for those operations;
     * JEP's preview exists solely in a ClientLevel and must not manufacture world identity.
     */
    private static void initializePresentation(VillagerEntityMCA villager) {
        villager.getGenetics().randomize();
        villager.getTraits().randomize();
        villager.setAgeState(AgeState.byCurrentAge(villager.getAge()));
        villager.initializeSkin(false);
        villager.validateClothes();
        villager.refreshDimensions();
    }

    public static void clear() {
        CACHE.clear();
        cachedLevel = null;
    }
}
