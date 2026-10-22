package dev.natan.payments.dashboard;

import java.time.Instant;
import java.util.UUID;

/**
 * One record on the payments topic, where Kafka put it.
 *
 * @param key       the accountId: it decides the partition
 * @param paymentId null when the value is not a readable payment
 */
public record TopicRecord(int partition, long offset, String key, UUID paymentId, Instant appendedAt) {
}
