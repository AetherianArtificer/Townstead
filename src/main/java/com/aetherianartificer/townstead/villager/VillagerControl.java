package com.aetherianartificer.townstead.villager;

import com.aetherianartificer.townstead.TownsteadConfig;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.jetbrains.annotations.Nullable;

/**
 * Who may move a villager when several systems want to, highest first. It holds no state of its
 * own: each owner is read from the system that runs it, so it cannot disagree with them. Combat
 * stays outside the list.
 *
 * <p>A lasting owner ends whatever ranks below it. A brief one only pauses it, so an emote or a
 * word with a seated villager does not end their visit.</p>
 */
public final class VillagerControl {
    public enum Owner {
        DOWNED(false), COLLAPSED(false), REACTION(true), RITUAL(false), STORY(true), REST(false),
        LEAVING(false), COMPANION(false), PERSONA_WALK(false), HANGOUT(false), WORK(false);

        private final boolean brief;

        Owner(boolean brief) { this.brief = brief; }

        public boolean brief() { return brief; }
    }

    private static final Owner[] ORDER = Owner.values();

    private VillagerControl() {}

    /** Whether nothing above {@code asking}, even briefly, wants this villager right now. */
    public static boolean mayMove(VillagerEntityMCA villager, Owner asking) {
        for (Owner owner : ORDER) {
            if (owner.ordinal() >= asking.ordinal()) return true;
            if (active(owner, villager)) return false;
        }
        return true;
    }

    /** The lasting owner above {@code asking} that ends it, or null. */
    public static @Nullable Owner outrankedBy(VillagerEntityMCA villager, Owner asking) {
        for (Owner owner : ORDER) {
            if (owner.ordinal() >= asking.ordinal()) return null;
            if (!owner.brief() && active(owner, villager)) return owner;
        }
        return null;
    }

    public static boolean outranked(VillagerEntityMCA villager, Owner asking) {
        return outrankedBy(villager, asking) != null;
    }

    private static boolean active(Owner owner, VillagerEntityMCA villager) {
        return switch (owner) {
            case DOWNED -> com.aetherianartificer.townstead.downed.DownedService.isDowned(villager);
            case COLLAPSED -> TownsteadConfig.isVillagerFatigueEnabled() && TownsteadVillagers.get(villager).needs().collapsed();
            case REACTION -> com.aetherianartificer.townstead.reaction.ReactionLockTracker.isLocked(villager, villager.level().getGameTime());
            case RITUAL -> com.aetherianartificer.townstead.ritual.RitualService.busy(villager);
            case STORY -> com.aetherianartificer.townstead.story.StoryService.inScene(villager);
            case REST -> TownsteadConfig.isVillagerFatigueEnabled() && TownsteadVillagers.get(villager).needs().restOverrideActive();
            case LEAVING -> com.aetherianartificer.townstead.journey.Journeys.isLeaving(villager.getUUID());
            case COMPANION -> com.aetherianartificer.townstead.journey.Companions.leader(villager) != null;
            case PERSONA_WALK -> com.aetherianartificer.townstead.persona.PersonaService.isWalking(villager.getUUID());
            case HANGOUT -> com.aetherianartificer.townstead.hangout.HangoutEngine.visit(villager.getUUID()) != null;
            case WORK -> false;
        };
    }
}
