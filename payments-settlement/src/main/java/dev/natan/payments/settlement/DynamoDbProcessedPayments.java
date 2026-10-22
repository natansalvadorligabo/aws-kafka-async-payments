package dev.natan.payments.settlement;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;

/**
 * {@link ProcessedPayments} on a DynamoDB table keyed by paymentId: {@code status} is PENDING or DONE, and a PENDING
 * item carries {@code leaseUntil} (epoch millis).
 *
 * <p>The lease is enforced by the condition expression, not by DynamoDB TTL: TTL deletes items up to ~48h late, so it
 * can only clean up, never decide who owns a claim.
 */
public class DynamoDbProcessedPayments implements ProcessedPayments {

    private static final String PENDING = "PENDING";
    private static final String DONE = "DONE";

    private final DynamoDbClient dynamoDb;
    private final String tableName;
    private final Clock clock;
    private final Duration lease;

    public DynamoDbProcessedPayments(DynamoDbClient dynamoDb, String tableName, Clock clock, Duration lease) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
        this.clock = clock;
        this.lease = lease;
    }

    @Override
    public ClaimOutcome claim(UUID paymentId) {
        long now = clock.millis();
        try {
            dynamoDb.putItem(put -> put
                    .tableName(tableName)
                    .item(Map.of(
                            "paymentId", AttributeValue.fromS(paymentId.toString()),
                            "status", AttributeValue.fromS(PENDING),
                            "leaseUntil", AttributeValue.fromN(Long.toString(now + lease.toMillis()))))
                    .conditionExpression(
                            "attribute_not_exists(paymentId) OR (#status = :pending AND leaseUntil < :now)")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(
                            ":pending", AttributeValue.fromS(PENDING),
                            ":now", AttributeValue.fromN(Long.toString(now))))
                    .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD));
            return ClaimOutcome.CLAIMED;
        } catch (ConditionalCheckFailedException held) {
            return isSettled(held.hasItem() ? held.item() : currentItem(paymentId))
                    ? ClaimOutcome.ALREADY_SETTLED
                    : ClaimOutcome.IN_PROGRESS;
        }
    }

    @Override
    public void markSettled(UUID paymentId) {
        dynamoDb.updateItem(update -> update
                .tableName(tableName)
                .key(key(paymentId))
                .updateExpression("SET #status = :done REMOVE leaseUntil")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(":done", AttributeValue.fromS(DONE))));
    }

    @Override
    public void release(UUID paymentId) {
        dynamoDb.deleteItem(delete -> delete.tableName(tableName).key(key(paymentId)));
    }

    // Items written before claims had a status were only ever written for settled payments.
    private static boolean isSettled(Map<String, AttributeValue> item) {
        AttributeValue status = item.get("status");
        return status == null || DONE.equals(status.s());
    }

    private Map<String, AttributeValue> currentItem(UUID paymentId) {
        return dynamoDb.getItem(get -> get.tableName(tableName).key(key(paymentId)).consistentRead(true)).item();
    }

    private static Map<String, AttributeValue> key(UUID paymentId) {
        return Map.of("paymentId", AttributeValue.fromS(paymentId.toString()));
    }
}
