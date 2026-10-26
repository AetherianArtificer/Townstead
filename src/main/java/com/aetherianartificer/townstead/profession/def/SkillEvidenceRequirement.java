package com.aetherianartificer.townstead.profession.def;

/**
 * One counted deed a character must have recorded before a skill can be learned.
 * The key is a Chronicle counter and {@code target} is its inclusive minimum.
 */
public record SkillEvidenceRequirement(String key, int target) {
    public SkillEvidenceRequirement {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Skill evidence key must not be blank");
        }
        if (target <= 0) {
            throw new IllegalArgumentException("Skill evidence target must be positive");
        }
    }
}
