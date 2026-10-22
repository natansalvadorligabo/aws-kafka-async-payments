package dev.natan.payments.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.natan.payments.contract.PaymentSettled;
import dev.natan.payments.testsupport.PaymentsLocalStack;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

/** Nothing consumes the queues here, so every count is exactly what this test put there. */
class AwsStackReaderTest {

    private final SqsClient sqs = PaymentsLocalStack.client(SqsClient.builder());
    private final JsonMapper json = JsonMapper.builder().build();
    private final DynamoDbClient dynamoDb = PaymentsLocalStack.client(DynamoDbClient.builder());
    private final AwsStackReader reader = new AwsStackReader(sqs, dynamoDb, json);

    @Test
    void countsWaitingMessages() {
        int before = reader.counts().waiting();

        send(AwsStackReader.PAYMENTS_QUEUE, "{}");

        await().atMost(Duration.ofSeconds(5)).until(() -> reader.counts().waiting() == before + 1);
    }

    @Test
    void drainsSettledNotificationsOnce() {
        PaymentSettled settled = new PaymentSettled(UUID.randomUUID(), "acc-1", BigDecimal.TEN, Instant.now());
        send(AwsStackReader.AUDIT_QUEUE, json.writeValueAsString(settled));

        await().atMost(Duration.ofSeconds(5)).until(() -> reader.drainSettled().contains(settled));
        assertThat(reader.drainSettled()).doesNotContain(settled);
    }

    @Test
    void reportsEachDeadLetterOnceAndLeavesItOnTheDlq() {
        String body = "dead " + UUID.randomUUID();
        send(AwsStackReader.DEAD_LETTER_QUEUE, body);

        await().atMost(Duration.ofSeconds(5))
                .until(() -> reader.pollNewDeadLetters().stream().anyMatch(letter -> letter.body().equals(body)));
        assertThat(reader.pollNewDeadLetters()).noneMatch(letter -> letter.body().equals(body));
        assertThat(reader.counts().deadLetters()).isPositive();
    }

    @Test
    void countsSettledPaymentsAndPendingClaimsSeparately() {
        QueueCounts before = reader.counts();

        putClaim("DONE");
        putClaim("PENDING");
        putClaim("PENDING");

        QueueCounts after = reader.counts();
        assertThat(after.processed() - before.processed()).isEqualTo(1);
        assertThat(after.pendingClaims() - before.pendingClaims()).isEqualTo(2);
    }

    private void putClaim(String status) {
        dynamoDb.putItem(put -> put.tableName(AwsStackReader.PROCESSED_TABLE).item(Map.of(
                "paymentId", AttributeValue.fromS(UUID.randomUUID().toString()),
                "status", AttributeValue.fromS(status))));
    }

    private void send(String queueName, String body) {
        String queueUrl = sqs.getQueueUrl(get -> get.queueName(queueName)).queueUrl();
        sqs.sendMessage(send -> send.queueUrl(queueUrl).messageBody(body));
    }
}
