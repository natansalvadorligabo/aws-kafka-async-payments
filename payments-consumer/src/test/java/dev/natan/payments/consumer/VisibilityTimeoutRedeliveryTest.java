package dev.natan.payments.consumer;

import static dev.natan.payments.testsupport.PaymentQueues.PAYMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.natan.payments.contract.PaymentRequested;
import dev.natan.payments.testsupport.PaymentQueues;

class VisibilityTimeoutRedeliveryTest extends ConsumerIntegrationTest {

    // LocalStack checks visibility on a coarse timer; allow it to redeliver slightly early.
    private static final Duration CLOCK_TOLERANCE = Duration.ofSeconds(1);

    @Test
    void failedAttemptReappearsAfterTheVisibilityTimeoutAndSettles() {
        PaymentRequested payment = PaymentQueues.newPayment();
        notifier.failNextAttempts(payment.paymentId(), 1);

        queues.send(payment);

        await().atMost(Duration.ofSeconds(45)).until(() -> audit.settlementsOf(payment.paymentId()) == 1);
        List<Instant> attempts = notifier.attemptsOf(payment.paymentId());
        assertThat(attempts).hasSize(2);
        assertThat(Duration.between(attempts.get(0), attempts.get(1)))
                .isGreaterThanOrEqualTo(queues.visibilityTimeoutOf(PAYMENTS).minus(CLOCK_TOLERANCE));
    }
}
