package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;

/** Reloadable interpretations; multiple descriptors may describe the same relationship. */
public final class RelationshipDescriptors {
    private static volatile List<RelationshipDescriptor> entries = List.of();
    private RelationshipDescriptors() {}
    public static void replaceAll(Collection<RelationshipDescriptor> next) {
        entries = next.stream().sorted(Comparator.comparingInt(RelationshipDescriptor::priority).reversed()
                .thenComparing(value -> value.id().toString())).toList();
    }
    public static List<RelationshipDescriptor> all() { return entries; }
    public static List<RelationshipDescriptor> matching(ConditionContext context) {
        return entries.stream().filter(value -> value.matches(context)).toList();
    }
    public static Set<String> tags(LivingEntity actor, LivingEntity other) {
        Set<String> tags = new LinkedHashSet<>();
        matching(new ConditionContext(actor, other)).forEach(value -> {
            tags.add(value.id().toString()); tags.addAll(value.tags());
        });
        return Set.copyOf(tags);
    }
}
