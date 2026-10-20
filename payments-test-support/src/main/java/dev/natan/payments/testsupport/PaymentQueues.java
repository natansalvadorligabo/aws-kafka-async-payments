package dev.natan.payments.testsupport;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.natan.payments.contract.PaymentRequested;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drives and inspects the payment queues the way the API and an operator would.
 *
 * <pre>{@code
 * PaymentQueues queues = new PaymentQueues(PaymentsLocalStack.client(SqsClient.builder()));
 * queues.send(PaymentQueues.newPayment());
 * }</pre>
 */
public class PaymentQueues {

    public static final String PAYMENTS = "payments";
    public static final String DEAD_LETTERS = "payments-dlq";

    private final SqsClient sqs;
    private final JsonMapper json = JsonMapper.builder().build();

    public PaymentQueues(SqsClient sqs) {
        this.sqs = sqs;
    }

    public static PaymentRequested newPayment() {
        return new PaymentRequested(
                UUID.randomUUID(), "acc-1", "alice@example.com", new BigDecimal("25.00"), Instant.now());
    }

    public void send(PaymentRequested payment) {
        sendRaw(json.writeValueAsString(payment));
    }

    public void sendRaw(String body) {
        sqs.sendMessage(send -> send.queueUrl(urlOf(PAYMENTS)).messageBody(body));
    }

    /** Visible plus in-flight messages: zero means every delivery was deleted. */
    public int messagesOn(String queueName) {
        return attributesOf(queueName,
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                .values().stream().mapToInt(Integer::parseInt).sum();
    }

    public Duration visibilityTimeoutOf(String queueName) {
        String seconds = attributesOf(queueName, QueueAttributeName.VISIBILITY_TIMEOUT)
                .get(QueueAttributeName.VISIBILITY_TIMEOUT);
        return Duration.ofSeconds(Long.parseLong(seconds));
    }

    /** Peeks at up to ten messages; they become visible again after the queue's visibility timeout. */
    public List<String> bodiesOn(String queueName) {
        return sqs.receiveMessage(receive -> receive.queueUrl(urlOf(queueName)).maxNumberOfMessages(10).waitTimeSeconds(1))
                .messages().stream().map(Message::body).toList();
    }

    private Map<QueueAttributeName, String> attributesOf(String queueName, QueueAttributeName... names) {
        return sqs.getQueueAttributes(get -> get.queueUrl(urlOf(queueName)).attributeNames(names)).attributes();
    }

    private String urlOf(String queueName) {
        return sqs.getQueueUrl(get -> get.queueName(queueName)).queueUrl();
    }
}
