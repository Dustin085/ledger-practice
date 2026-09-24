package com.example.ledgerpractice.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

// 模擬綠界 CheckMacValue 這類「雙方共享密鑰 + 對內容做雜湊」的簽章機制。
@Component
public class WebhookSigner {

    public static final String SIGNATURE_HEADER = "X-Signature";
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public WebhookSigner(@Value("${settlement.webhook.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("無法計算簽章", e);
        }
    }

    // 用 MessageDigest.isEqual 做固定時間比較，避免從比較耗時推測出簽章內容。
    public boolean verify(String body, String signature) {
        if (signature == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sign(body).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
