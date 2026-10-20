package dev.natan.payments.consumer;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Component
class DynamoDbProcessedPayments implements ProcessedPayments {

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    DynamoDbProcessedPayments(DynamoDbClient dynamoDb, @Value("${payments.processed-table}") String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    @Override
    public boolean claim(UUID paymentId) {
        try {
            dynamoDb.putItem(put -> put
                    .tableName(tableName)
                    .item(key(paymentId))
                    .conditionExpression("attribute_not_exists(paymentId)"));
            return true;
        } catch (ConditionalCheckFailedException alreadyClaimed) {
            return false;
        }
    }

    @Override
    public void release(UUID paymentId) {
        dynamoDb.deleteItem(delete -> delete.tableName(tableName).key(key(paymentId)));
    }

    private static Map<String, AttributeValue> key(UUID paymentId) {
        return Map.of("paymentId", AttributeValue.fromS(paymentId.toString()));
    }
}
