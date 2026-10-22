package dev.natan.payments.api;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import dev.natan.payments.contract.PaymentRequested;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes on the payments topic, keyed by accountId so every payment of an account lands on the same partition and
 * keeps its order. Waits for the broker's acknowledgement (acks=all), so a 202 means the same thing as with SQS.
 */
@Component
@ConditionalOnProperty(name = "payments.broker", havingValue = "kafka")
class KafkaPaymentQueue implements PaymentQueue {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper json;
    private final String topic;

    KafkaPaymentQueue(KafkaTemplate<String, String> kafkaTemplate, JsonMapper json,
            @Value("${payments.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.json = json;
        this.topic = topic;
    }

    @Override
    public void enqueue(PaymentRequested payment) {
        try {
            kafkaTemplate.send(topic, payment.accountId(), json.writeValueAsString(payment))
                    .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(
                    "expected Kafka to acknowledge payment " + payment.paymentId() + " within " + SEND_TIMEOUT, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while publishing payment " + payment.paymentId(), e);
        }
    }
}
