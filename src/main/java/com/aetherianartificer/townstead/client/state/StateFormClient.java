package com.aetherianartificer.townstead.client.state;

import java.util.HashMap;
import java.util.Map;

/** The forms the server says entities are in, by entity id: the rig they wear and whether they talk. */
public final class StateFormClient {
    private record Form(String rig, boolean talk, Map<String, Integer> variants) {}

    private static final Map<Integer, Form> FORMS = new HashMap<>();

    private StateFormClient() {}

    public static void set(int entityId, String rig, boolean talk, Map<String, Integer> variants) {
        if (rig.isEmpty() && talk) FORMS.remove(entityId);
        else FORMS.put(entityId, new Form(rig, talk, variants));
    }

    /** The variant the form picks for a texture placeholder, or 0. */
    public static int variant(int entityId, String name) {
        Form form = FORMS.get(entityId);
        return form == null ? 0 : form.variants().getOrDefault(name, 0);
    }

    /** The rig a state puts this entity in, or null. */
    public static String rig(int entityId) {
        Form form = FORMS.get(entityId);
        return form == null || form.rig().isEmpty() ? null : form.rig();
    }

    public static boolean canTalk(int entityId) {
        Form form = FORMS.get(entityId);
        return form == null || form.talk();
    }

    public static void clear() {
        FORMS.clear();
    }
}
