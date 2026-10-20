package dev.natan.payments.consumer;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.sqs.model.Message;

class DeadLetterQueueTest extends ConsumerIntegrationTest {

    @Test
    void messageThatNeverSettlesMovesToTheDlqAfterMaxReceives() {
        String poison = "{\"paymentId\": \"not-a-uuid\", \"marker\": \"" + UUID.randomUUID() + "\"}";

        sqsClient.sendMessage(send -> send.queueUrl(queueUrl(PAYMENTS_QUEUE)).messageBody(poison)).join();

        // maxReceiveCount = 3 and visibility timeout = 10s, so it takes ~30s to give up on the message.
        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(2))
                .until(() -> deadLetters().contains(poison));
    }

    private List<String> deadLetters() {
        return sqsClient.receiveMessage(receive -> receive
                        .queueUrl(queueUrl(DEAD_LETTER_QUEUE))
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1))
                .join()
                .messages()
                .stream()
                .map(Message::body)
                .toList();
    }
}
