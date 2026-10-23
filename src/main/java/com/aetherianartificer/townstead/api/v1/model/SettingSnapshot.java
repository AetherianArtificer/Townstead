package com.aetherianartificer.townstead.api.v1.model;

import java.util.List;

/**
 * One Townstead setting in effect. {@code value} is its text form: {@code true}/{@code false},
 * a number, a choice by id, or a list joined with commas. {@code values} holds the items of a
 * {@code LIST} setting and is empty otherwise.
 *
 * <p>Added in API revision 2.</p>
 */
public record SettingSnapshot(
        String key,
        SettingKind kind,
        String value,
        List<String> values,
        SettingSource source,
        boolean locked
) {
}
