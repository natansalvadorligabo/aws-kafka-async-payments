package dev.natan.payments.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;

import dev.natan.payments.testsupport.PaymentsKafka;
import tools.jackson.databind.json.JsonMapper;

class KafkaPaymentsTopicTest {

    private static final KafkaContainer kafka = PaymentsKafka.shared();

    private final KafkaPaymentsTopic topic = new KafkaPaymentsTopic(
            Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers())),
            new KafkaConsumer<>(Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
                    new StringDeserializer(), new StringDeserializer()),
            PaymentsKafka.PAYMENTS_TOPIC,
            new MessageBodies(JsonMapper.builder().build()));

    @Test
    void reportsEveryPartitionOfTheTopic() {
        assertThat(topic.partitions()).extracting(PartitionState::partition).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    void readsRecordsWithTheirPartitionOffsetKeyAndPaymentId() {
        String account = "acc-" + UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        publish(account, first);
        publish(account, second);

        List<TopicRecord> seen = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            topic.pollNewRecords().stream().filter(record -> account.equals(record.key())).forEach(seen::add);
            return seen.size() == 2;
        });

        assertThat(seen).extracting(TopicRecord::paymentId).containsExactly(first, second);
        assertThat(seen.get(1).partition()).isEqualTo(seen.get(0).partition());
        assertThat(seen.get(1).offset()).isEqualTo(seen.get(0).offset() + 1);
        long end = topic.partitions().get(seen.get(1).partition()).endOffset();
        assertThat(end).isGreaterThan(seen.get(1).offset());
    }

    private static void publish(String key, UUID paymentId) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(PaymentsKafka.PAYMENTS_TOPIC, key,
                    "{\"paymentId\": \"" + paymentId + "\"}"));
        }
    }
}
