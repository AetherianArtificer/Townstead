package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;

/**
 * Runs {@code command} as the actor, from a silent source at permission level 2
 * (Apoli's entity {@code execute_command}). {@code createCommandSourceStack} /
 * {@code withPermission} / {@code withSuppressedOutput} are uniform across branches.
 *
 * <p>JSON: {@code { "type":"pheno:execute_command", "command":"effect give @s glowing 5" }}</p>
 */
public final class ExecuteCommandActionType implements ActionType {

    public static final String KEY = "pheno:execute_command";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        String command = GsonHelper.getAsString(json, "command", "");
        if (command.isEmpty()) return null;
        return ctx -> {
            MinecraftServer server = ctx.entity().getServer();
            if (server == null) return;
            server.getCommands().performPrefixedCommand(
                    ctx.entity().createCommandSourceStack().withPermission(2).withSuppressedOutput(), command);
        };
    }
}
