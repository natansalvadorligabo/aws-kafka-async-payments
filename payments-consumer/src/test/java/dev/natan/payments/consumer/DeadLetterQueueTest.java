package dev.natan.payments.consumer;

import static dev.natan.payments.testsupport.PaymentQueues.DEAD_LETTERS;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DeadLetterQueueTest extends ConsumerIntegrationTest {

    @Test
    void messageThatNeverSettlesMovesToTheDlqAfterMaxReceives() {
        String poison = "{\"paymentId\": \"not-a-uuid\", \"marker\": \"" + UUID.randomUUID() + "\"}";

        queues.sendRaw(poison);

        // maxReceiveCount = 3 and visibility timeout = 10s, so it takes ~30s to give up on the message.
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(2))
                .until(() -> queues.bodiesOn(DEAD_LETTERS).contains(poison));
    }
}
