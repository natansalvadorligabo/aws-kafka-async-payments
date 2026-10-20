package dev.natan.payments.consumer;

import static dev.natan.payments.testsupport.PaymentQueues.PAYMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.natan.payments.contract.PaymentRequested;
import dev.natan.payments.testsupport.PaymentQueues;
import io.awspring.cloud.sqs.listener.MessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;

class NoPaymentLostTest extends ConsumerIntegrationTest {

    private static final int PAYMENTS_SENT = 20;

    @Autowired
    MessageListenerContainerRegistry listeners;

    @Test
    void paymentsQueuedWhileTheConsumerIsDownAreSettledWhenItComesBack() {
        MessageListenerContainer<?> consumer = listeners.getContainerById(PaymentListener.CONTAINER_ID);
        List<PaymentRequested> payments = Stream.generate(PaymentQueues::newPayment).limit(PAYMENTS_SENT).toList();

        consumer.stop();
        try {
            payments.forEach(queues::send);
            assertThat(queues.messagesOn(PAYMENTS)).isGreaterThanOrEqualTo(PAYMENTS_SENT);
        } finally {
            consumer.start();
        }

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(payments)
                .allSatisfy(payment -> assertThat(audit.settlementsOf(payment.paymentId())).isEqualTo(1)));
    }
}
