package dev.natan.payments.consumer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.localstack.LocalStackContainer;

import dev.natan.payments.contract.PaymentRequested;
import dev.natan.payments.testsupport.PaymentsLocalStack;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * All consumer integration tests share this one Spring context, so a single listener polls the queue. Separate
 * contexts would keep their listeners alive in the context cache and compete for the same messages.
 */
@SpringBootTest
@Import(ConsumerIntegrationTest.Doubles.class)
abstract class ConsumerIntegrationTest {

    static final String PAYMENTS_QUEUE = "payments";
    static final String DEAD_LETTER_QUEUE = "payments-dlq";

    @ServiceConnection
    static LocalStackContainer localStack = PaymentsLocalStack.shared();

    @TestConfiguration
    static class Doubles {

        @Bean
        @Primary
        FaultInjectingNotifier faultInjectingNotifier(SnsSettlementNotifier snsSettlementNotifier) {
            return new FaultInjectingNotifier(snsSettlementNotifier);
        }

        @Bean
        SettledPaymentsAudit settledPaymentsAudit(SqsTemplate sqsTemplate) {
            return new SettledPaymentsAudit(sqsTemplate);
        }
    }

    @Autowired
    SqsTemplate sqsTemplate;

    @Autowired
    SqsAsyncClient sqsClient;

    @Autowired
    SettledPaymentsAudit audit;

    @Autowired
    FaultInjectingNotifier notifier;

    static PaymentRequested newPayment() {
        return new PaymentRequested(
                UUID.randomUUID(), "acc-1", "alice@example.com", new BigDecimal("25.00"), Instant.now());
    }

    String queueUrl(String queueName) {
        return sqsClient.getQueueUrl(get -> get.queueName(queueName)).join().queueUrl();
    }

    /** Visible plus in-flight messages: zero means every delivery was deleted. */
    int messagesOn(String queueName) {
        Map<QueueAttributeName, String> attributes = queueAttributes(queueName,
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE);
        return attributes.values().stream().mapToInt(Integer::parseInt).sum();
    }

    Duration visibilityTimeoutOf(String queueName) {
        String seconds = queueAttributes(queueName, QueueAttributeName.VISIBILITY_TIMEOUT)
                .get(QueueAttributeName.VISIBILITY_TIMEOUT);
        return Duration.ofSeconds(Long.parseLong(seconds));
    }

    private Map<QueueAttributeName, String> queueAttributes(String queueName, QueueAttributeName... names) {
        return sqsClient.getQueueAttributes(get -> get.queueUrl(queueUrl(queueName)).attributeNames(names))
                .join()
                .attributes();
    }
}
