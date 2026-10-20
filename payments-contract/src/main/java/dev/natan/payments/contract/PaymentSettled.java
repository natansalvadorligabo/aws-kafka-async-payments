package dev.natan.payments.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published once per payment after a consumer settles it.
 *
 * <pre>{@code
 * PaymentSettled.of(requested, Instant.now());
 * }</pre>
 */
public record PaymentSettled(
        UUID paymentId,
        String accountId,
        BigDecimal amount,
        Instant settledAt) {

    public static PaymentSettled of(PaymentRequested payment, Instant settledAt) {
        return new PaymentSettled(payment.paymentId(), payment.accountId(), payment.amount(), settledAt);
    }
}
