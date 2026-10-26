package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server to client: the player's Vampirism tasks for the Quest Ledger, read from Vampirism's own task
 * manager, since its client only hears of them while its menu is open. Text travels as
 * (translate key, fallback) pairs so each client reads it in its own language.
 */
//? if neoforge {
public record VampirismTasksS2CPayload(List<Task> tasks) implements CustomPacketPayload {
//?} else {
/*public record VampirismTasksS2CPayload(List<Task> tasks) {
*///?}

    public static final int OFFERED = 0, ACCEPTED = 1, DONE = 2;

    /**
     * One task. {@code id} is the task instance (or the task, for a finished unique one); {@code board}
     * is the task master's UUID, empty for a unique task; {@code deadline} is the game time it runs
     * out, 0 for none; {@code hasPos} says whether x, y, z are where the master was last seen.
     */
    public record Task(String id, String[] title, String[] description, String board, int state,
                       boolean ready, long deadline, boolean hasPos, int x, int y, int z,
                       List<Requirement> requirements, List<Reward> rewards) {}

    /** One requirement: its kind's heading and its own name as text pairs, or an item, and its count. */
    public record Requirement(String[] kind, String[] label, String itemId, int current, int total) {}

    /** One reward: an item and its count, or a line of text. */
    public record Reward(String itemId, int count, String[] text) {}

    //? if neoforge {
    public static final Type<VampirismTasksS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "vampirism_tasks_s2c"));
    public static final StreamCodec<FriendlyByteBuf, VampirismTasksS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), VampirismTasksS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(tasks.size());
        for (Task task : tasks) {
            buf.writeUtf(task.id(), 128);
            pair(buf, task.title());
            pair(buf, task.description());
            buf.writeUtf(task.board(), 64);
            buf.writeVarInt(task.state());
            buf.writeBoolean(task.ready());
            buf.writeLong(task.deadline());
            buf.writeBoolean(task.hasPos());
            buf.writeInt(task.x());
            buf.writeInt(task.y());
            buf.writeInt(task.z());
            buf.writeVarInt(task.requirements().size());
            for (Requirement r : task.requirements()) {
                pair(buf, r.kind());
                pair(buf, r.label());
                buf.writeUtf(r.itemId(), 256);
                buf.writeVarInt(Math.max(0, r.current()));
                buf.writeVarInt(Math.max(0, r.total()));
            }
            buf.writeVarInt(task.rewards().size());
            for (Reward r : task.rewards()) {
                buf.writeUtf(r.itemId(), 256);
                buf.writeVarInt(Math.max(0, r.count()));
                pair(buf, r.text());
            }
        }
    }

    public static VampirismTasksS2CPayload read(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 256);
        List<Task> tasks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf(128);
            String[] title = pair(buf);
            String[] description = pair(buf);
            String board = buf.readUtf(64);
            int state = buf.readVarInt();
            boolean ready = buf.readBoolean();
            long deadline = buf.readLong();
            boolean hasPos = buf.readBoolean();
            int x = buf.readInt(), y = buf.readInt(), z = buf.readInt();
            int reqCount = Math.min(buf.readVarInt(), 32);
            List<Requirement> requirements = new ArrayList<>(reqCount);
            for (int j = 0; j < reqCount; j++) {
                requirements.add(new Requirement(pair(buf), pair(buf), buf.readUtf(256), buf.readVarInt(), buf.readVarInt()));
            }
            int rewardCount = Math.min(buf.readVarInt(), 32);
            List<Reward> rewards = new ArrayList<>(rewardCount);
            for (int j = 0; j < rewardCount; j++) rewards.add(new Reward(buf.readUtf(256), buf.readVarInt(), pair(buf)));
            tasks.add(new Task(id, title, description, board, state, ready, deadline, hasPos, x, y, z,
                    List.copyOf(requirements), List.copyOf(rewards)));
        }
        return new VampirismTasksS2CPayload(List.copyOf(tasks));
    }

    private static void pair(FriendlyByteBuf buf, String[] pair) {
        buf.writeUtf(pair == null || pair.length < 1 ? "" : pair[0], 256);
        buf.writeUtf(pair == null || pair.length < 2 ? "" : pair[1], 1024);
    }

    private static String[] pair(FriendlyByteBuf buf) {
        return new String[]{buf.readUtf(256), buf.readUtf(1024)};
    }
}
