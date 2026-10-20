package dev.natan.payments.testsupport;

import java.nio.file.Path;

import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

/**
 * One LocalStack per test JVM, provisioned by the same init script docker-compose uses.
 *
 * <pre>{@code
 * @ServiceConnection
 * static LocalStackContainer localStack = PaymentsLocalStack.shared();
 *
 * SqsClient sqs = PaymentsLocalStack.client(SqsClient.builder());
 * }</pre>
 */
public final class PaymentsLocalStack {

    // 4.14.0 is the last image that starts without a LOCALSTACK_AUTH_TOKEN (2026.x requires a license).
    private static final DockerImageName IMAGE = DockerImageName.parse("localstack/localstack:4.14.0");
    private static final Path INFRA = Path.of("..", "infra", "localstack");

    private static final LocalStackContainer SHARED = new LocalStackContainer(IMAGE)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(INFRA.resolve("init-aws.sh"), 0755),
                    "/etc/localstack/init/ready.d/init-aws.sh")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(INFRA.resolve("deploy-lambda.sh"), 0755),
                    "/opt/payments/deploy-lambda.sh")
            .waitingFor(Wait.forLogMessage(".*payments infra ready.*", 1));

    private PaymentsLocalStack() {
    }

    public static synchronized LocalStackContainer shared() {
        if (!SHARED.isRunning()) {
            SHARED.start();
        }
        return SHARED;
    }

    /** Builds any AWS SDK client pointed at the shared LocalStack. */
    public static <B extends AwsClientBuilder<B, C>, C> C client(B builder) {
        LocalStackContainer localStack = shared();
        return builder
                .endpointOverride(localStack.getEndpoint())
                .region(Region.of(localStack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
                .build();
    }
}
