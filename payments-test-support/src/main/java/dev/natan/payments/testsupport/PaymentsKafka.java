package dev.natan.payments.testsupport;

import java.io.IOException;
import java.nio.file.Path;

import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One Kafka (KRaft) per test JVM, with topics created by the same script docker-compose uses.
 *
 * <pre>{@code
 * @ServiceConnection
 * static KafkaContainer kafka = PaymentsKafka.shared();
 * }</pre>
 */
public final class PaymentsKafka {

    public static final String PAYMENTS_TOPIC = "payments";

    // Same version as the kafka-clients Spring Boot manages.
    private static final DockerImageName IMAGE = DockerImageName.parse("apache/kafka:4.1.2");
    private static final Path CREATE_TOPICS = Path.of("..", "infra", "kafka", "create-topics.sh");
    // The container's broker-to-broker listener, which advertises an address reachable from inside it.
    private static final String INTERNAL_BOOTSTRAP = "localhost:9093";

    private static final KafkaContainer SHARED = new KafkaContainer(IMAGE)
            .withCopyFileToContainer(MountableFile.forHostPath(CREATE_TOPICS, 0755), "/opt/payments/create-topics.sh");

    private PaymentsKafka() {
    }

    public static synchronized KafkaContainer shared() {
        if (!SHARED.isRunning()) {
            SHARED.start();
            createTopics();
        }
        return SHARED;
    }

    private static void createTopics() {
        try {
            ExecResult result = SHARED.execInContainer("/opt/payments/create-topics.sh", INTERNAL_BOOTSTRAP);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("expected create-topics.sh to exit 0, got "
                        + result.getExitCode() + ": " + result.getStderr());
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not run create-topics.sh in the Kafka container", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while creating Kafka topics", e);
        }
    }
}
