package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.TownsteadConfig;

/** Defensive config reads: startup and test environments may not have loaded client TOML yet. */
final class ResourceHudConfig {
    private ResourceHudConfig() {}

    static TownsteadConfig.ResourceHudAnchor anchor() {
        try { return TownsteadConfig.RESOURCE_HUD_ANCHOR.get(); }
        catch (Exception ignored) { return TownsteadConfig.ResourceHudAnchor.PACK_DECIDED; }
    }

    static TownsteadConfig.ResourceHudVisibility visibility() {
        try { return TownsteadConfig.RESOURCE_HUD_VISIBILITY.get(); }
        catch (Exception ignored) { return TownsteadConfig.ResourceHudVisibility.CONTEXTUAL; }
    }

    static TownsteadConfig.ResourceHudStack stack() {
        try { return TownsteadConfig.RESOURCE_HUD_STACK.get(); }
        catch (Exception ignored) { return TownsteadConfig.ResourceHudStack.DOWN; }
    }

    static TownsteadConfig.ResourceHudExitStyle exitStyle() {
        try { return TownsteadConfig.RESOURCE_HUD_EXIT_STYLE.get(); }
        catch (Exception ignored) { return TownsteadConfig.ResourceHudExitStyle.FADE; }
    }

    static int offsetX() { try { return TownsteadConfig.RESOURCE_HUD_OFFSET_X.get(); } catch (Exception ignored) { return 4; } }
    static int offsetY() { try { return TownsteadConfig.RESOURCE_HUD_OFFSET_Y.get(); } catch (Exception ignored) { return 4; } }
    static float scale() { try { return TownsteadConfig.RESOURCE_HUD_SCALE.get().floatValue(); } catch (Exception ignored) { return 1f; } }
    static int holdTicks() { try { return TownsteadConfig.RESOURCE_HUD_HOLD_TICKS.get(); } catch (Exception ignored) { return 60; } }
    static int fadeTicks() { try { return TownsteadConfig.RESOURCE_HUD_FADE_TICKS.get(); } catch (Exception ignored) { return 10; } }
    static boolean showValues() { try { return TownsteadConfig.RESOURCE_HUD_SHOW_VALUES.get(); } catch (Exception ignored) { return true; } }
}
