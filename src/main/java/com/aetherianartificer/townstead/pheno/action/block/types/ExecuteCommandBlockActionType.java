package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Runs {@code command} from a silent command source positioned at the target block
 * (Apoli's block {@code execute_command}). Permission level 2; the {@code cause} entity,
 * if any, is the source entity. The public {@code CommandSourceStack} constructor is
 * identical on both branches.
 *
 * <p>JSON: {@code { "type":"pheno:execute_command", "command":"setblock ~ ~1 ~ torch" }}</p>
 */
public final class ExecuteCommandBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:execute_command";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        String command = GsonHelper.getAsString(json, "command", "");
        if (command.isEmpty()) return null;
        return ctx -> {
            if (ctx.level().getServer() == null) return;
            CommandSourceStack source = new CommandSourceStack(CommandSource.NULL,
                    Vec3.atCenterOf(ctx.pos()), Vec2.ZERO, ctx.level(), 2, "Origin",
                    Component.literal("Origin"), ctx.level().getServer(), ctx.cause());
            ctx.level().getServer().getCommands().performPrefixedCommand(source, command);
        };
    }
}
