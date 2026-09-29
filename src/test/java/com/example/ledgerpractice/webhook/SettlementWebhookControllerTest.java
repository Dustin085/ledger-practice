package com.example.ledgerpractice.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

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

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    @Test
    void validSignatureConfirmedIsPublishedWithConfirmedRoutingKey() throws Exception {
        long timestamp = now();

        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.TIMESTAMP_HEADER, String.valueOf(timestamp))
                        .header(WebhookSigner.SIGNATURE_HEADER, webhookSigner.sign(timestamp, CONFIRMED_BODY))
                        .content(CONFIRMED_BODY))
                .andExpect(status().isOk());

        verify(rabbitTemplate).convertAndSend(
                eq(EXTERNAL_SETTLEMENT_EXCHANGE_NAME), eq(TRANSFER_CONFIRMED_ROUTING_KEY), any(Object.class));
    }

    @Test
    void validSignatureFailedIsPublishedWithFailedRoutingKey() throws Exception {
        long timestamp = now();

        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.TIMESTAMP_HEADER, String.valueOf(timestamp))
                        .header(WebhookSigner.SIGNATURE_HEADER, webhookSigner.sign(timestamp, FAILED_BODY))
                        .content(FAILED_BODY))
                .andExpect(status().isOk());

        verify(rabbitTemplate).convertAndSend(
                eq(EXTERNAL_SETTLEMENT_EXCHANGE_NAME), eq(TRANSFER_FAILED_ROUTING_KEY), any(Object.class));
    }

    @Test
    void wrongSignatureIsRejectedAndNothingIsPublished() throws Exception {
        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.TIMESTAMP_HEADER, String.valueOf(now()))
                        .header(WebhookSigner.SIGNATURE_HEADER, "not-a-valid-signature")
                        .content(CONFIRMED_BODY))
                .andExpect(status().isUnauthorized());

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void missingSignatureIsRejected() throws Exception {
        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.TIMESTAMP_HEADER, String.valueOf(now()))
                        .content(CONFIRMED_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedBodyFailsSignatureVerification() throws Exception {
        long timestamp = now();
        String signatureOfConfirmed = webhookSigner.sign(timestamp, CONFIRMED_BODY);

        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.TIMESTAMP_HEADER, String.valueOf(timestamp))
                        .header(WebhookSigner.SIGNATURE_HEADER, signatureOfConfirmed)
                        .content(CONFIRMED_BODY.replace("CONFIRMED", "FAILED")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validSignatureButMalformedBodyIsBadRequest() throws Exception {
        long timestamp = now();
        String malformed = "{\"externalReferenceId\":\"MOCK-1\"";

        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.TIMESTAMP_HEADER, String.valueOf(timestamp))
                        .header(WebhookSigner.SIGNATURE_HEADER, webhookSigner.sign(timestamp, malformed))
                        .content(malformed))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingTimestampIsRejected() throws Exception {
        // 沒帶時間戳記，簽章不管填什麼都不會對，因為簽章是對「時間戳記+body」算的。
        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.SIGNATURE_HEADER, "irrelevant")
                        .content(CONFIRMED_BODY))
                .andExpect(status().isUnauthorized());

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void malformedTimestampIsRejected() throws Exception {
        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.TIMESTAMP_HEADER, "not-a-number")
                        .header(WebhookSigner.SIGNATURE_HEADER, "irrelevant")
                        .content(CONFIRMED_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTimestampIsRejectedEvenWithACorrectSignature() throws Exception {
        // 簽章本身完全正確（對「舊時間戳記+body」算的），但這個時間戳記已經超過容許誤差
        // （application.yml 設定 5 分鐘），代表這是一則被重放的舊請求。
        long oldTimestamp = now() - Duration.ofMinutes(10).toSeconds();
        String signature = webhookSigner.sign(oldTimestamp, CONFIRMED_BODY);

        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.TIMESTAMP_HEADER, String.valueOf(oldTimestamp))
                        .header(WebhookSigner.SIGNATURE_HEADER, signature)
                        .content(CONFIRMED_BODY))
                .andExpect(status().isUnauthorized());

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void timestampFromTheFutureBeyondToleranceIsRejected() throws Exception {
        long futureTimestamp = now() + Duration.ofMinutes(10).toSeconds();
        String signature = webhookSigner.sign(futureTimestamp, CONFIRMED_BODY);

        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.TIMESTAMP_HEADER, String.valueOf(futureTimestamp))
                        .header(WebhookSigner.SIGNATURE_HEADER, signature)
                        .content(CONFIRMED_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swappedTimestampFailsSignatureVerificationEvenThoughBodyIsUntouched() throws Exception {
        // 證明時間戳記真的有被簽進去：簽章是對 timestamp1 算的，header 卻換成 timestamp2
        // （同樣在容許誤差內、body 完全沒被動過），也要被拒絕，不能只讓「內容」決定簽章對不對。
        long signedTimestamp = now();
        String signature = webhookSigner.sign(signedTimestamp, CONFIRMED_BODY);
        long swappedTimestamp = signedTimestamp + 1;

        mockMvc.perform(post("/webhooks/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSigner.TIMESTAMP_HEADER, String.valueOf(swappedTimestamp))
                        .header(WebhookSigner.SIGNATURE_HEADER, signature)
                        .content(CONFIRMED_BODY))
                .andExpect(status().isUnauthorized());
    }
}
