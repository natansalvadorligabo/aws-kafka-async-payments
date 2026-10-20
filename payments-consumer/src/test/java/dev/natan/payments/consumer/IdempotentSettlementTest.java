package dev.natan.payments.consumer;

import static dev.natan.payments.testsupport.PaymentQueues.PAYMENTS;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import dev.natan.payments.contract.PaymentRequested;
import dev.natan.payments.testsupport.PaymentQueues;

class IdempotentSettlementTest extends ConsumerIntegrationTest {

    @Test
    void theSamePaymentDeliveredTwiceIsSettledOnce() {
        PaymentRequested payment = PaymentQueues.newPayment();

        queues.send(payment);
        queues.send(payment);

        await().atMost(Duration.ofSeconds(30)).until(() -> audit.settlementsOf(payment.paymentId()) == 1);
        await().atMost(Duration.ofSeconds(30)).until(() -> queues.messagesOn(PAYMENTS) == 0);
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
                .until(() -> audit.settlementsOf(payment.paymentId()) == 1);
    }
}
