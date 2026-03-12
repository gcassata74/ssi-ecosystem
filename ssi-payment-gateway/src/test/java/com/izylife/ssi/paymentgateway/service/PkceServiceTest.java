package com.izylife.ssi.paymentgateway.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PkceServiceTest {

    private final PkceService pkceService = new PkceService();

    @Test
    void shouldGenerateVerifierAndChallenge() {
        PkcePair pair = pkceService.createPair();
        assertThat(pair.codeVerifier()).isNotNull();
        assertThat(pair.codeChallenge()).isNotNull();
        assertThat(pair.codeVerifier()).doesNotContain("=");
        assertThat(pair.method()).isEqualTo("S256");
    }
}
