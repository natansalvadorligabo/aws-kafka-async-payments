package dev.natan.payments.dashboard;

import java.time.Instant;
import java.util.UUID;

/**
 * One step a payment took through the stack, as the dashboard observed it.
 *
 * @param paymentId null when the message has no readable paymentId (a poison message)
 */
public record FlowEvent(long sequence, Instant at, Type type, UUID paymentId, String detail) {

    public enum Type {
        ACCEPTED,
        DUPLICATE_SENT,
        POISON_SENT,
        SETTLED,
        DUPLICATE_IGNORED,
        DEAD_LETTERED,
        CONSUMER_STOPPED,
        CONSUMER_STARTED
    }
}
