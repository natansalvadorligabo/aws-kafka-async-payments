package dev.natan.payments.api;

import dev.natan.payments.contract.PaymentRequested;

/**
 * Durable hand-off between the API and the consumers. Once {@link #enqueue} returns, the payment survives
 * consumer crashes.
 *
 * <pre>{@code
 * paymentQueue.enqueue(payment);
 * }</pre>
 */
public interface PaymentQueue {

    void enqueue(PaymentRequested payment);
}
