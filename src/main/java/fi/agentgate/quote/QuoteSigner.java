package fi.agentgate.quote;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The "trust protocol" for prices: every quote carries an HMAC-SHA256 over its commercial terms.
 * If price, quantity, date or expiry change anywhere between issue and order, verification fails.
 */
@Component
public class QuoteSigner {

    public static final String ALGORITHM = "HMAC-SHA256";

    private final String secret;

    public QuoteSigner(@Value("${app.quote.signing-secret}") String secret) {
        this.secret = secret;
    }

    @PostConstruct
    void check() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "QUOTE_SIGNING_SECRET must be set and at least 32 characters long (see .env.example)");
        }
    }

    /** Stable text form of the signed terms. Numbers are normalised so database scale does not matter. */
    public String canonical(Quote q) {
        return String.join("|",
                "v1",
                q.getId().toString(),
                q.getAgentUserId().toString(),
                q.getProductCode(),
                num(q.getQuantity()),
                num(q.getUnitPrice()),
                q.getCurrency(),
                num(q.getLineTotal()),
                q.getNeededBy().toString(),
                q.getExpiresAt().toString());
    }

    public String sign(Quote q) {
        return hmac(canonical(q));
    }

    public boolean verify(Quote q) {
        byte[] expected = sign(q).getBytes(StandardCharsets.UTF_8);
        byte[] actual = q.getSignature().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute quote signature", e);
        }
    }

    private static String num(BigDecimal v) {
        return v.signum() == 0 ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
