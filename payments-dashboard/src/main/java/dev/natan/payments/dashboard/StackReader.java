package dev.natan.payments.dashboard;

import java.util.List;

import dev.natan.payments.contract.PaymentSettled;

/**
 * Observes the running stack without taking part in it.
 *
 * <pre>{@code
 * QueueCounts counts = stackReader.counts();
 * }</pre>
 */
public interface StackReader {

    QueueCounts counts();

    /** Removes and returns the PaymentSettled notifications SNS fanned out since the last call. */
    List<PaymentSettled> drainSettled();

    /** Dead letters not returned by an earlier call; they stay on the DLQ. */
    List<DeadLetter> pollNewDeadLetters();
}
