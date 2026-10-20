package dev.natan.payments.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.natan.payments.contract.PaymentRequested;
import io.awspring.cloud.sqs.listener.MessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;

class NoPaymentLostTest extends ConsumerIntegrationTest {

    private static final int PAYMENTS = 20;

    @Autowired
    MessageListenerContainerRegistry listeners;

    @Test
    void paymentsQueuedWhileTheConsumerIsDownAreSettledWhenItComesBack() {
        MessageListenerContainer<?> consumer = listeners.getContainerById(PaymentListener.CONTAINER_ID);
        List<PaymentRequested> payments = Stream.generate(ConsumerIntegrationTest::newPayment).limit(PAYMENTS).toList();

        consumer.stop();
        try {
            payments.forEach(payment -> sqsTemplate.send(PAYMENTS_QUEUE, payment));
            assertThat(messagesOn(PAYMENTS_QUEUE)).isGreaterThanOrEqualTo(PAYMENTS);
        } finally {
            consumer.start();
        }

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(payments)
                .allSatisfy(payment -> assertThat(audit.settlementsOf(payment.paymentId())).isEqualTo(1)));
    }
}
