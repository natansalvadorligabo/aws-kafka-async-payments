package dev.natan.payments.consumer;

import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import dev.natan.payments.contract.PaymentRequested;

class IdempotentSettlementTest extends ConsumerIntegrationTest {

    @Test
    void theSamePaymentDeliveredTwiceIsSettledOnce() {
        PaymentRequested payment = newPayment();

        sqsTemplate.send(PAYMENTS_QUEUE, payment);
        sqsTemplate.send(PAYMENTS_QUEUE, payment);

        await().atMost(Duration.ofSeconds(30)).until(() -> audit.settlementsOf(payment.paymentId()) == 1);
        await().atMost(Duration.ofSeconds(30)).until(() -> messagesOn(PAYMENTS_QUEUE) == 0);
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
                .until(() -> audit.settlementsOf(payment.paymentId()) == 1);
    }
}
