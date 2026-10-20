package dev.natan.payments.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.natan.payments.contract.PaymentRequested;

class VisibilityTimeoutRedeliveryTest extends ConsumerIntegrationTest {

    // LocalStack checks visibility on a coarse timer; allow it to redeliver slightly early.
    private static final Duration CLOCK_TOLERANCE = Duration.ofSeconds(1);

    @Test
    void failedAttemptReappearsAfterTheVisibilityTimeoutAndSettles() {
        PaymentRequested payment = newPayment();
        notifier.failNextAttempts(payment.paymentId(), 1);

        sqsTemplate.send(PAYMENTS_QUEUE, payment);

        await().atMost(Duration.ofSeconds(45)).until(() -> audit.settlementsOf(payment.paymentId()) == 1);
        List<Instant> attempts = notifier.attemptsOf(payment.paymentId());
        assertThat(attempts).hasSize(2);
        assertThat(Duration.between(attempts.get(0), attempts.get(1)))
                .isGreaterThanOrEqualTo(visibilityTimeoutOf(PAYMENTS_QUEUE).minus(CLOCK_TOLERANCE));
    }
}
