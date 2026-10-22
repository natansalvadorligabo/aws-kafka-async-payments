package dev.natan.payments.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.kafka.KafkaContainer;

import dev.natan.payments.contract.PaymentRequested;
import dev.natan.payments.testsupport.PaymentsKafka;
import tools.jackson.databind.json.JsonMapper;

/** API → Kafka: the key is the accountId, so one account's payments share a partition and keep their order. */
@SpringBootTest(properties = "payments.broker=kafka")
@AutoConfigureMockMvc
class KafkaPaymentQueueingTest {

    @ServiceConnection
    static KafkaContainer kafka = PaymentsKafka.shared();

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JsonMapper json;

    @Test
    void paymentsOfOneAccountLandOnOnePartitionInTheOrderTheyWereAccepted() {
        String account = "acc-" + UUID.randomUUID();
        List<UUID> accepted = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            accepted.add(accept(account));
            accept("acc-other-" + i);
        }

        List<ConsumerRecord<String, String>> records = recordsKeyed(account, accepted.size());

        assertThat(records).extracting(ConsumerRecord::partition).containsOnly(records.getFirst().partition());
        assertThat(records).extracting(record -> json.readValue(record.value(), PaymentRequested.class).paymentId())
                .containsExactlyElementsOf(accepted);
    }

    private UUID accept(String accountId) {
        MvcTestResult response = mvc.post().uri("/payments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\": \"" + accountId + "\", \"pixKey\": \"k@example.com\", \"amount\": 1.00}")
                .exchange();
        assertThat(response).hasStatus(HttpStatus.ACCEPTED);
        return UUID.fromString(json.readTree(response.getResponse().getContentAsByteArray()).get("paymentId").asString());
    }

    /** Reads the topic from the beginning with a throwaway group; records of one partition arrive in offset order. */
    private static List<ConsumerRecord<String, String>> recordsKeyed(String key, int expected) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(PaymentsKafka.PAYMENTS_TOPIC));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.equals(record.key())) {
                        matching.add(record);
                    }
                });
                return matching.size() >= expected;
            });
        }
        return matching;
    }
}
