package in.ommishra.ledgerline.payout;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ledgerline.webhooks")
public record WebhookProperties(String razorpaySecret) {
}
