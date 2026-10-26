package in.ommishra.ledgerline.payout;

import in.ommishra.ledgerline.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Receives Razorpay payout webhooks. The signature is an HMAC-SHA256 of the raw
 * body, so the body is read as a string and verified before it is parsed.
 *
 * <p>Anything that is authentic but cannot be applied (unknown payout, an event
 * arriving after a terminal state) is acknowledged with 200: a non-2xx would make
 * the provider redeliver an event that will never succeed.
 */
@RestController
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private static final Map<String, PayoutStatus> EVENTS = Map.of(
            "payout.processed", PayoutStatus.PROCESSED,
            "payout.failed", PayoutStatus.FAILED,
            "payout.rejected", PayoutStatus.FAILED,
            "payout.reversed", PayoutStatus.REVERSED);

    private final PayoutService payoutService;
    private final ObjectMapper json;
    private final byte[] secret;

    public RazorpayWebhookController(PayoutService payoutService, ObjectMapper json, WebhookProperties properties) {
        this.payoutService = payoutService;
        this.json = json;
        this.secret = properties.razorpaySecret().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/api/v1/webhooks/razorpay")
    ResponseEntity<Map<String, String>> receive(@RequestHeader("X-Razorpay-Signature") String signature,
                                                @RequestBody String body) {
        if (!signatureMatches(body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "invalid_signature"));
        }

        JsonNode event;
        try {
            event = json.readTree(body);
        } catch (JacksonException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "malformed"));
        }
        String type = text(event.path("event"));
        PayoutStatus next = EVENTS.get(type);
        if (next == null) {
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }

        JsonNode payout = event.path("payload").path("payout").path("entity");
        UUID payoutId;
        try {
            payoutId = UUID.fromString(String.valueOf(text(payout.path("reference_id"))));
        } catch (IllegalArgumentException e) {
            log.warn("Webhook {} has no usable reference_id", type);
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }

        try {
            boolean applied = payoutService.applyProviderUpdate(payoutId, next, text(payout.path("id")),
                    text(payout.path("status_details").path("description")));
            return ResponseEntity.ok(Map.of("status", applied ? "applied" : "duplicate"));
        } catch (NotFoundException | IllegalPayoutTransitionException e) {
            log.warn("Ignoring webhook {} for payout {}: {}", type, payoutId, e.getMessage());
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }
    }

    private boolean signatureMatches(String body, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            byte[] given = HexFormat.of().parseHex(signature);
            return MessageDigest.isEqual(expected, given); // constant time
        } catch (IllegalArgumentException e) {
            return false;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(JsonNode node) {
        return node.isString() ? node.stringValue() : null;
    }
}
