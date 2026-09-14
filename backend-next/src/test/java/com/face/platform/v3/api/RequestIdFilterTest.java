package com.face.platform.v3.api;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void preservesSafeCallerRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v3/shops");
        request.addHeader("X-Request-Id", "req-20260728_ABC.01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE))
            .isEqualTo("req-20260728_ABC.01");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-20260728_ABC.01");
    }

    @Test
    void replacesUnsafeCallerRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v3/shops");
        request.addHeader("X-Request-Id", "<script>alert(1)</script>");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE).toString())
            .matches("[0-9a-f-]{36}")
            .doesNotContain("script");
        assertThat(response.getHeader("X-Request-Id"))
            .isEqualTo(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
    }
}
