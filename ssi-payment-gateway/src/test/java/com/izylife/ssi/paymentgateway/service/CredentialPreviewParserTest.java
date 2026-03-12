package com.izylife.ssi.paymentgateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialPreviewParserTest {

    private final CredentialPreviewParser parser = new CredentialPreviewParser(new ObjectMapper());

    @Test
    void shouldExtractHolderAndPaymentMethod() {
        Map<String, Object> preview = Map.of(
                "credentialSubject", Map.of(
                        "id", "did:example:123",
                        "givenName", "Alice",
                        "familyName", "Verdi",
                        "paymentMethodId", "pm_card_visa"
                )
        );

        CredentialPaymentData data = parser.toPaymentData(preview);

        assertThat(data.holderDid()).isEqualTo("did:example:123");
        assertThat(data.holderName()).isEqualTo("Alice Verdi");
        assertThat(data.paymentMethodId()).isEqualTo("pm_card_visa");
        assertThat(data.credentialPreview()).isNotNull();
    }
}
