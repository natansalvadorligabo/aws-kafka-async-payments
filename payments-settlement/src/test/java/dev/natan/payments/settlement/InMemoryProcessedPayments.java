package dev.natan.payments.settlement;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Fake {@link ProcessedPayments} with the same PENDING/lease/DONE rules as the DynamoDB table, kept in memory. */
public class InMemoryProcessedPayments implements ProcessedPayments {

    private final Clock clock;
    private final Duration lease;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public InMemoryProcessedPayments(Clock clock, Duration lease) {
        this.clock = clock;
        this.lease = lease;
    }

    @Override
    public ClaimOutcome claim(UUID paymentId) {
        Instant now = clock.instant();
        AtomicReference<ClaimOutcome> outcome = new AtomicReference<>(ClaimOutcome.CLAIMED);
        entries.compute(paymentId, (id, entry) -> {
            if (entry == null || (!entry.settled() && entry.leaseUntil().isBefore(now))) {
                return new Entry(false, now.plus(lease));
            }
            outcome.set(entry.settled() ? ClaimOutcome.ALREADY_SETTLED : ClaimOutcome.IN_PROGRESS);
            return entry;
        });
        return outcome.get();
    }

    @Override
    public void markSettled(UUID paymentId) {
        entries.put(paymentId, new Entry(true, null));
    }

    @Override
    public void release(UUID paymentId) {
        entries.remove(paymentId);
    }

    public boolean isClaimed(UUID paymentId) {
        return entries.containsKey(paymentId);
    }

    private record Entry(boolean settled, Instant leaseUntil) {
    }
}
