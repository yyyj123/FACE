package com.face.platform.v3.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTokenCodecTest {

    private final SessionTokenCodec codec = new SessionTokenCodec();

    @Test
    void issuesIndependentOpaqueTokensAndStoresOnlyStableHashes() {
        SessionTokenCodec.TokenPair first = codec.issue();
        SessionTokenCodec.TokenPair second = codec.issue();

        assertThat(first.accessToken()).isNotEqualTo(first.refreshToken());
        assertThat(first.accessToken()).isNotEqualTo(second.accessToken());
        assertThat(first.accessTokenHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(first.refreshTokenHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(first.accessTokenHash()).isNotEqualTo(first.accessToken());
        assertThat(codec.hash(first.accessToken())).isEqualTo(first.accessTokenHash());
        assertThat(codec.hash(first.refreshToken())).isEqualTo(first.refreshTokenHash());
    }
}
