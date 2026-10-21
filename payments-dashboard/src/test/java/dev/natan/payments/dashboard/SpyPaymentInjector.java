package dev.natan.payments.dashboard;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Spy {@link PaymentInjector}: counts what each scenario asked for, without touching a stack. */
class SpyPaymentInjector implements PaymentInjector {

    final AtomicInteger payments = new AtomicInteger();
    final AtomicInteger duplicates = new AtomicInteger();
    final AtomicInteger poisons = new AtomicInteger();

    @Override
    public UUID sendPayment() {
        payments.incrementAndGet();
        return UUID.randomUUID();
    }

    @Override
    public UUID sendDuplicate() {
        duplicates.incrementAndGet();
        return UUID.randomUUID();
    }

    @Override
    public void sendPoison() {
        poisons.incrementAndGet();
    }
}
