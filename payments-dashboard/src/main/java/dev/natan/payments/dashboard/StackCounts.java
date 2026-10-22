package dev.natan.payments.dashboard;

/** Counts the stack exposes: SQS message counters and DynamoDB claims. SQS offers no per-message view. */
public record StackCounts(int waiting, int inFlight, int deadLetters, int settled, int pendingClaims) {
}
