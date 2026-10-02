package com.aetherianartificer.townstead.client.gui.character;

import com.aetherianartificer.townstead.root.CharacterEditorLayout;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CharacterEditorResolverTest {
    @Test void customEyesReplaceOnlyTheNativeEyeControls() {
        var gene = new GeneCatalogEntry("test:eyes", "Eyes", "", "Eyes",
                GeneDisplay.Kind.VARIANTS.ordinal(), 0f, 1f, "", 0f, 0, "", 1,
                List.of(), "", "", "eyes", "", "", List.of(), List.of(), "");
        var tabs = new ArrayList<>(List.of(
                new CharacterEditorResolver.Tab("eyes", Component.literal("Eyes"),
                        List.of(CharacterEditorResolver.Field.nativeGroup(CharacterEditorLayout.NATIVE_EYES))),
                new CharacterEditorResolver.Tab("body", Component.literal("Body"),
                        List.of(CharacterEditorResolver.Field.nativeGroup(CharacterEditorLayout.NATIVE_BODY))),
                new CharacterEditorResolver.Tab("townstead_char:Eyes", Component.literal("Eyes"),
                        List.of(CharacterEditorResolver.Field.gene(gene)))));
        var result = CharacterEditorResolver.finish(tabs);
        assertNull(result.byPage("eyes"));
        assertNotNull(result.byPage("body"));
        assertNotNull(result.byPage("townstead_char:Eyes"));
        tabs.remove(2);
        assertNotNull(CharacterEditorResolver.finish(tabs).byPage("eyes"));
    }
}
