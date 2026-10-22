package dev.natan.payments.dashboard;

/**
 * What the stack looks like right now, as AWS reports it.
 *
 * @param waiting        visible messages on the payments queue: nobody has received them yet
 * @param inFlight       received but not deleted: being processed, or waiting out the visibility timeout
 * @param deadLetters    messages on the DLQ
 * @param processed      payments marked DONE in the processed-payments table
 * @param pendingClaims  PENDING claims: a delivery is settling them, or died and left them until the lease expires
 * @param consumer       which consumer settles the queue: "lambda" or "spring"
 * @param consumerUp     whether that consumer is currently taking messages
 */
public record StackState(
        int waiting,
        int inFlight,
        int deadLetters,
        int processed,
        int pendingClaims,
        String consumer,
        boolean consumerUp) {
}
