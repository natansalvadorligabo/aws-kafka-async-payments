package dev.natan.payments.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse.BatchItemFailure;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;

import dev.natan.payments.contract.PaymentRequested;
import dev.natan.payments.settlement.InMemoryProcessedPayments;
import dev.natan.payments.settlement.PaymentSettlement;
import dev.natan.payments.settlement.SpySettlementNotifier;
import dev.natan.payments.testsupport.PaymentQueues;
import tools.jackson.databind.json.JsonMapper;

class SettlePaymentsHandlerTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final SpySettlementNotifier notifier = new SpySettlementNotifier();
    private final SettlePaymentsHandler handler = new SettlePaymentsHandler(
            new PaymentSettlement(new InMemoryProcessedPayments(), notifier, Clock.systemUTC()), json);

    @Test
    void reportsOnlyTheRecordThatFailed() {
        PaymentRequested payment = PaymentQueues.newPayment();
        SQSEvent batch = batchOf(message("ok", json.writeValueAsString(payment)), message("poison", "not json"));

        SQSBatchResponse response = handler.handleRequest(batch, null);

        assertThat(response.getBatchItemFailures()).extracting(BatchItemFailure::getItemIdentifier)
                .containsExactly("poison");
        assertThat(notifier.published()).singleElement()
                .satisfies(settled -> assertThat(settled.paymentId()).isEqualTo(payment.paymentId()));
    }

    private static SQSEvent batchOf(SQSMessage... messages) {
        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(messages));
        return event;
    }

    private static SQSMessage message(String messageId, String body) {
        SQSMessage message = new SQSMessage();
        message.setMessageId(messageId);
        message.setBody(body);
        return message;
    }
}
