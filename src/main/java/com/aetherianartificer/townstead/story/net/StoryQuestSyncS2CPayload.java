package com.aetherianartificer.townstead.story.net;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** The player's story quests, for the Quest Ledger. Sent whole whenever one changes. */
//? if neoforge {
public record StoryQuestSyncS2CPayload(List<Quest> quests) implements CustomPacketPayload {
//?} else {
/*public record StoryQuestSyncS2CPayload(List<Quest> quests) {
*///?}
    public record Objective(String label, long current, long total, boolean done) {}

    /** A reward line: an item with a count, or text when {@code itemId} is empty or {@code count} is 0. */
    public record Reward(String text, String itemId, int count) {}

    /**
     * {@code state}: 0 active, 1 ready to hand back, 2 complete. {@code handBack}: the teller has
     * something to say once the goals are met, so the ledger adds a line to go and talk to them.
     */
    public record Quest(String id, String title, String about, String teller, byte state, boolean handBack,
                        List<Objective> objectives, List<Reward> rewards) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(quests.size());
        for (Quest quest : quests) {
            buf.writeUtf(quest.id());
            buf.writeUtf(quest.title());
            buf.writeUtf(quest.about());
            buf.writeUtf(quest.teller());
            buf.writeByte(quest.state());
            buf.writeBoolean(quest.handBack());
            buf.writeVarInt(quest.objectives().size());
            for (Objective objective : quest.objectives()) {
                buf.writeUtf(objective.label());
                buf.writeVarLong(objective.current());
                buf.writeVarLong(objective.total());
                buf.writeBoolean(objective.done());
            }
            buf.writeVarInt(quest.rewards().size());
            for (Reward reward : quest.rewards()) {
                buf.writeUtf(reward.text());
                buf.writeUtf(reward.itemId());
                buf.writeVarInt(reward.count());
            }
        }
    }

    public static StoryQuestSyncS2CPayload read(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 1024);
        List<Quest> quests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf();
            String title = buf.readUtf();
            String about = buf.readUtf();
            String teller = buf.readUtf();
            byte state = buf.readByte();
            boolean handBack = buf.readBoolean();
            int objectiveCount = Math.min(buf.readVarInt(), 64);
            List<Objective> objectives = new ArrayList<>(objectiveCount);
            for (int j = 0; j < objectiveCount; j++) {
                objectives.add(new Objective(buf.readUtf(), buf.readVarLong(), buf.readVarLong(), buf.readBoolean()));
            }
            int rewardCount = Math.min(buf.readVarInt(), 64);
            List<Reward> rewards = new ArrayList<>(rewardCount);
            for (int j = 0; j < rewardCount; j++) rewards.add(new Reward(buf.readUtf(), buf.readUtf(), buf.readVarInt()));
            quests.add(new Quest(id, title, about, teller, state, handBack, List.copyOf(objectives), List.copyOf(rewards)));
        }
        return new StoryQuestSyncS2CPayload(List.copyOf(quests));
    }

    //? if neoforge {
    public static final Type<StoryQuestSyncS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "story_quest_sync"));
    public static final StreamCodec<FriendlyByteBuf, StoryQuestSyncS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), StoryQuestSyncS2CPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "story_quest_sync");
    *///?}
}
