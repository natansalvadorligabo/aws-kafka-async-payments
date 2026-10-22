package dev.natan.payments.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import dev.natan.payments.contract.PaymentRequested;
import dev.natan.payments.testsupport.PaymentQueues;

/** Regression: a consumer dying between claim and publish used to leave a claim that made the payment unsettleable. */
class CrashMidSettlementTest extends ConsumerIntegrationTest {

    @Test
    void paymentWhoseConsumerDiedMidSettlementIsSettledByTheRedelivery() {
        PaymentRequested payment = PaymentQueues.newPayment();
        notifier.crashNextAttemptOf(payment.paymentId());

        queues.send(payment);

        await().atMost(Duration.ofSeconds(60)).until(() -> audit.settlementsOf(payment.paymentId()) == 1);
        assertThat(notifier.attemptsOf(payment.paymentId())).hasSize(2);
    }
}
