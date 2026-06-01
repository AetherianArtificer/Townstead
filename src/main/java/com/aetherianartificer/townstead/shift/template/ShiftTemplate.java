package com.aetherianartificer.townstead.shift.template;

import com.aetherianartificer.townstead.shift.ShiftData;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Optional;

/**
 * Named shift schedule. Two flavors: built-in (loaded from data pack JSON,
 * `builtIn=true`) and user-defined (stored in world SavedData).
 *
 * Built-in templates use namespace `townstead`; user templates use
 * `townstead_user` so the namespaces cannot collide.
 */
public record ShiftTemplate(ResourceLocation id, String displayName, int[] shifts,
                            Optional<String> chronotype, boolean builtIn) {

    public static final String USER_NAMESPACE = "townstead_user";

    public ShiftTemplate {
        if (id == null) throw new IllegalArgumentException("id");
        if (displayName == null || displayName.isBlank()) displayName = id.getPath();
        if (shifts == null || shifts.length != ShiftData.HOURS_PER_DAY) {
            throw new IllegalArgumentException("shifts must have length " + ShiftData.HOURS_PER_DAY);
        }
        shifts = Arrays.copyOf(shifts, shifts.length);
        if (chronotype == null) chronotype = Optional.empty();
        for (int s : shifts) {
            if (s < 0 || s >= ShiftData.ORDINAL_TO_ACTIVITY.length) {
                throw new IllegalArgumentException("shift ordinal out of range: " + s);
            }
        }
    }

    public boolean isUserTemplate() {
        return USER_NAMESPACE.equals(id.getNamespace());
    }

    public int[] copyShifts() {
        return Arrays.copyOf(shifts, shifts.length);
    }
}
