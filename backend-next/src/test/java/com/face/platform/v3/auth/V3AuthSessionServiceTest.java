package com.face.platform.v3.auth;

import com.face.platform.v3.api.V3ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V3AuthSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T06:30:00Z");
    private final V3AuthSessionRepository repository = mock(V3AuthSessionRepository.class);
    private final SessionTokenCodec codec = new SessionTokenCodec();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final V3AuthSessionService service = new V3AuthSessionService(
        repository,
        codec,
        passwordEncoder,
        Clock.fixed(NOW, ZoneOffset.UTC),
        Duration.ofMinutes(15),
        Duration.ofDays(30)
    );

    @Test
    void successfulLoginPersistsOnlyTokenHashes() {
        when(repository.findAccountForLogin("manager")).thenReturn(Optional.of(
            new V3AuthSessionRepository.LoginAccount(
                10L,
                1L,
                2L,
                "manager",
                passwordEncoder.encode("Synthetic!123"),
                "ACTIVE",
                false
            )
        ));

        V3AuthSessionService.SessionTokens tokens = service.login(" manager ", "Synthetic!123");

        ArgumentCaptor<V3AuthSessionRepository.NewSession> captor =
            ArgumentCaptor.forClass(V3AuthSessionRepository.NewSession.class);
        verify(repository).create(captor.capture());
        V3AuthSessionRepository.NewSession stored = captor.getValue();

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(stored.accessTokenHash()).isEqualTo(codec.hash(tokens.accessToken()));
        assertThat(stored.refreshTokenHash()).isEqualTo(codec.hash(tokens.refreshToken()));
        assertThat(stored.accessTokenHash()).isNotEqualTo(tokens.accessToken());
        assertThat(stored.refreshTokenHash()).isNotEqualTo(tokens.refreshToken());
        verify(repository).recordLoginSuccess(10L, NOW);
    }

    @Test
    void failedPasswordIsRecordedWithoutRevealingWhetherCredentialsExist() {
        when(repository.findAccountForLogin("manager")).thenReturn(Optional.of(
            new V3AuthSessionRepository.LoginAccount(
                10L,
                1L,
                2L,
                "manager",
                passwordEncoder.encode("Synthetic!123"),
                "ACTIVE",
                false
            )
        ));

        assertThatThrownBy(() -> service.login("manager", "wrong-password"))
            .isInstanceOfSatisfying(V3ApiException.class, exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(exception.code()).isEqualTo("UNAUTHENTICATED");
            });

        verify(repository).recordLoginFailure(10L, NOW, 5, Duration.ofMinutes(15));
    }

    @Test
    void lockedAccountIsRateLimited() {
        when(repository.findAccountForLogin("manager")).thenReturn(Optional.of(
            new V3AuthSessionRepository.LoginAccount(
                10L,
                1L,
                2L,
                "manager",
                passwordEncoder.encode("Synthetic!123"),
                "ACTIVE",
                true
            )
        ));

        assertThatThrownBy(() -> service.login("manager", "Synthetic!123"))
            .isInstanceOfSatisfying(V3ApiException.class, exception -> {
                assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(exception.code()).isEqualTo("RATE_LIMITED");
            });
    }

    @Test
    void refreshRotatesBothTokensWithOptimisticVersion() {
        String oldRefreshToken = codec.issue().refreshToken();
        when(repository.findRefreshableForUpdate(codec.hash(oldRefreshToken), NOW)).thenReturn(Optional.of(
            new V3AuthSessionRepository.RefreshableSession(
                7L,
                "session-7",
                1L,
                10L,
                2L,
                "manager",
                3L
            )
        ));
        when(repository.rotate(eq(7L), eq(3L), any())).thenReturn(true);

        V3AuthSessionService.SessionTokens rotated = service.refresh(oldRefreshToken);

        ArgumentCaptor<V3AuthSessionRepository.Rotation> captor =
            ArgumentCaptor.forClass(V3AuthSessionRepository.Rotation.class);
        verify(repository).rotate(eq(7L), eq(3L), captor.capture());
        assertThat(captor.getValue().refreshTokenHash()).isEqualTo(codec.hash(rotated.refreshToken()));
        assertThat(captor.getValue().accessTokenHash()).isEqualTo(codec.hash(rotated.accessToken()));
        assertThat(rotated.refreshToken()).isNotEqualTo(oldRefreshToken);
    }

    @Test
    void concurrentOrReplayRefreshIsRejectedWhenVersionUpdateLoses() {
        String refreshToken = codec.issue().refreshToken();
        when(repository.findRefreshableForUpdate(codec.hash(refreshToken), NOW)).thenReturn(Optional.of(
            new V3AuthSessionRepository.RefreshableSession(
                7L,
                "session-7",
                1L,
                10L,
                2L,
                "manager",
                3L
            )
        ));
        when(repository.rotate(eq(7L), eq(3L), any())).thenReturn(false);

        assertThatThrownBy(() -> service.refresh(refreshToken))
            .isInstanceOfSatisfying(V3ApiException.class, exception ->
                assertThat(exception.code()).isEqualTo("UNAUTHENTICATED")
            );
    }

    @Test
    void logoutRevokesOnlySessionAddressedByAccessToken() {
        String accessToken = codec.issue().accessToken();
        when(repository.revokeByAccessHash(codec.hash(accessToken), NOW, "LOGOUT")).thenReturn(true);

        service.logout(accessToken);

        verify(repository).revokeByAccessHash(codec.hash(accessToken), NOW, "LOGOUT");
    }
}
