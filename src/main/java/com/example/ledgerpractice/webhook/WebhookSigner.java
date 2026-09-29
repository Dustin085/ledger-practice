package com.example.ledgerpractice.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

// 模擬綠界 CheckMacValue 這類「雙方共享密鑰 + 對內容做雜湊」的簽章機制，
// 額外加上時間戳記防重放：簽章只保證內容沒被改、來源知道密鑰，擋不住把合法請求原封不動重送一次。
@Component
public class WebhookSigner {

    public static final String SIGNATURE_HEADER = "X-Signature";
    public static final String TIMESTAMP_HEADER = "X-Signature-Timestamp";
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final Duration tolerance;

    public WebhookSigner(
            @Value("${settlement.webhook.secret}") String secret,
            @Value("${settlement.webhook.timestamp-tolerance}") Duration tolerance) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.tolerance = tolerance;
    }

    public String sign(long timestampEpochSeconds, String body) {
        return hmac(signedContent(timestampEpochSeconds, body));
    }

    // 時間戳記必須是簽章保護的內容之一，不能只驗 body、時間戳記另外裸傳：
    // 不然攻擊者可以把攔截到的舊請求的時間戳記直接改成現在的值，繞過下面的過期檢查，
    // 簽章卻還是驗證得過，等於防重放形同虛設。
    public boolean verify(String timestampHeader, String body, String signature) {
        Long timestamp = parseTimestamp(timestampHeader);
        if (timestamp == null || signature == null) {
            return false;
        }
        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > tolerance.toSeconds()) {
            return false;
        }
        // 用 MessageDigest.isEqual 做固定時間比較，避免從比較耗時推測出簽章內容。
        return MessageDigest.isEqual(
                hmac(signedContent(timestamp, body)).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String content) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("無法計算簽章", e);
        }
    }

    private static String signedContent(long timestamp, String body) {
        return timestamp + "." + body;
    }

    private static Long parseTimestamp(String header) {
        if (header == null) {
            return null;
        }
        try {
            return Long.parseLong(header);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
