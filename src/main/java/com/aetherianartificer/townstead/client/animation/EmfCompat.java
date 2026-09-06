package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.emote.EmotePlaybackRegistry;
import com.aetherianartificer.townstead.client.animation.nativeclip.NativePlaybackRegistry;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * EMF (Entity Model Features) cooperation for emote bend. EMF's custom model cubes
 * override {@code ModelPart.Cube.compile} without calling super, which silently
 * bypasses bendy-lib's mesh-deformation mixin — limb bend can never render on
 * EMF-built geometry (upstream: KosmX/emotes#478). EMF 3.0.9+ exposes
 * {@code EMFAnimationApi.registerVanillaModelCondition}: while the condition holds
 * for an entity, EMF renders the entity's true vanilla model — real vanilla cubes,
 * the same instances playerAnim registered bend mutators on — then hands back to
 * the CEM model afterwards. Registering "has an active Townstead-tracked emote"
 * restores bend for exactly the emote's duration. The same arbitration is required
 * for Townstead-native performances: otherwise EMF re-poses the model after MCA's
 * {@code setupAnim} and silently erases every native clip transform. EmotecraftEventBridge
 * mirrors player emotes into EmotePlaybackRegistry, so this covers players and villagers
 * alike. Reflective: EMF is an optional runtime neighbor, never a compile dep.
 */
public final class EmfCompat {
    private static boolean registered;

    private EmfCompat() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            Class<?> api = Class.forName("traben.entity_model_features.EMFAnimationApi");
            Method register = api.getMethod("registerVanillaModelCondition", Function.class);
            Function<Object, Boolean> townsteadOwnsPose = emfEntity -> {
                if (!(emfEntity instanceof LivingEntity living)) return false;
                return EmotePlaybackRegistry.get(living.getUUID()) != null
                        || NativePlaybackRegistry.hasActive(living.getId(), living.level().getGameTime());
            };
            register.invoke(null, townsteadOwnsPose);
            Townstead.LOGGER.info("[EmfCompat] registered EMF vanilla-model condition for active Townstead performances");
        } catch (ClassNotFoundException ignored) {
            // EMF not installed; nothing to cooperate with.
        } catch (ReflectiveOperationException e) {
            Townstead.LOGGER.warn("[EmfCompat] could not register EMF vanilla-model condition: {}", e.toString());
        }
    }
}
