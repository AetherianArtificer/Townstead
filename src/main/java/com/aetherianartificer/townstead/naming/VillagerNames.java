package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.Cultures;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Composes a villager's name from every source that has something to say, and hands the result to
 * everything that shows it.
 *
 * <p>Names are assembled from parts rather than written as one string, and the assembly is open at
 * both ends. A mod that knows a villager's rank registers a {@link NamePartSource}; a mod that
 * draws names registers a {@link NameTarget}. Neither has to know the other exists, and Townstead
 * does not have to special-case which mods are installed.</p>
 *
 * <p>MCA Capitals ends up on both sides, which is the clearest illustration of why this is a wrapper
 * and not a special case: its surname and court title are a source, and its identity is a target, so
 * a Townstead culture's patronymic reaches the nameplate Capitals already draws instead of Townstead
 * drawing a second one over it.</p>
 */
public final class VillagerNames {

    private static final List<NamePartSource> SOURCES = new CopyOnWriteArrayList<>();
    private static final List<NameTarget> TARGETS = new CopyOnWriteArrayList<>();

    private VillagerNames() {}

    /** Registers a contributor. Later registration wins, part by part. */
    public static void addSource(NamePartSource source) {
        if (source != null) SOURCES.add(source);
    }

    /** Registers somewhere the composed name should reach. */
    public static void addTarget(NameTarget target) {
        if (target != null) TARGETS.add(target);
    }

    /**
     * This villager's name in pieces. The given name starts as whatever MCA already calls them, so
     * a villager always has a name even with nothing else loaded, and sources refine it from there.
     */
    public static NameParts parts(VillagerEntityMCA villager) {
        NameParts.Builder builder = NameParts.builder(baseName(villager));

        NamingTradition tradition = Naming.traditionOf(villager);
        if (tradition != null) builder.order(tradition.order());

        for (NamePartSource source : SOURCES) {
            try {
                source.contribute(villager, builder);
            } catch (Throwable ignored) {
                // One source's silence is a missing part, never a villager without a name.
            }
        }
        return builder.build();
    }

    /** The composed name, ready to draw. */
    public static Component display(VillagerEntityMCA villager) {
        return Component.literal(parts(villager).fullName());
    }

    /**
     * Composes the name and hands it to every target. Idempotent, so it is safe to run whenever a
     * villager is named, loaded, or changes in a way that could move a part.
     */
    public static void publish(VillagerEntityMCA villager) {
        // Composed unconditionally, even with nothing currently displaying it. A villager's name is
        // part of who they are, not a render detail: Chronicles, dialogue and the editor all read it
        // later, and skipping the work when no target happens to be registered would mean a world
        // without the right mod installed quietly had no family names at all.
        NameParts parts = parts(villager);
        for (NameTarget target : TARGETS) {
            try {
                target.accept(villager, parts);
            } catch (Throwable ignored) {
                // A target that cannot take the name is one screen out of date, not a broken name.
            }
        }
    }

    /**
     * A server-built screen title using the same surname ownership and ordering as client names.
     * The existing display component is retained when no name decoration is needed.
     */
    public static Component titleFor(VillagerEntityMCA villager) {
        Component display = villager.getDisplayName();
        NameParts parts = parts(villager);
        String full = NameParts.format(display.getString(), parts.family(), parts.order());
        return full.equals(display.getString()) ? display : Component.literal(full);
    }

    /** The culture whose naming this villager follows, or null. */
    public static Culture cultureOf(VillagerEntityMCA villager) {
        return Cultures.get(Naming.cultureOf(villager));
    }

    /**
     * MCA's own name for this villager, which is the given name everything else builds on. Read
     * from the entity rather than reconstructed from anything already rendered, because a rendered
     * name may already carry a title or a surname another mod put there.
     */
    private static String baseName(VillagerEntityMCA villager) {
        try {
            String name = villager.getName().getString();
            return name == null ? "" : name.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
