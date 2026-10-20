package dev.natan.payments.consumer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import dev.natan.payments.contract.PaymentSettled;

/** Decorates the real notifier: fails chosen payments a given number of times and records every attempt. */
class FaultInjectingNotifier implements SettlementNotifier {

    private final SettlementNotifier delegate;
    private final Map<UUID, AtomicInteger> failuresLeft = new ConcurrentHashMap<>();
    private final Map<UUID, List<Instant>> attempts = new ConcurrentHashMap<>();

    FaultInjectingNotifier(SettlementNotifier delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(PaymentSettled settled) {
        UUID paymentId = settled.paymentId();
        attempts.computeIfAbsent(paymentId, id -> new CopyOnWriteArrayList<>()).add(Instant.now());
        AtomicInteger left = failuresLeft.get(paymentId);
        if (left != null && left.getAndDecrement() > 0) {
            throw new IllegalStateException("injected failure for payment " + paymentId);
        }
        delegate.publish(settled);
    }

    void failNextAttempts(UUID paymentId, int times) {
        failuresLeft.put(paymentId, new AtomicInteger(times));
    }

    List<Instant> attemptsOf(UUID paymentId) {
        return List.copyOf(attempts.getOrDefault(paymentId, List.of()));
    }
}
