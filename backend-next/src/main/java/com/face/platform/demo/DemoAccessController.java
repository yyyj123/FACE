package com.face.platform.demo;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@Profile("demo")
@RequestMapping("/api/v3/demo/access")
public class DemoAccessController {

    static final String COOKIE_NAME = "face_demo_access";

    private final DemoAccessService accessService;

    public DemoAccessController(DemoAccessService accessService) {
        this.accessService = accessService;
    }

    @PostMapping("/session")
    public ResponseEntity<Map<String, Object>> create(@RequestBody AccessRequest body) {
        if (body == null || !accessService.passwordMatches(body.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("authorized", false, "message", "演示访问密码不正确"));
        }
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, accessService.createToken())
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(DemoAccessService.SESSION_TTL)
            .build();
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(Map.of("authorized", true, "expires_in_seconds", DemoAccessService.SESSION_TTL.toSeconds()));
    }

    @GetMapping("/check")
    public ResponseEntity<Void> check(
        @CookieValue(name = COOKIE_NAME, required = false) String token
    ) {
        return accessService.tokenValid(token)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> clear() {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ZERO)
            .build();
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .build();
    }

    public record AccessRequest(String password) {}
}
