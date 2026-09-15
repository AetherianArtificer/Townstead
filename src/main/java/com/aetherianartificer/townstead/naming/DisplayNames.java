package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.TownsteadConfig;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;

/**
 * The one place a villager's name is composed for showing.
 *
 * <p>Every surface that draws a name reaches {@code getDisplayName}, so composing there is what
 * makes a surname appear on screens Townstead does not own, including MCA's own and MCA Capitals'.
 * {@code getName} is deliberately left alone: that is the villager's identity, and the editor, the
 * family tree and a patronymic's stem all need the given name by itself.</p>
 *
 * <p>The two sides hold the name in different places. On the server it is villager state; on the
 * client it is whatever the sync delivered. Asking the wrong one would give a screen the empty
 * answer and look exactly like the feature being broken.</p>
 */
public final class DisplayNames {

    /** Composition reads a name, so a source that reaches back here must not start again. */
    private static final ThreadLocal<Boolean> COMPOSING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private DisplayNames() {}

    /** The composed name for a villager, given whatever the caller would otherwise have drawn. */
    public static Component of(VillagerEntityMCA villager, Component raw) {
        String composed = apply(villager, raw == null ? "" : raw.getString(), style());
        if (composed.isEmpty() || raw == null || composed.equals(raw.getString())) return raw;
        return Component.literal(composed);
    }

    /** The composed name in a named style, for a surface that wants something other than the default. */
    public static String apply(VillagerEntityMCA villager, String given, NameStyle style) {
        if (villager == null || style == null || COMPOSING.get()) return given;
        COMPOSING.set(Boolean.TRUE);
        try {
            return villager.level().isClientSide
                    ? NameClientStore.styled(villager.getId(), given, style)
                    : serverSide(villager, given, style);
        } catch (Throwable ignored) {
            // A villager whose name cannot be composed keeps the one the caller already had.
            return given;
        } finally {
            COMPOSING.set(Boolean.FALSE);
        }
    }

    private static String serverSide(VillagerEntityMCA villager, String given, NameStyle style) {
        NameParts parts = VillagerNames.parts(villager);
        return style.apply(given, parts.family(), parts.order());
    }

    /** The configured default, which every surface uses unless it asks for something else. */
    public static NameStyle style() {
        try {
            return TownsteadConfig.NAME_STYLE.get();
        } catch (Throwable ignored) {
            // Config not loaded yet: showing the whole name is the point of having one.
            return NameStyle.FULL;
        }
    }
}
