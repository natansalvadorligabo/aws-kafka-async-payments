package dev.natan.payments.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.natan.payments.contract.PaymentSettled;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import tools.jackson.databind.json.JsonMapper;

/** Reads queue attributes, the processed-payments table, the audit queue and the DLQ. */
class AwsStackReader implements StackReader {

    static final String PAYMENTS_QUEUE = "payments";
    static final String DEAD_LETTER_QUEUE = "payments-dlq";
    static final String AUDIT_QUEUE = "payment-settled-audit";
    static final String PROCESSED_TABLE = "processed-payments";

    private final SqsClient sqs;
    private final DynamoDbClient dynamoDb;
    private final JsonMapper json;
    private final Set<String> seenDeadLetters = ConcurrentHashMap.newKeySet();

    AwsStackReader(SqsClient sqs, DynamoDbClient dynamoDb, JsonMapper json) {
        this.sqs = sqs;
        this.dynamoDb = dynamoDb;
        this.json = json;
    }

    @Override
    public StackCounts counts() {
        Map<QueueAttributeName, String> payments = attributesOf(PAYMENTS_QUEUE);
        Map<QueueAttributeName, String> deadLetters = attributesOf(DEAD_LETTER_QUEUE);
        return new StackCounts(
                Integer.parseInt(payments.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)),
                Integer.parseInt(payments.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)),
                Integer.parseInt(deadLetters.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)),
                // Items without a status predate PENDING/DONE claims and were only written for settled payments.
                claimsMatching("#status = :status OR attribute_not_exists(#status)", "DONE"),
                claimsMatching("#status = :status", "PENDING"));
    }

    private int claimsMatching(String filter, String status) {
        return dynamoDb.scan(scan -> scan
                        .tableName(PROCESSED_TABLE)
                        .select(Select.COUNT)
                        .filterExpression(filter)
                        .expressionAttributeNames(Map.of("#status", "status"))
                        .expressionAttributeValues(Map.of(":status", AttributeValue.fromS(status))))
                .count();
    }

    @Override
    public List<PaymentSettled> drainSettled() {
        String queueUrl = urlOf(AUDIT_QUEUE);
        List<PaymentSettled> settled = new ArrayList<>();
        for (Message message : receive(queueUrl, 10)) {
            settled.add(json.readValue(message.body(), PaymentSettled.class));
            sqs.deleteMessage(delete -> delete.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
        }
        return settled;
    }

    @Override
    public List<DeadLetter> pollNewDeadLetters() {
        // Visibility 0 peeks without hiding the message, so the DLQ count stays truthful.
        return receive(urlOf(DEAD_LETTER_QUEUE), 0).stream()
                .filter(message -> seenDeadLetters.add(message.messageId()))
                .map(message -> new DeadLetter(message.messageId(), message.body()))
                .toList();
    }

    private List<Message> receive(String queueUrl, int visibilityTimeoutSeconds) {
        return sqs.receiveMessage(receive -> receive
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .visibilityTimeout(visibilityTimeoutSeconds))
                .messages();
    }

    private Map<QueueAttributeName, String> attributesOf(String queueName) {
        return sqs.getQueueAttributes(get -> get.queueUrl(urlOf(queueName)).attributeNames(
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE))
                .attributes();
    }

    private String urlOf(String queueName) {
        return sqs.getQueueUrl(get -> get.queueName(queueName)).queueUrl();
    }
}
