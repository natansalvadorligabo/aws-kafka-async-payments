package dev.natan.payments.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment the API accepted and queued for asynchronous settlement.
 *
 * <pre>{@code
 * new PaymentRequested(UUID.randomUUID(), "acc-1", "alice@example.com", new BigDecimal("10.00"), Instant.now());
 * }</pre>
 *
 * @param paymentId   unique id, also the idempotency key for consumers
 * @param accountId   payer account; later the Kafka partition key
 * @param pixKey      receiver's PIX-like key (e-mail, phone, random key)
 * @param amount      positive amount in BRL with two decimal places
 * @param requestedAt when the API accepted the payment
 */
public record PaymentRequested(
        UUID paymentId,
        String accountId,
        String pixKey,
        BigDecimal amount,
        Instant requestedAt) {
}
