package com.face.platform.security;

import java.util.List;
import java.util.Set;

public record TenantPrincipal(
    long accountId,
    long tenantId,
    Long homeShopId,
    String username,
    List<String> roles,
    Set<Long> regionIds,
    Set<Long> shopIds,
    boolean tenantWide
) {
}
