package dev.natan.payments.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class MessageBodiesTest {

    private final MessageBodies bodies = new MessageBodies(JsonMapper.builder().build());

    @Test
    void readsThePaymentIdOfAPayment() {
        UUID paymentId = UUID.randomUUID();

        assertThat(bodies.paymentIdIn("{\"paymentId\": \"" + paymentId + "\"}")).isEqualTo(paymentId);
    }

    @Test
    void anythingElseHasNoPaymentId() {
        assertThat(bodies.paymentIdIn("this is not a payment")).isNull();
        assertThat(bodies.paymentIdIn("{\"paymentId\": \"not-a-uuid\"}")).isNull();
        assertThat(bodies.paymentIdIn("{\"paymentId\": 42}")).isNull();
    }
}
