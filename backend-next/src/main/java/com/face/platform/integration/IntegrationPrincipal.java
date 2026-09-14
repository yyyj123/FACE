package com.face.platform.integration;

import java.util.Set;

public record IntegrationPrincipal(
    long clientId,
    long tenantId,
    long shopId,
    String clientCode,
    Set<String> scopes,
    long requestLogId
) {
}
