package dev.natan.payments.settlement;

import java.util.UUID;

/**
 * Remembers which payments were taken and which were settled, so a redelivered message is not settled twice and an
 * abandoned claim does not block its payment forever.
 *
 * <p>A claim is {@code PENDING} with a lease, then {@code DONE}. A process that dies while holding a claim leaves it
 * {@code PENDING}; once the lease expires, the next delivery takes it over.
 *
 * <pre>{@code
 * switch (processedPayments.claim(paymentId)) {
 *     case CLAIMED -> { settle; processedPayments.markSettled(paymentId); }
 *     case ALREADY_SETTLED -> { ignore the duplicate }
 *     case IN_PROGRESS -> { fail, so the message is retried later }
 * }
 * }</pre>
 */
public interface ProcessedPayments {

    /**
     * Atomically takes the payment if nobody holds it, or if its holder's lease expired.
     * Breaks command/query separation on purpose: check-and-set must be one atomic call.
     */
    ClaimOutcome claim(UUID paymentId);

    /** Turns this delivery's claim into a permanent record: later deliveries are duplicates. */
    void markSettled(UUID paymentId);

    /** Undoes a claim whose settlement failed, so the next delivery can try again without waiting for the lease. */
    void release(UUID paymentId);

    enum ClaimOutcome {
        CLAIMED,
        ALREADY_SETTLED,
        IN_PROGRESS
    }
}
