package dev.natan.payments.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.localstack.LocalStackContainer;

import dev.natan.payments.contract.PaymentRequested;
import dev.natan.payments.testsupport.PaymentsLocalStack;
import io.awspring.cloud.sqs.operations.SqsTemplate;

/** API → SQS against LocalStack: an accepted payment is already on the queue when the 202 comes back. */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentQueueingTest {

    @ServiceConnection
    static LocalStackContainer localStack = PaymentsLocalStack.shared();

    @Autowired
    MockMvcTester mvc;

    @Autowired
    SqsTemplate sqsTemplate;

    @Test
    void acceptedPaymentIsOnTheQueue() {
        var response = mvc.post().uri("/payments").contentType(MediaType.APPLICATION_JSON).content("""
                {"accountId": "acc-42", "pixKey": "+5511999990000", "amount": 99.90}
                """).exchange();

        assertThat(response).hasStatus(HttpStatus.ACCEPTED);
        var queued = sqsTemplate.receive(
                from -> from.queue("payments").pollTimeout(Duration.ofSeconds(5)), PaymentRequested.class);
        assertThat(queued).isPresent();
        PaymentRequested payment = queued.get().getPayload();
        assertThat(response).bodyJson().extractingPath("$.paymentId").isEqualTo(payment.paymentId().toString());
        assertThat(payment.accountId()).isEqualTo("acc-42");
    }
}
