package com.aetherianartificer.townstead.villager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Persisted player policy for a villager's secondary workplaces. */
public final class WorksiteAssignmentPolicy {
    public enum Mode { AUTOMATIC, MANUAL }

    private final Runnable dirty;
    private Mode mode = Mode.AUTOMATIC;
    private final Set<Long> manualSiteIds = new LinkedHashSet<>();

    public WorksiteAssignmentPolicy() {
        this(() -> {});
    }

    public WorksiteAssignmentPolicy(Runnable dirty) {
        this.dirty = dirty == null ? () -> {} : dirty;
    }

    public Mode mode() { return mode; }
    public boolean automatic() { return mode == Mode.AUTOMATIC; }
    public Set<Long> manualSiteIds() { return Set.copyOf(manualSiteIds); }

    public boolean permitsAdditional(long worksiteId) {
        return automatic() || manualSiteIds.contains(worksiteId);
    }

    public void setAutomatic() {
        if (mode == Mode.AUTOMATIC) return;
        mode = Mode.AUTOMATIC;
        dirty.run();
    }

    public void setManual(Set<Long> worksiteIds) {
        Set<Long> clean = worksiteIds == null ? Set.of() : Set.copyOf(worksiteIds);
        if (mode == Mode.MANUAL && manualSiteIds.equals(clean)) return;
        mode = Mode.MANUAL;
        manualSiteIds.clear();
        manualSiteIds.addAll(clean);
        dirty.run();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode.name().toLowerCase(Locale.ROOT));
        ListTag sites = new ListTag();
        for (long id : manualSiteIds) sites.add(LongTag.valueOf(id));
        tag.put("sites", sites);
        return tag;
    }

    public void load(CompoundTag tag) {
        mode = "manual".equalsIgnoreCase(tag.getString("mode")) ? Mode.MANUAL : Mode.AUTOMATIC;
        manualSiteIds.clear();
        ListTag sites = tag.getList("sites", Tag.TAG_LONG);
        for (int i = 0; i < sites.size(); i++) manualSiteIds.add(((LongTag) sites.get(i)).getAsLong());
    }
}
