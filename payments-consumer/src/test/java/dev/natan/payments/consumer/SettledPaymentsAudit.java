package dev.natan.payments.consumer;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.natan.payments.contract.PaymentSettled;
import io.awspring.cloud.sqs.operations.SqsTemplate;

/** Counts PaymentSettled notifications by draining the SQS queue subscribed to the SNS topic. */
class SettledPaymentsAudit {

    private static final String AUDIT_QUEUE = "payment-settled-audit";

    private final SqsTemplate sqsTemplate;
    private final Map<UUID, Integer> settlements = new ConcurrentHashMap<>();

    SettledPaymentsAudit(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    int settlementsOf(UUID paymentId) {
        drain();
        return settlements.getOrDefault(paymentId, 0);
    }

    private void drain() {
        sqsTemplate.receiveMany(
                        from -> from.queue(AUDIT_QUEUE).maxNumberOfMessages(10).pollTimeout(Duration.ofSeconds(1)),
                        PaymentSettled.class)
                .forEach(message -> settlements.merge(message.getPayload().paymentId(), 1, Integer::sum));
    }
}
