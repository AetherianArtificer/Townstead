package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.cem.CemAnimationProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;

import java.lang.reflect.Method;
import java.util.List;
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
    private static final List<ResourceLocation> PLAYER_CEM_CANDIDATES = List.of(
            ResourceLocation.fromNamespaceAndPath("minecraft", "emf/cem/player.jem"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "optifine/cem/player.jem")
    );

    private boolean resolved;
    private ResourceLocation cachedLocation;
    private Optional<CemAnimationProgram> program = Optional.empty();

    /** Drop the cached CEM program so the next render re-resolves and reloads. */
    public void invalidate() {
        resolved = false;
        cachedLocation = null;
        program = Optional.empty();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        return isEmfLoaded() && resolvePlayerCem().isPresent();
    }

    @Override
    public List<AnimationTransform> collectTransforms(AnimationSourceContext context) {
        Optional<CemAnimationProgram> activeProgram = program(context);
        return activeProgram.map(cemAnimationProgram -> cemAnimationProgram.evaluate(context)).orElseGet(List::of);
    }

    private void logDiagnostics(AnimationSourceContext context) {
        boolean emfLoaded = isEmfLoaded();
        boolean apiPresent = hasEmfAnimationApi();
        boolean modelHasEmfRoot = modelHasEmfRoot(context.model());
        boolean emfRootHasAnimation = modelHasEmfRoot && emfRootHasAnimation(context.model());
        boolean focusedEvaluatorAvailable = program.isPresent();
        boolean evaluatedVectorAccess = modelHasEmfRoot && emfRootHasAnimation;

        Townstead.LOGGER.info(
                "[AnimationBridge] source={} emfLoaded={} playerCem={} emfApiPresent={} modelHasEmfRoot={} emfRootHasAnimation={} focusedEvaluatorAvailable={} evaluatedVectorAccess={} action={}",
                ID,
                emfLoaded,
                cachedLocation == null ? "none" : cachedLocation.toString(),
                apiPresent,
                modelHasEmfRoot,
                emfRootHasAnimation,
                focusedEvaluatorAvailable,
                evaluatedVectorAccess,
                focusedEvaluatorAvailable ? "apply_focused_evaluator" : "skip");
    }

    private static Optional<ResourceLocation> resolvePlayerCem() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getResourceManager() == null) return Optional.empty();
        List<PackResources> packs = client.getResourceManager().listPacks().toList();
        // listPacks() returns packs in load order; the topmost (highest-priority)
        // pack is at the end. Walk in reverse so a higher pack's optifine/cem/
        // beats a lower pack's emf/cem/.
        for (int i = packs.size() - 1; i >= 0; i--) {
            PackResources pack = packs.get(i);
            for (ResourceLocation candidate : PLAYER_CEM_CANDIDATES) {
                if (pack.getResource(PackType.CLIENT_RESOURCES, candidate) != null) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
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

    private static boolean modelHasEmfRoot(Object model) {
        try {
            Class<?> iemfModel = Class.forName("traben.entity_model_features.models.IEMFModel");
            if (!iemfModel.isInstance(model)) return false;
            Method isEmfModel = iemfModel.getMethod("emf$isEMFModel");
            Method getRoot = iemfModel.getMethod("emf$getEMFRootModel");
            return Boolean.TRUE.equals(isEmfModel.invoke(model)) && getRoot.invoke(model) != null;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean emfRootHasAnimation(Object model) {
        try {
            Class<?> iemfModel = Class.forName("traben.entity_model_features.models.IEMFModel");
            Method getRoot = iemfModel.getMethod("emf$getEMFRootModel");
            Object root = getRoot.invoke(model);
            if (root == null) return false;
            Method hasAnimation = root.getClass().getMethod("hasAnimation");
            return Boolean.TRUE.equals(hasAnimation.invoke(root));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private Optional<CemAnimationProgram> program(AnimationSourceContext context) {
        if (resolved) return program;
        resolved = true;
        cachedLocation = resolvePlayerCem().orElse(null);
        program = cachedLocation == null ? Optional.empty() : CemAnimationProgram.load(cachedLocation);
        logDiagnostics(context);
        return program;
    }
}
