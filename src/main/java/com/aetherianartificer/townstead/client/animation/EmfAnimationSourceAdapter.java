package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.client.animation.cem.CemAnimationProgram;
import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Source adapter for EMF (Entity Model Features) resource packs.
 *
 * <p>EMF accepts CEM ({@code .jem}) files at two locations within a pack: the
 * modern {@code emf/cem/} path (used by Fresh Animations Player Extension) and
 * the legacy {@code optifine/cem/} path (used by Fresh Moves and Fresh
 * Animations itself). Resolution walks the resource pack stack from top to
 * bottom; within each pack the modern path is preferred, but a higher-priority
 * pack always wins regardless of which path it uses, matching EMF's own
 * resolution order and the player's drag-to-reorder mental model.</p>
 *
 * <p>Currently scoped to the player CEM only ({@code player.jem}); non-player
 * CEM files, slim/baby variants, and {@code .properties} gating are not yet
 * handled.</p>
 */
public final class EmfAnimationSourceAdapter implements AnimationSourceAdapter {
    private static final String ID = "emf";
    /** Used when an entity has no authored provider chain (normal players/villagers): the player CEM. */
    private static final List<String> DEFAULT_CHAIN = List.of("minecraft:player");

    // CEM programs cached per resolved file, since the resolved identity now varies per entity.
    private final Map<ResourceLocation, Optional<CemAnimationProgram>> programs = new HashMap<>();
    // Resolved CEM file per identity, so the pack-stack walk runs once per identity, not per frame.
    private final Map<String, Optional<ResourceLocation>> identities = new HashMap<>();

    /** Drop cached CEM programs and resolutions so the next render re-resolves and reloads. */
    public void invalidate() {
        programs.clear();
        identities.clear();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        // Per-entity resolution happens in collectTransforms; here we only gate on EMF being present.
        return isEmfLoaded();
    }

    @Override
    public List<AnimationTransform> collectTransforms(AnimationSourceContext context) {
        Optional<CemAnimationProgram> activeProgram = program(context);
        return activeProgram.map(cemAnimationProgram -> cemAnimationProgram.evaluate(context)).orElseGet(List::of);
    }

    /** Resolve the CEM file for this entity by walking its provider chain (fall-through), cached. */
    private ResourceLocation resolveCem(LivingEntity entity) {
        List<String> chain = RigModels.animations(entity).providers();
        if (chain.isEmpty()) chain = DEFAULT_CHAIN;
        for (String identity : chain) {
            if (identity.equalsIgnoreCase("humanoid")) return null;  // the base; no CEM, our setupAnim is the floor
            Optional<ResourceLocation> cem =
                    identities.computeIfAbsent(identity, id -> Optional.ofNullable(resolveCemForIdentity(id)));
            if (cem.isPresent()) return cem.get();
        }
        return null;
    }

    /** First existing emf/optifine CEM file for one identity, top pack wins; null if none loaded. */
    private static ResourceLocation resolveCemForIdentity(String identity) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getResourceManager() == null) return null;
        String path = identity.contains(":") ? identity.substring(identity.indexOf(':') + 1) : identity;
        List<ResourceLocation> candidates = List.of(
                DataPackLang.parseId("minecraft:emf/cem/" + path + ".jem"),
                DataPackLang.parseId("minecraft:optifine/cem/" + path + ".jem"));
        // listPacks() is load order (topmost last); walk in reverse so a higher pack wins.
        List<PackResources> packs = client.getResourceManager().listPacks().toList();
        for (int i = packs.size() - 1; i >= 0; i--) {
            PackResources pack = packs.get(i);
            for (ResourceLocation candidate : candidates) {
                if (candidate != null && pack.getResource(PackType.CLIENT_RESOURCES, candidate) != null) return candidate;
            }
        }
        return null;
    }

    private static boolean hasEmfAnimationApi() {
        try {
            Class.forName("traben.entity_model_features.EMFAnimationApi");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean isEmfLoaded() {
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            Method get = modListClass.getMethod("get");
            Object modList = get.invoke(null);
            Method isLoaded = modListClass.getMethod("isLoaded", String.class);
            return Boolean.TRUE.equals(isLoaded.invoke(modList, "entity_model_features"));
        } catch (ReflectiveOperationException ignored) {
            return hasEmfAnimationApi();
        }
    }

    private Optional<CemAnimationProgram> program(AnimationSourceContext context) {
        ResourceLocation loc = resolveCem(context.entity());
        return loc == null ? Optional.empty() : programs.computeIfAbsent(loc, CemAnimationProgram::load);
    }
}
