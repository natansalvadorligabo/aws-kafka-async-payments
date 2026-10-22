package dev.natan.payments.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.natan.payments.testsupport.PaymentQueues;
import dev.natan.payments.testsupport.PaymentsLocalStack;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

/** Payments go through the API (mocked here); duplicates and poison go straight onto LocalStack's SQS. */
class StackPaymentInjectorTest {

    private final RestClient.Builder http = RestClient.builder().baseUrl("http://api:8080");
    private final MockRestServiceServer api = MockRestServiceServer.bindTo(http).build();
    private final SqsClient sqs = PaymentsLocalStack.client(SqsClient.builder());
    private final PaymentQueues queues = new PaymentQueues(sqs);
    private final StackPaymentInjector injector =
            new StackPaymentInjector(http.build(), sqs, JsonMapper.builder().build(), Clock.systemUTC());

    @Test
    void sendsPaymentsThroughTheApiAndReturnsTheAcceptedId() {
        UUID paymentId = UUID.randomUUID();
        api.expect(requestTo("http://api:8080/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.accountId").isString())
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"paymentId\": \"" + paymentId + "\", \"status\": \"ACCEPTED\"}"));

        assertThat(injector.sendPayment()).isEqualTo(paymentId);
    }

    @Test
    void duplicatePutsTwoCopiesOfOnePaymentOnTheQueue() {
        int before = queues.messagesOn(PaymentQueues.PAYMENTS);

        injector.sendDuplicate();

        assertThat(queues.messagesOn(PaymentQueues.PAYMENTS) - before).isEqualTo(2);
    }

    @Test
    void poisonPutsOneUnparseableMessageOnTheQueue() {
        int before = queues.messagesOn(PaymentQueues.PAYMENTS);

        injector.sendPoison();

        assertThat(queues.messagesOn(PaymentQueues.PAYMENTS) - before).isEqualTo(1);
    }
}
