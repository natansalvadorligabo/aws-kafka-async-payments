package dev.natan.payments.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.natan.payments.contract.PaymentRequested;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    @TestConfiguration
    static class Doubles {

        @Bean
        InMemoryPaymentQueue paymentQueue() {
            return new InMemoryPaymentQueue();
        }

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    InMemoryPaymentQueue paymentQueue;

    @Test
    void validPaymentIsQueuedAndAnswered202() {
        var response = post("""
                {"accountId": "acc-1", "pixKey": "alice@example.com", "amount": 10.50}
                """);

        assertThat(response).hasStatus(HttpStatus.ACCEPTED);
        PaymentRequested queued = paymentQueue.enqueued().getLast();
        assertThat(response).bodyJson().extractingPath("$.paymentId").isEqualTo(queued.paymentId().toString());
        assertThat(queued.accountId()).isEqualTo("acc-1");
        assertThat(queued.amount()).isEqualByComparingTo(new BigDecimal("10.50"));
        assertThat(queued.requestedAt()).isEqualTo(NOW);
    }

    @Test
    void invalidPaymentIsRejectedAndNeverQueued() {
        int queuedBefore = paymentQueue.enqueued().size();

        var response = post("""
                {"accountId": "", "pixKey": "alice@example.com", "amount": -1}
                """);

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(paymentQueue.enqueued()).hasSize(queuedBefore);
    }

    private MvcTestResult post(String body) {
        return mvc.post().uri("/payments").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }
}
