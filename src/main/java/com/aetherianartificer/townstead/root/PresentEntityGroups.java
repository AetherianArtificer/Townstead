package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.Gene;
import com.aetherianartificer.townstead.root.gene.GeneRegistry;
import com.aetherianartificer.townstead.root.gene.GeneVariant;
import com.aetherianartificer.townstead.root.gene.types.EntityGroupGeneType;
import com.aetherianartificer.townstead.root.gene.types.EntityGroupGeneType.Group;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which creature groups the loaded content can express at all: a group counts as present when any
 * loaded gene grants it. Lets content that only makes sense for one kind of folk (the spider-folk
 * scarf) stay out of the way on installs without that root pack. Read from the gene registry where
 * it exists (the server, singleplayer) and from the synced root catalog on a client of a dedicated
 * server, whose registries stay empty.
 */
public final class PresentEntityGroups {

    private static volatile Set<Group> SYNCED = Set.of();

    private PresentEntityGroups() {}

    /** Whether any loaded gene, here or on the server we are connected to, expresses this group. */
    public static boolean present(Group group) {
        return SYNCED.contains(group) || fromRegistries().contains(group);
    }

    /** Groups granted by the loaded gene registry, as lower-case keys for the catalog sync. */
    public static List<String> keysFromRegistries() {
        List<String> keys = new ArrayList<>();
        for (Group group : fromRegistries()) keys.add(group.name().toLowerCase(Locale.ROOT));
        return keys;
    }

    /** Client side: remember what the server's content can express. */
    public static void setSynced(Collection<String> keys) {
        Set<Group> groups = EnumSet.noneOf(Group.class);
        for (String key : keys) {
            Group group = Group.byKey(key);
            if (group != Group.DEFAULT) groups.add(group);
        }
        SYNCED = Set.copyOf(groups);
    }

    private static Set<Group> fromRegistries() {
        Set<Group> groups = EnumSet.noneOf(Group.class);
        for (Gene gene : GeneRegistry.all()) {
            for (GeneVariant variant : gene.variants()) {
                if (variant.instance() instanceof EntityGroupGeneType.Instance instance) groups.add(instance.group());
            }
        }
        return groups;
    }
}
