package dev.natan.payments.testsupport;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.natan.payments.contract.PaymentSettled;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.databind.json.JsonMapper;

/**
 * Counts PaymentSettled notifications by draining the SQS queue subscribed to the SNS topic.
 *
 * <pre>{@code
 * await().until(() -> audit.settlementsOf(payment.paymentId()) == 1);
 * }</pre>
 */
public class SettledPaymentsAudit {

    private static final String AUDIT_QUEUE = "payment-settled-audit";

    private final SqsClient sqs;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<UUID, Integer> settlements = new ConcurrentHashMap<>();

    public SettledPaymentsAudit(SqsClient sqs) {
        this.sqs = sqs;
    }

    public int settlementsOf(UUID paymentId) {
        drain();
        return settlements.getOrDefault(paymentId, 0);
    }

    private void drain() {
        String queueUrl = sqs.getQueueUrl(get -> get.queueName(AUDIT_QUEUE)).queueUrl();
        for (Message message : sqs.receiveMessage(receive -> receive.queueUrl(queueUrl).maxNumberOfMessages(10).waitTimeSeconds(1)).messages()) {
            PaymentSettled settled = json.readValue(message.body(), PaymentSettled.class);
            settlements.merge(settled.paymentId(), 1, Integer::sum);
            sqs.deleteMessage(delete -> delete.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
        }
    }
}
