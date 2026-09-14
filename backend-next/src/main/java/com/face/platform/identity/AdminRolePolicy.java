package com.face.platform.identity;

import java.util.List;
import java.util.Locale;

public final class AdminRolePolicy {

    private static final List<String> VISIBLE_ROLES = List.of("SUPER_ADMIN", "ADMIN");

    private AdminRolePolicy() {
    }

    public static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!VISIBLE_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("管理员角色仅支持 SUPER_ADMIN 或 ADMIN");
        }
        return normalized;
    }

    public static List<String> visibleRoles() {
        return VISIBLE_ROLES;
    }
}
