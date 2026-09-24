package com.example.ledgerpractice.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.example.ledgerpractice.outbox.RabbitConfig.EXTERNAL_SETTLEMENT_EXCHANGE_NAME;
import static com.example.ledgerpractice.outbox.RabbitConfig.TRANSFER_CONFIRMED_ROUTING_KEY;
import static com.example.ledgerpractice.outbox.RabbitConfig.TRANSFER_FAILED_ROUTING_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementWebhookController.class)
@Import(WebhookSigner.class)
class SettlementWebhookControllerTest {

    private static final String CONFIRMED_BODY =
            "{\"externalReferenceId\":\"MOCK-1\",\"externalEventId\":\"evt-1\",\"result\":\"CONFIRMED\"}";
    private static final String FAILED_BODY =
            "{\"externalReferenceId\":\"MOCK-1\",\"externalEventId\":\"evt-2\",\"result\":\"FAILED\"}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private WebhookSigner webhookSigner;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void validSignatureConfirmedIsPublishedWithConfirmedRoutingKey() throws Exception {
        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.SIGNATURE_HEADER, webhookSigner.sign(CONFIRMED_BODY))
                        .content(CONFIRMED_BODY))
                .andExpect(status().isOk());

        verify(rabbitTemplate).convertAndSend(
                eq(EXTERNAL_SETTLEMENT_EXCHANGE_NAME), eq(TRANSFER_CONFIRMED_ROUTING_KEY), any(Object.class));
    }

    @Test
    void validSignatureFailedIsPublishedWithFailedRoutingKey() throws Exception {
        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.SIGNATURE_HEADER, webhookSigner.sign(FAILED_BODY))
                        .content(FAILED_BODY))
                .andExpect(status().isOk());

        verify(rabbitTemplate).convertAndSend(
                eq(EXTERNAL_SETTLEMENT_EXCHANGE_NAME), eq(TRANSFER_FAILED_ROUTING_KEY), any(Object.class));
    }

    @Test
    void wrongSignatureIsRejectedAndNothingIsPublished() throws Exception {
        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.SIGNATURE_HEADER, "not-a-valid-signature")
                        .content(CONFIRMED_BODY))
                .andExpect(status().isUnauthorized());

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void missingSignatureIsRejected() throws Exception {
        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONFIRMED_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedBodyFailsSignatureVerification() throws Exception {
        String signatureOfConfirmed = webhookSigner.sign(CONFIRMED_BODY);

        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.SIGNATURE_HEADER, signatureOfConfirmed)
                        .content(CONFIRMED_BODY.replace("CONFIRMED", "FAILED")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validSignatureButMalformedBodyIsBadRequest() throws Exception {
        String malformed = "{\"externalReferenceId\":\"MOCK-1\"";

        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.SIGNATURE_HEADER, webhookSigner.sign(malformed))
                        .content(malformed))
                .andExpect(status().isBadRequest());
    }
}
