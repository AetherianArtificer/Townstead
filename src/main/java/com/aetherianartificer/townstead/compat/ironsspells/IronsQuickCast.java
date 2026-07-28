package com.aetherianartificer.townstead.compat.ironsspells;

//? if >=1.21 {

import com.aetherianartificer.townstead.client.input.KeybindDetails;
import com.aetherianartificer.townstead.client.input.LiveKeybinds;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Reads what is ACTUALLY in an Iron's quick-cast slot, so the wheel can show the spell rather than
 * the slot number.
 *
 * <p>Iron's ships fifteen "Quick Cast Slot" bindings, and every one is an index resolved against
 * the player's equipped spellbook: {@code Utils.serverSideInitiateQuickCast} does
 * {@code new SpellSelectionManager(player).getSpellSlot(slot)}. So the contents change when the
 * book does, and no pack can name them. This mirrors that lookup on the client.</p>
 *
 * <p>Their API, not reflection. It is published as its own artifact for addons and their licence
 * names this use exactly: "Write your own code that uses this code as a dependency (such as addons
 * or datapacks)." Reflection would be stringly-typed against a mod that renames things between
 * versions, and would fail as a silently empty wheel rather than a compile error. We hold a
 * {@code ResourceLocation} for their icon and never a copy of it, so nothing of theirs ships here,
 * which is the one thing that licence forbids.</p>
 */
public final class IronsQuickCast implements LiveKeybinds.Source {

    /** Their binding names run 1..15; the slots they index run 0..14. */
    private static final String PREFIX = "key.irons_spellbooks.spell_quick_cast_";

    private IronsQuickCast() {}

    /** Registers the bridge, and does nothing at all when Iron's is absent. */
    public static void register() {
        if (!net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks")) return;
        LiveKeybinds.register(new IronsQuickCast());
    }

    @Override
    public KeybindDetails.Detail resolve(String keybind) {
        if (keybind == null || !keybind.startsWith(PREFIX)) return null;
        int slot;
        try {
            slot = Integer.parseInt(keybind.substring(PREFIX.length())) - 1;
        } catch (NumberFormatException ex) {
            return null;
        }
        if (slot < 0) return null;
        Player player = Minecraft.getInstance().player;
        if (player == null) return null;

        SpellSelectionManager.SelectionOption option =
                new SpellSelectionManager(player).getSpellSlot(slot);
        if (option == null) return null;
        SpellData data = option.spellData;
        // An empty slot is CLAIMED, not passed on: this binding is ours to answer for, and the
        // honest answer is that there is nothing in it. Returning null would let a pack's stale
        // icon stand in for a spell the player no longer carries.
        if (data == null || data == SpellData.EMPTY) return KeybindDetails.Detail.NONE;

        AbstractSpell spell = data.getSpell();
        if (spell == null) return KeybindDetails.Detail.NONE;
        return new KeybindDetails.Detail(
                spell.getSpellIconResource().toString(),
                spell.getDisplayName(player).getString(),
                "");
    }
}

//?} else {
/*public final class IronsQuickCast {
    private IronsQuickCast() {}

    // Iron's Spells has no 1.20.1 build in this instance's line, and the API artifact is only a
    // compile dependency of the NeoForge build, so there is nothing to bridge to here.
    public static void register() {}
}
*///?}
