package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.cem.CemAnimationProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Source adapter for EMF (Entity Model Features) resource packs.
 *
 * <p>Currently scoped to Fresh Animations Player Extension's player CEM at
 * {@code minecraft:emf/cem/player.jem}; broader pack-format coverage (the
 * legacy {@code optifine/cem/} path, non-player CEM files, slim/baby variants,
 * {@code .properties} gating) is intentionally not yet handled.</p>
 */
public final class EmfAnimationSourceAdapter implements AnimationSourceAdapter {
    private static final String ID = "emf";
    private static final ResourceLocation FRESH_PLAYER_CEM =
            ResourceLocation.fromNamespaceAndPath("minecraft", "emf/cem/player.jem");

    private boolean logged;
    private boolean loadAttempted;
    private Optional<CemAnimationProgram> program = Optional.empty();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        return isEmfLoaded() && hasFreshPlayerResources();
    }

    @Override
    public List<AnimationTransform> collectTransforms(AnimationSourceContext context) {
        logDiagnostics(context);
        Optional<CemAnimationProgram> activeProgram = program();
        return activeProgram.map(cemAnimationProgram -> cemAnimationProgram.evaluate(context)).orElseGet(List::of);
    }

    private void logDiagnostics(AnimationSourceContext context) {
        if (logged) return;
        logged = true;

        boolean emfLoaded = isEmfLoaded();
        boolean freshResources = hasFreshPlayerResources();
        boolean apiPresent = hasEmfAnimationApi();
        boolean modelHasEmfRoot = modelHasEmfRoot(context.model());
        boolean emfRootHasAnimation = modelHasEmfRoot && emfRootHasAnimation(context.model());
        boolean focusedEvaluatorAvailable = program().isPresent();
        boolean evaluatedVectorAccess = modelHasEmfRoot && emfRootHasAnimation;

        Townstead.LOGGER.info(
                "[AnimationBridge] source={} emfLoaded={} freshPlayerResources={} emfApiPresent={} modelHasEmfRoot={} emfRootHasAnimation={} focusedEvaluatorAvailable={} evaluatedVectorAccess={} action={}",
                ID,
                emfLoaded,
                freshResources,
                apiPresent,
                modelHasEmfRoot,
                emfRootHasAnimation,
                focusedEvaluatorAvailable,
                evaluatedVectorAccess,
                focusedEvaluatorAvailable ? "apply_focused_evaluator" : "skip");
    }

    private static boolean hasFreshPlayerResources() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getResourceManager() == null) return false;
        return client.getResourceManager().getResource(FRESH_PLAYER_CEM).isPresent();
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

    private Optional<CemAnimationProgram> program() {
        if (!loadAttempted) {
            loadAttempted = true;
            program = CemAnimationProgram.loadFreshPlayerProgram();
        }
        return program;
    }
}
