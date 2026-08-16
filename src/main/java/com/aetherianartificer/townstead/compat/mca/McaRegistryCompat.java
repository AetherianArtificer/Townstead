package com.aetherianartificer.townstead.compat.mca;

//? if neoforge {
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.registry.SoundsMCA;
//?} else {
/*import net.conczin.mca.ProfessionsMCA;
import net.conczin.mca.SoundsMCA;
*///?}
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.function.Supplier;

/**
 * Binary compatibility boundary for MCA's registry-holder migration.
 *
 * <p>The holder fields have carried three different types across supported MCA builds: the bare
 * value, Architectury's {@code RegistrySupplier}, and MCA's own {@code RegistryRef}. Field
 * resolution matches on descriptor, so a direct reference links against exactly one of them. Both
 * wrappers implement {@link Supplier}, so one reflective read plus an unwrap serves all three.</p>
 */
public final class McaRegistryCompat {
    private static final Field GUARD = field(ProfessionsMCA.class, "GUARD");
    private static final Field ARCHER = field(ProfessionsMCA.class, "ARCHER");
    private static final Field SILENT = field(SoundsMCA.class, "SILENT");

    private static VillagerProfession guard;
    private static VillagerProfession archer;
    private static SoundEvent silent;

    private McaRegistryCompat() {}

    public static @Nullable VillagerProfession guard() {
        if (guard == null) guard = read(GUARD, VillagerProfession.class);
        return guard;
    }

    public static @Nullable VillagerProfession archer() {
        if (archer == null) archer = read(ARCHER, VillagerProfession.class);
        return archer;
    }

    /** MCA's silent sound, returned to mute MCA's own follow-up play. */
    public static @Nullable SoundEvent silent() {
        if (silent == null) silent = read(SILENT, SoundEvent.class);
        return silent;
    }

    public static boolean isGuardOrArcher(@Nullable VillagerProfession profession) {
        if (profession == null) return false;
        return profession == guard() || profession == archer();
    }

    private static <T> @Nullable T read(@Nullable Field field, Class<T> type) {
        if (field == null) return null;
        try {
            Object value = field.get(null);
            if (value instanceof Supplier<?> supplier) value = supplier.get();
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static @Nullable Field field(Class<?> owner, String name) {
        try {
            return owner.getField(name);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }
}
