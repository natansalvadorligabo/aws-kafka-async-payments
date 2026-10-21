package dev.natan.payments.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import dev.natan.payments.contract.PaymentRequested;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

class StackPaymentInjector implements PaymentInjector {

    private static final int ACCOUNTS = 5;

    private final RestClient api;
    private final SqsClient sqs;
    private final JsonMapper json;
    private final Clock clock;

    StackPaymentInjector(RestClient api, SqsClient sqs, JsonMapper json, Clock clock) {
        this.api = api;
        this.sqs = sqs;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public UUID sendPayment() {
        AcceptedPayment accepted = api.post().uri("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("accountId", randomAccount(), "pixKey", "dashboard@example.com", "amount", randomAmount()))
                .retrieve()
                .body(AcceptedPayment.class);
        if (accepted == null) {
            throw new IllegalStateException("expected the API to answer 202 with a paymentId, got an empty body");
        }
        return accepted.paymentId();
    }

    @Override
    public UUID sendDuplicate() {
        PaymentRequested payment = new PaymentRequested(
                UUID.randomUUID(), randomAccount(), "dashboard@example.com", randomAmount(), clock.instant());
        String body = json.writeValueAsString(payment);
        send(body);
        send(body);
        return payment.paymentId();
    }

    @Override
    public void sendPoison() {
        send("this is not a payment");
    }

    private void send(String body) {
        String queueUrl = sqs.getQueueUrl(get -> get.queueName(AwsStackReader.PAYMENTS_QUEUE)).queueUrl();
        sqs.sendMessage(send -> send.queueUrl(queueUrl).messageBody(body));
    }

    private static String randomAccount() {
        return "acc-" + ThreadLocalRandom.current().nextInt(1, ACCOUNTS + 1);
    }

    private static BigDecimal randomAmount() {
        return BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(1, 500)).setScale(2, RoundingMode.HALF_UP);
    }

    record AcceptedPayment(UUID paymentId) {
    }
}
