package dev.natan.payments.dashboard;

import java.util.UUID;

/**
 * Feeds the stack the inputs each scenario needs.
 *
 * <pre>{@code
 * UUID paymentId = paymentInjector.sendPayment();
 * }</pre>
 */
public interface PaymentInjector {

    /** Through the API, like a real client; returns the id from the 202 answer. */
    UUID sendPayment();

    /** Straight onto SQS, twice, as an at-least-once redelivery would; returns the shared paymentId. */
    UUID sendDuplicate();

    /** Straight onto SQS: a body no consumer can parse. */
    void sendPoison();
}
