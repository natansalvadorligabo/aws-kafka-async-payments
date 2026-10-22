package dev.natan.payments.settlement;

import java.util.UUID;

/**
 * Another delivery of the same payment holds a live claim. Thrown instead of ignoring the message, so the queue
 * redelivers it: if that other delivery dies, this one settles the payment after the lease expires.
 */
public class SettlementInProgressException extends RuntimeException {

    public SettlementInProgressException(UUID paymentId) {
        super("payment " + paymentId + " is being settled by another delivery; expected it to be free, retry later");
    }
}
