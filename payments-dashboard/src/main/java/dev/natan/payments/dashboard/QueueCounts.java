package dev.natan.payments.dashboard;

/** Message counts SQS and DynamoDB expose; SQS offers no per-message view of a queue. */
public record QueueCounts(int waiting, int inFlight, int deadLetters, int processed) {
}
