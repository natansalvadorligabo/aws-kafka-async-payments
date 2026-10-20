package dev.natan.payments.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.natan.payments.contract.PaymentRequested;

/** Fake {@link PaymentQueue} that keeps enqueued payments in memory. */
class InMemoryPaymentQueue implements PaymentQueue {

    private final List<PaymentRequested> enqueued = new CopyOnWriteArrayList<>();

    @Override
    public void enqueue(PaymentRequested payment) {
        enqueued.add(payment);
    }

    List<PaymentRequested> enqueued() {
        return List.copyOf(enqueued);
    }
}
