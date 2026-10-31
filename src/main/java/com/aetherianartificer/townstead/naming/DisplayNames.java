package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.switchboard.Switchboard;
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

    /**
     * The composed name for a zombie villager who was someone, in the zombie's own style. A zombie
     * Townstead never knew keeps its name as is.
     */
    public static Component ofZombie(net.minecraft.world.entity.Mob zombie, Component raw) {
        if (raw == null || COMPOSING.get()) return raw;
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.NAMING)) return raw;
        String given = raw.getString();
        String composed;
        if (zombie.level().isClientSide) {
            composed = NameClientStore.styled(zombie.getId(), given, style());
        } else {
            VillagerEntityMCA person = com.aetherianartificer.townstead.villager.ZombifiedVillagers.person(zombie);
            composed = person == null ? given : apply(person, given, style());
        }
        if (composed.isEmpty() || composed.equals(given)) return raw;
        return Component.literal(composed).withStyle(raw.getStyle());
    }

    /** The composed name in a named style, for a surface that wants something other than the default. */
    public static String apply(VillagerEntityMCA villager, String given, NameStyle style) {
        if (villager == null || style == null || COMPOSING.get()) return given;
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.NAMING)) return given;
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
        // Server text has no one reader, so a Persona who keeps their family name back is only
        // their given name in it.
        if (VillagerNames.familyHidden(villager, null)) return NameParts.withoutFamily(given, parts.family(), parts.order());
        return style.apply(given, parts.family(), parts.order());
    }

    /** The configured default, which every surface uses unless it asks for something else. */
    public static NameStyle style() {
        try {
            return Switchboard.get(TownsteadConfig.NAME_STYLE);
        } catch (Throwable ignored) {
            // Config not loaded yet: showing the whole name is the point of having one.
            return NameStyle.FULL;
        }
    }
}
