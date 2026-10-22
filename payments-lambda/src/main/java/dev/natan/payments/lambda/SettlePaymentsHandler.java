package dev.natan.payments.lambda;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse.BatchItemFailure;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;

import dev.natan.payments.contract.PaymentRequested;
import dev.natan.payments.settlement.DynamoDbProcessedPayments;
import dev.natan.payments.settlement.PaymentSettlement;
import dev.natan.payments.settlement.SnsSettlementNotifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sns.SnsClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Settles a batch of queued payments. Each record that fails is reported back, so SQS redelivers only that one
 * (ReportBatchItemFailures) and moves it to the DLQ after maxReceiveCount attempts.
 *
 * <pre>{@code
 * SQSBatchResponse response = new SettlePaymentsHandler().handleRequest(sqsEvent, context);
 * }</pre>
 */
public class SettlePaymentsHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final Logger log = LoggerFactory.getLogger(SettlePaymentsHandler.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PaymentSettlement paymentSettlement;
    private final JsonMapper json;

    /** Called by the Lambda runtime. AWS clients pick region and endpoint up from the environment. */
    public SettlePaymentsHandler() {
        this(new PaymentSettlement(
                new DynamoDbProcessedPayments(DynamoDbClient.create(), requiredEnv("PROCESSED_TABLE"),
                        Clock.systemUTC(), Duration.ofSeconds(Long.parseLong(requiredEnv("CLAIM_LEASE_SECONDS")))),
                new SnsSettlementNotifier(SnsClient.create(), requiredEnv("SETTLED_TOPIC_ARN"), JSON),
                Clock.systemUTC()), JSON);
    }

    SettlePaymentsHandler(PaymentSettlement paymentSettlement, JsonMapper json) {
        this.paymentSettlement = paymentSettlement;
        this.json = json;
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<BatchItemFailure> failures = new ArrayList<>();
        for (SQSMessage message : event.getRecords()) {
            try {
                paymentSettlement.settle(json.readValue(message.getBody(), PaymentRequested.class));
            } catch (RuntimeException e) {
                log.atWarn().addKeyValue("messageId", message.getMessageId()).setCause(e).log("settlement failed");
                failures.add(new BatchItemFailure(message.getMessageId()));
            }
        }
        return new SQSBatchResponse(failures);
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("expected environment variable " + name + " to be set, got " + value);
        }
        return value;
    }
}
