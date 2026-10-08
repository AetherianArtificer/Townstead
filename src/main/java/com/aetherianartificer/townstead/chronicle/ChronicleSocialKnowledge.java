package com.aetherianartificer.townstead.chronicle;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.social.SocialKnowledge;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.UUID;

/** Belief-side reads from the existing hot tier, never from the archive or ground-truth event log. */
public final class ChronicleSocialKnowledge {
    private ChronicleSocialKnowledge() {}
    public static SocialKnowledge of(SelectorContext context) {
        if (context.subject() != null) return context.subject().socialKnowledge();
        if (context.self() == null || !(context.level() instanceof ServerLevel level)) return null;
        UUID knower = context.self().getUUID();
        ChronicleSavedData data = ChronicleSavedData.get(level.getServer());
        return new SocialKnowledge() {
            @Override public long today() { return TownsteadCalendar.worldDay(level.getServer()); }
            @Override public List<Memory> memories() {
                return data.memoriesFor(knower).stream().map(m -> new Memory(m.memoryKey(), m.otherParty(),
                        m.lastDay(), m.count(), m.strength(), m.valence())).toList();
            }
            @Override public double sentiment(UUID toward) {
                return data.relationships().hasQuality(knower, toward, com.aetherianartificer.townstead.social.RelationshipQualities.AFFECTION)
                        ? relationship(toward, com.aetherianartificer.townstead.social.RelationshipQualities.AFFECTION)
                        : data.sentiment(knower, toward);
            }
            @Override public double relationship(UUID toward, String quality) {
                return data.relationships().value(knower, toward, quality, today());
            }
            @Override public double socialInclination(String value) {
                var id=net.minecraft.resources.ResourceLocation.tryParse(value);
                var definition=id==null?null:com.aetherianartificer.townstead.social.SocialInclinations.all().get(id);
                return definition==null||!(context.self() instanceof net.conczin.mca.entity.VillagerEntityMCA villager)
                        ? Double.NaN : com.aetherianartificer.townstead.social.SocialInclinations.score(villager,definition);
            }
        };
    }
}
