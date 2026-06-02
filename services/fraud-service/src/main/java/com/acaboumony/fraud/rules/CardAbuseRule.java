package com.acaboumony.fraud.rules;

import com.acaboumony.fraud.dto.request.FraudAnalysisRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CardAbuseRule implements FraudRule {

    static final String KEY_PREFIX = "fraud:card:";

    @Override
    public int evaluate(FraudAnalysisRequest request, StringRedisTemplate redis) {
        String cardHash = hashPaymentMethod(request.paymentMethodId());
        String countStr = redis.opsForValue().get(KEY_PREFIX + cardHash);
        if (countStr == null) return 0;
        long count = Long.parseLong(countStr);
        return count >= 3 ? 35 : 0;
    }

    @Override
    public String getReason() {
        return "CARD_ABUSE";
    }

    @Override
    public int getScore() {
        return 35;
    }

    static String hashPaymentMethod(String paymentMethodId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(paymentMethodId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return paymentMethodId;
        }
    }
}
