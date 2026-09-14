package com.face.platform.integration;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntegrationClientFilterTest {

    @Test
    void authenticatesOnlyVersionedIntegrationCatalogAndLeavesConsumerOpenRoutesPublic() {
        IntegrationClientFilter filter = new IntegrationClientFilter(
            mock(IntegrationClientApplicationService.class)
        );

        assertThat(filter.shouldNotFilter(request("/api/v3/open/v1/catalog/services"))).isFalse();
        assertThat(filter.shouldNotFilter(request("/api/v3/open/content/home"))).isTrue();
        assertThat(filter.shouldNotFilter(request("/api/v3/open/shop-context"))).isTrue();
    }

    private HttpServletRequest request(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContextPath()).thenReturn("");
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getMethod()).thenReturn("GET");
        return request;
    }
}
