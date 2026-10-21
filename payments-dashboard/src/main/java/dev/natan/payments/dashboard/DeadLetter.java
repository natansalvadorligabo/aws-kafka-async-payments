package dev.natan.payments.dashboard;

/** A message SQS moved to the DLQ after maxReceiveCount failed receives. */
public record DeadLetter(String messageId, String body) {
}
