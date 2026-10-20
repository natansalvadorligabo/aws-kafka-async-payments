package dev.natan.payments.settlement;

import java.util.UUID;

/**
 * Remembers which payments were already taken, so a redelivered message is not settled twice.
 *
 * <pre>{@code
 * if (processedPayments.claim(paymentId)) { ... }
 * }</pre>
 */
public interface ProcessedPayments {

    /**
     * Atomically records the payment as taken; {@code false} means it was already claimed.
     * Breaks command/query separation on purpose: check-and-set must be one atomic call.
     */
    boolean claim(UUID paymentId);

    /** Undoes a claim whose settlement failed, so the next delivery can try again. */
    void release(UUID paymentId);
}
