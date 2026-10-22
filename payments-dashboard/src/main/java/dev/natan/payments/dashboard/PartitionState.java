package dev.natan.payments.dashboard;

/** A partition of the payments topic and the offset its next record will get (its log end). */
public record PartitionState(int partition, long endOffset) {
}
