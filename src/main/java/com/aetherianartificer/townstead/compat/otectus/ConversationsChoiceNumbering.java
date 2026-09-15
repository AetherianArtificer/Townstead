package com.aetherianartificer.townstead.compat.otectus;

import java.lang.reflect.Method;

/**
 * Detects that MCA: Conversations is numbering our dialogue choices itself.
 *
 * <p>Conversations mixes into {@code ChoicePanel} and paints a number badge on each visible row.
 * It asks us for nothing and needs to know nothing about us, which is how it should stay, so we
 * read the two facts we need from its own side: the marker interface its mixin adds to our panel,
 * and its own display toggle. When both say yes, the panel starts its text past the badges instead
 * of letting them land on it (see {@code ChoicePanel}).
 *
 * <p>Reflection only, resolved once. A Conversations that renames either of these simply stops
 * being detected and we lay out as we always did; nothing here can fail loudly.
 */
public final class ConversationsChoiceNumbering {

    private static final String BRIDGE =
            "dev.otectus.mcaconversations.client.townstead.NumberedChoicePanelBridge";
    private static final String CONTROLLER =
            "dev.otectus.mcaconversations.client.dialogue.ClientChoiceController";

    private static boolean resolved;
    private static Class<?> bridgeInterface;
    private static Method numberingEnabled;

    private ConversationsChoiceNumbering() {
    }

    /** True when Conversations will paint numbers over {@code panel} on this frame. */
    public static boolean numbersRows(Object panel) {
        if (panel == null) {
            return false;
        }
        resolve();
        if (bridgeInterface == null || !bridgeInterface.isInstance(panel)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(numberingEnabled.invoke(null));
        } catch (Throwable t) {
            return false;
        }
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            ClassLoader loader = ConversationsChoiceNumbering.class.getClassLoader();
            Class<?> bridge = Class.forName(BRIDGE, false, loader);
            Method toggle = Class.forName(CONTROLLER, false, loader).getMethod("numberingEnabled");
            if (!java.lang.reflect.Modifier.isStatic(toggle.getModifiers())) {
                return;
            }
            toggle.setAccessible(true);
            bridgeInterface = bridge;
            numberingEnabled = toggle;
        } catch (Throwable absent) {
            // Conversations is not installed, or no longer numbers choices this way.
        }
    }
}
