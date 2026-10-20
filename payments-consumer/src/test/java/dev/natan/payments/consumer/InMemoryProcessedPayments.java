package dev.natan.payments.consumer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake {@link ProcessedPayments} backed by a concurrent set. */
class InMemoryProcessedPayments implements ProcessedPayments {

    private final Set<UUID> claimed = ConcurrentHashMap.newKeySet();

    @Override
    public boolean claim(UUID paymentId) {
        return claimed.add(paymentId);
    }

    @Override
    public void release(UUID paymentId) {
        claimed.remove(paymentId);
    }

    boolean isClaimed(UUID paymentId) {
        return claimed.contains(paymentId);
    }
}
