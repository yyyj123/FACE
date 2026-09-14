package com.face.platform.integration;

import java.util.List;
import java.util.Map;

public interface IntegrationCatalogQueryPort {
    List<Map<String, Object>> activeServices(long tenantId, long shopId);
}
