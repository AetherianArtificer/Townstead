package com.aetherianartificer.townstead.politics.charter;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression checks against the real Minecraft buffer/component classes. */
class CharterWireTest {
    @Test
    void snapshotSurvivesWireRoundTrip() {
        var text = CharterSnapshotS2CPayload.Text.of(Component.translatableWithFallback(
                "charter.test.holders", "%s representatives: %s", 3,
                Component.translatableWithFallback("charter.test.name", "Mira %s", "Reed")).append(Component.literal(" — ").append(Component.translatableWithFallback("test.event", "appointed"))));
        var action = new CharterSnapshotS2CPayload.Action("apply", literal("Apply"), text, false);
        var organizations = new ArrayList<CharterSnapshotS2CPayload.Organization>();
        for (int i = 0; i < 100; i++) organizations.add(new CharterSnapshotS2CPayload.Organization(
                "test:group_" + i, literal("Organization " + i), literal("Unknown modded organization"),
                literal("A description retained for the detail view"), "visitor", i == 0,
                "minecraft:paper", 0xff43655f, literal("Invitation"), literal("Notice"), List.of(),
                List.of(new CharterSnapshotS2CPayload.Role(literal("Representative"), List.of(text))), List.of(action)));
        var original = new CharterSnapshotS2CPayload(BlockPos.ZERO, new BlockPos(0, 1, 1),
                CharterSnapshotS2CPayload.FOUNDED, false, "Settlement", "Polity", text, text,
                literal("Founding tradition"), "", List.of(new CharterSnapshotS2CPayload.Option("test:profile", text, text, text, true, List.of("League of {name}"))),
                List.of(new CharterSnapshotS2CPayload.Option("test:culture", text, text, text, false, List.of("Thorncourt"))), organizations, List.of(),
                List.of(new CharterSnapshotS2CPayload.CensusGroup("test:culture", literal("Culture"), 12, 0xff397f79)), 12,
                List.of(new CharterSnapshotS2CPayload.Request("request", text, literal("Applicant"), "application", "pending", 1, 2, List.of(action))), 42, List.of(new CharterSnapshotS2CPayload.CensusScope("polity", literal("Faction population"),
                        List.of(), 0, false)), new CharterSnapshotS2CPayload.Civic("test:provider", "test:actor", "active", true, false,
                        literal("Modded governance"), text, text, List.of(new CharterSnapshotS2CPayload.Role(text, List.of(text))),
                        List.of(text), List.of(action)), List.of(new CharterSnapshotS2CPayload.Heraldry("polity:test:one", text,
                                com.aetherianartificer.townstead.politics.heraldry.EmblemRecipe.DEFAULT.encode(), 7, true)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.write(buf);
            var decoded = CharterSnapshotS2CPayload.read(buf);
            assertTrue(original.equals(decoded), "Snapshot changed during wire round trip");
            assertTrue(buf.readableBytes() == 0, "Snapshot decoder left unread bytes");
            assertTrue(decoded.authority().component().getString().equals("3 representatives: Mira Reed — appointed"),
                    "Nested translation arguments were lost");
            assertTrue(decoded.civic() != null && decoded.civic().controlsGovernment() && !decoded.civic().mayManage(), "External governance authority changed");
            assertTrue(decoded.organizations().size() == 100, "Large directory was truncated");
        } finally { buf.release(); }
    }

    @Test
    void actionIntentSurvivesWireRoundTrip() {
        var intent = new CharterActionC2SPayload(BlockPos.ZERO, CharterActionC2SPayload.MEMBERSHIP,
                "", "", "", "approve", "request-id", "", 42);
        FriendlyByteBuf actionBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            intent.write(actionBuffer);
            assertTrue(intent.equals(CharterActionC2SPayload.read(actionBuffer)), "Action intent lost target or revision");
            assertTrue(actionBuffer.readableBytes() == 0, "Action decoder left bytes unread");
        } finally { actionBuffer.release(); }
    }

    @Test
    void malformedListCountsAreRejected() {
        for (int invalid : new int[]{-1, 4097}) {
            FriendlyByteBuf bad = new FriendlyByteBuf(Unpooled.buffer());
            try {
                bad.writeUtf("test:key"); bad.writeUtf(""); bad.writeVarInt(invalid);
                assertThrows(IllegalArgumentException.class, () -> CharterSnapshotS2CPayload.Text.read(bad),
                        "Invalid list count accepted: " + invalid);
            } finally { bad.release(); }
        }
    }

    @Test
    void unboundedTranslationNestingIsRejected() {
        Component nested = Component.literal("leaf");
        for (int i = 0; i < 18; i++) nested = Component.translatableWithFallback("test:nested", "%s", nested);
        Component deep = nested;
        assertThrows(IllegalArgumentException.class, () -> CharterSnapshotS2CPayload.Text.of(deep),
                "Unbounded translation nesting accepted");
    }

    private static CharterSnapshotS2CPayload.Text literal(String value) { return new CharterSnapshotS2CPayload.Text("", value); }
}
