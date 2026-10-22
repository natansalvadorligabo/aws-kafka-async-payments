package dev.natan.payments.settlement;

import static dev.natan.payments.settlement.ProcessedPayments.ClaimOutcome.ALREADY_SETTLED;
import static dev.natan.payments.settlement.ProcessedPayments.ClaimOutcome.CLAIMED;
import static dev.natan.payments.settlement.ProcessedPayments.ClaimOutcome.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.natan.payments.testsupport.PaymentsLocalStack;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** The lease rules live in a DynamoDB condition expression, so they are checked against DynamoDB itself. */
class DynamoDbProcessedPaymentsTest {

    private static final String TABLE = "processed-payments";
    private static final Duration LEASE = Duration.ofSeconds(8);

    private final DynamoDbClient dynamoDb = PaymentsLocalStack.client(DynamoDbClient.builder());
    private final FakeClock clock = new FakeClock(Instant.now());
    private final DynamoDbProcessedPayments processedPayments =
            new DynamoDbProcessedPayments(dynamoDb, TABLE, clock, LEASE);
    private final UUID paymentId = UUID.randomUUID();

    @Test
    void firstClaimWinsAndASecondOneFindsItInProgress() {
        assertThat(processedPayments.claim(paymentId)).isEqualTo(CLAIMED);
        assertThat(processedPayments.claim(paymentId)).isEqualTo(IN_PROGRESS);
    }

    @Test
    void abandonedClaimIsTakenOverOnceTheLeaseExpires() {
        processedPayments.claim(paymentId);

        clock.advance(LEASE.plusMillis(1));

        assertThat(processedPayments.claim(paymentId)).isEqualTo(CLAIMED);
    }

    @Test
    void settledPaymentIsADuplicateForever() {
        processedPayments.claim(paymentId);
        processedPayments.markSettled(paymentId);

        clock.advance(LEASE.multipliedBy(100));

        assertThat(processedPayments.claim(paymentId)).isEqualTo(ALREADY_SETTLED);
    }

    @Test
    void releasedClaimCanBeTakenRightAway() {
        processedPayments.claim(paymentId);
        processedPayments.release(paymentId);

        assertThat(processedPayments.claim(paymentId)).isEqualTo(CLAIMED);
    }

    @Test
    void itemWrittenBeforeClaimsHadAStatusCountsAsSettled() {
        dynamoDb.putItem(put -> put.tableName(TABLE)
                .item(Map.of("paymentId", AttributeValue.fromS(paymentId.toString()))));

        assertThat(processedPayments.claim(paymentId)).isEqualTo(ALREADY_SETTLED);
    }
}
