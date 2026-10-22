package dev.natan.payments.consumer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import dev.natan.payments.contract.PaymentSettled;
import dev.natan.payments.settlement.SettlementNotifier;
import dev.natan.payments.settlement.SimulatedCrash;

/** Decorates the real notifier: fails or crashes chosen payments and records every attempt. */
class FaultInjectingNotifier implements SettlementNotifier {

    private final SettlementNotifier delegate;
    private final Map<UUID, AtomicInteger> failuresLeft = new ConcurrentHashMap<>();
    private final Set<UUID> crashing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<Instant>> attempts = new ConcurrentHashMap<>();

    FaultInjectingNotifier(SettlementNotifier delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(PaymentSettled settled) {
        UUID paymentId = settled.paymentId();
        attempts.computeIfAbsent(paymentId, id -> new CopyOnWriteArrayList<>()).add(Instant.now());
        if (crashing.remove(paymentId)) {
            throw new SimulatedCrash("consumer died while settling payment " + paymentId);
        }
        AtomicInteger left = failuresLeft.get(paymentId);
        if (left != null && left.getAndDecrement() > 0) {
            throw new IllegalStateException("injected failure for payment " + paymentId);
        }
        delegate.publish(settled);
    }

    void failNextAttempts(UUID paymentId, int times) {
        failuresLeft.put(paymentId, new AtomicInteger(times));
    }

    /** The next attempt dies like a killed process: no catch block runs, so its claim is left behind. */
    void crashNextAttemptOf(UUID paymentId) {
        crashing.add(paymentId);
    }

    List<Instant> attemptsOf(UUID paymentId) {
        return List.copyOf(attempts.getOrDefault(paymentId, List.of()));
    }
}
