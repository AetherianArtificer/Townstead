package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.client.animation.cem.CemAnimationProgram;
import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.LivingEntity;

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
    private final ZombieAnimationSourceAdapter vanillaZombie = new ZombieAnimationSourceAdapter();

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
        // Provider selection includes vanilla implementations, even when EMF is absent.
        return true; // Vanilla providers must remain available when EMF is absent.
    }

    @Override
    public List<AnimationTransform> collectTransforms(AnimationSourceContext context) {
        var entity = context.entity();
        var form = GeneAnimations.active(entity);
        List<String> chain = GeneAnimations.providerChain(form == null ? null : form.targetId(),
                RigModels.animations(entity).providers(), GeneAnimations.usesHumanoidStatePose(entity));
        boolean emfAvailable = isEmfLoaded();
        return resolveTransforms(chain, identity -> {
            if (!emfAvailable) return List.of();
            Optional<ResourceLocation> cem = identities.computeIfAbsent(identity,
                    id -> Optional.ofNullable(resolveCemForIdentity(id)));
            if (cem.isEmpty()) return List.of();
            Optional<CemAnimationProgram> program = programs.computeIfAbsent(cem.get(), CemAnimationProgram::load);
            return program.map(p -> p.evaluate(context)).orElseGet(List::of);
        }, identity -> vanillaTransforms(identity, context));
    }

    private Optional<List<AnimationTransform>> vanillaTransforms(String identity, AnimationSourceContext context) {
        return switch (identity) {
            case "minecraft:zombie", "minecraft:zombified_piglin" -> Optional.of(vanillaZombie.vanillaTransforms(context));
            // Piglin's normal body gait is the vanilla player/humanoid gait already posed by MCA.
            // The Root retains its own geometry, attachment physics, and activity handling.
            case "minecraft:piglin", "minecraft:piglin_brute", "humanoid", "minecraft:player" -> Optional.of(List.of());
            default -> Optional.empty();
        };
    }

    /** Try all authored EMF providers first, then their vanilla implementations in the same order. */
    static List<AnimationTransform> resolveTransforms(List<String> chain,
            java.util.function.Function<String, List<AnimationTransform>> evaluate,
            java.util.function.Function<String, Optional<List<AnimationTransform>>> vanilla) {
        for (String provider : chain) {
            boolean humanoid = provider.equalsIgnoreCase("humanoid");
            List<AnimationTransform> transforms = evaluate.apply(humanoid ? "minecraft:player" : provider);
            if (!transforms.isEmpty()) return transforms;
        }
        for (String provider : chain) {
            Optional<List<AnimationTransform>> fallback = vanilla.apply(provider);
            if (fallback.isPresent()) return fallback.get();
        }
        return List.of(); // Retain the built-in base; never append unauthored EMF identities.
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

    private static boolean isEmfLoaded() {
        // ModCompat selects the correct loader at build time and caches the result.
        // Probing NeoForge reflectively here threw on every rendered Forge entity.
        return ModCompat.isLoaded("entity_model_features");
    }

}
